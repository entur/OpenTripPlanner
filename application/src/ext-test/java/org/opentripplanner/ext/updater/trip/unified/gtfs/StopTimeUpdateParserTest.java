package org.opentripplanner.ext.updater.trip.unified.gtfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_ARRIVAL_TIME;
import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_STOP_REFERENCE;

import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.model.ServiceTime;
import org.opentripplanner.ext.updater.trip.unified.model.StopSequence;
import org.opentripplanner.ext.updater.trip.unified.model.command.ParsedStopTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.StopReference;
import org.opentripplanner.ext.updater.trip.unified.model.command.StopResolutionStrategy;
import org.opentripplanner.ext.updater.trip.unified.model.command.TimeUpdate;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.gtfs.model.StopTimeUpdate;
import org.opentripplanner.utils.time.ServiceDateUtils;
import org.opentripplanner.utils.time.TimeUtils;

class StopTimeUpdateParserTest {

  private static final ZoneId ZONE = ZoneId.of("Europe/Oslo");
  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 5, 7);
  private static final String FEED_ID = "F";

  @Test
  void rejectsACallReferencingItsStopNeitherByIdNorBySequence() {
    var call = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder();

    var e = assertThrows(UpdateException.class, () -> parser(false).parse(wrap(call)));

    assertEquals(INVALID_STOP_REFERENCE, e.errorType());
  }

  @Test
  void parsesAPredictedTimeWithItsDerivedAimedTime() {
    var call = call("A").setArrival(
      StopTimeEvent.newBuilder().setTime(epochOf("8:30")).setDelay(120)
    );

    var parsed = parser(false).parse(wrap(call)).getFirst();

    assertEquals(
      TimeUpdate.ofAbsolute(ServiceTime.parse("8:30"), ServiceTime.parse("8:28")),
      parsed.arrivalUpdate()
    );
    assertEquals(
      StopReference.ofStopId(new FeedScopedId(FEED_ID, "A"), null),
      parsed.stopReference()
    );
  }

  @Test
  void parsesADelayOnlyPrediction() {
    var call = call("A").setDeparture(StopTimeEvent.newBuilder().setDelay(60));

    var parsed = parser(false).parse(wrap(call)).getFirst();

    assertEquals(TimeUpdate.ofDelay(60), parsed.departureUpdate());
    assertFalse(parsed.hasArrivalUpdate());
  }

  /**
   * A trip that brings its own schedule runs to the schedule it reports: a call stating only a
   * scheduled time still produces a time update, offset by the delay if it states one.
   */
  @Test
  void runsToTheReportedScheduleWhenACallStatesOnlyAScheduledTime() {
    var call = call("A").setArrival(
      StopTimeEvent.newBuilder().setScheduledTime(epochOf("8:30")).setDelay(60)
    );

    var parsed = parser(true).parse(wrap(call)).getFirst();

    assertEquals(
      TimeUpdate.ofAbsolute(ServiceTime.parse("8:31"), ServiceTime.parse("8:30")),
      parsed.arrivalUpdate()
    );
  }

  /**
   * An arrival or departure of a trip running to an existing schedule must state a time or a
   * delay - an event stating neither is a producer error that rejects the entity.
   */
  @Test
  void rejectsAnEmptyEventOnATripRunningToAnExistingSchedule() {
    var call = call("A").setArrival(StopTimeEvent.newBuilder());

    var e = assertThrows(UpdateException.class, () -> parser(false).parse(wrap(call)));

    assertEquals(INVALID_ARRIVAL_TIME, e.errorType());
  }

  /** The empty-event check applies only to calls the trip is predicted to make. */
  @Test
  void skippedCallMayCarryAnEmptyEvent() {
    var call = call("A")
      .setScheduleRelationship(GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED)
      .setArrival(StopTimeEvent.newBuilder());

    var parsed = parser(false).parse(wrap(call)).getFirst();

    assertEquals(ParsedStopTimeUpdate.StopUpdateStatus.SKIPPED, parsed.status());
  }

  /**
   * A trip that brings its own schedule must place every call within the bounds of its service
   * date; a trip running to an existing schedule is not held to them - its times are reconciled
   * against the scheduled timetable instead.
   */
  @Test
  void rejectsAScheduledTimeOutsideTheServiceDateBoundsOnlyForATripBringingItsOwnSchedule() {
    var outOfBounds = call("A").setArrival(StopTimeEvent.newBuilder().setTime(epochOf("73:00")));

    var e = assertThrows(UpdateException.class, () -> parser(true).parse(wrap(outOfBounds)));
    assertEquals(INVALID_ARRIVAL_TIME, e.errorType());

    var parsed = parser(false).parse(wrap(outOfBounds)).getFirst();
    assertEquals(
      TimeUpdate.ofAbsolute(ServiceTime.parse("73:00"), ServiceTime.parse("73:00")),
      parsed.arrivalUpdate()
    );
  }

  @Test
  void numbersItsCallBySequenceWhenTheMessageDoes() {
    var call = call("A").setStopSequence(5).setDeparture(StopTimeEvent.newBuilder().setDelay(60));

    var parsed = parser(false).parse(wrap(call)).getFirst();

    assertEquals(StopSequence.of(5), parsed.stopSequence());
  }

  @Test
  void referencesItsStopBySequenceAloneWhenTheMessageNamesNoStopId() {
    var call = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
      .setStopSequence(3)
      .setDeparture(StopTimeEvent.newBuilder().setDelay(60));

    var parsed = parser(false).parse(wrap(call)).getFirst();

    assertEquals(StopSequence.of(3), parsed.stopSequence());
    assertEquals(
      new StopReference(null, null, StopResolutionStrategy.DIRECT),
      parsed.stopReference()
    );
  }

  private static StopTimeUpdateParser parser(boolean reportsOwnSchedule) {
    return new StopTimeUpdateParser(FEED_ID, SERVICE_DATE, ZONE, reportsOwnSchedule);
  }

  private static List<StopTimeUpdate> wrap(GtfsRealtime.TripUpdate.StopTimeUpdate.Builder call) {
    return List.of(new StopTimeUpdate(call.build()));
  }

  private static GtfsRealtime.TripUpdate.StopTimeUpdate.Builder call(String stopId) {
    return GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder().setStopId(stopId);
  }

  private static long epochOf(String time) {
    return ServiceDateUtils.asStartOfService(SERVICE_DATE, ZONE)
      .plusSeconds(TimeUtils.time(time))
      .toEpochSecond();
  }
}
