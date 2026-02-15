package org.opentripplanner.updater.trip.gtfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.transit.realtime.GtfsRealtime;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.basic.Accessibility;
import org.opentripplanner.updater.trip.model.ParsedAddNewTrip;
import org.opentripplanner.updater.trip.model.ParsedCancelTrip;
import org.opentripplanner.updater.trip.model.ParsedDeleteTrip;
import org.opentripplanner.updater.trip.model.ParsedModifyTrip;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedTripRemoval;
import org.opentripplanner.updater.trip.model.ParsedUpdateExisting;
import org.opentripplanner.updater.trip.model.TimeUpdate;

class GtfsRtTripUpdateParserTest {

  private static final String FEED_ID = "TEST";
  private static final LocalDate TEST_DATE = LocalDate.of(2024, 1, 15);
  private static final ZoneId TIME_ZONE = ZoneId.of("America/New_York");
  private static final long MIDNIGHT_EPOCH = TEST_DATE.atStartOfDay(TIME_ZONE).toEpochSecond();
  private static final long TIME_0830 = MIDNIGHT_EPOCH + 30600;
  private static final long TIME_0831 = MIDNIGHT_EPOCH + 30660;
  private static final long TIME_0820 = MIDNIGHT_EPOCH + 30000;
  private static final long TIME_0821 = MIDNIGHT_EPOCH + 30060;
  private GtfsRtTripUpdateParser parser;

  @BeforeEach
  void setUp() {
    parser = new GtfsRtTripUpdateParser(
      ForwardsDelayPropagationType.DEFAULT,
      BackwardsDelayPropagationType.ALWAYS,
      FEED_ID,
      TIME_ZONE,
      () -> TEST_DATE
    );
  }

  @Test
  void parseScheduledTripUpdate() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setRouteId("route1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
      )
      .addStopTimeUpdate(
        GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
          .setStopId("stop1")
          .setStopSequence(0)
          .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(60))
          .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(120))
      )
      .build();

    var result = parser.parse(tripUpdate);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedUpdateExisting.class, result.successValue());

    assertEquals(new FeedScopedId(FEED_ID, "trip1"), parsed.tripReference().tripId());
    assertEquals(TEST_DATE, parsed.serviceDate());
    assertEquals(1, parsed.stopTimeUpdates().size());

    var stopUpdate = parsed.stopTimeUpdates().get(0);
    assertEquals(new FeedScopedId(FEED_ID, "stop1"), stopUpdate.stopReference().stopId());
    assertEquals(0, stopUpdate.stopSequence());
    assertNotNull(stopUpdate.arrivalUpdate());
    assertEquals(60, asTimeUpdate(stopUpdate.arrivalUpdate()).delaySeconds());
    assertNotNull(stopUpdate.departureUpdate());
    assertEquals(120, asTimeUpdate(stopUpdate.departureUpdate()).delaySeconds());
  }

  @Test
  void parseCancelledTrip() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED)
      )
      .build();

    var result = parser.parse(tripUpdate);

    assertTrue(result.isSuccess());
    assertInstanceOf(ParsedCancelTrip.class, result.successValue());
  }

  @Test
  void parseDeletedTrip() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.DELETED)
      )
      .build();

    var result = parser.parse(tripUpdate);

    assertTrue(result.isSuccess());
    assertInstanceOf(ParsedDeleteTrip.class, result.successValue());
    assertInstanceOf(ParsedTripRemoval.class, result.successValue());
  }

  @Test
  void parseNewTrip() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setRouteId("route1")
          .setStartTime("08:30:00")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED)
      )
      .setVehicle(
        GtfsRealtime.VehicleDescriptor.newBuilder().setWheelchairAccessible(
          GtfsRealtime.VehicleDescriptor.WheelchairAccessible.WHEELCHAIR_ACCESSIBLE
        )
      )
      .addStopTimeUpdate(
        GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
          .setStopId("stop1")
          .setStopSequence(0)
          .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(TIME_0830))
          .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(TIME_0831))
      )
      .build();

    var result = parser.parse(tripUpdate);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedAddNewTrip.class, result.successValue());

    assertNotNull(parsed.tripCreationInfo());
    assertEquals(new FeedScopedId(FEED_ID, "trip1"), parsed.tripCreationInfo().tripId());
    assertEquals(new FeedScopedId(FEED_ID, "route1"), parsed.tripCreationInfo().routeId());
    assertEquals(Accessibility.POSSIBLE, parsed.tripCreationInfo().wheelchairAccessibility());

    assertEquals(1, parsed.stopTimeUpdates().size());
    var stopUpdate = parsed.stopTimeUpdates().get(0);
    assertNotNull(stopUpdate.arrivalUpdate());
    assertEquals(
      30600,
      asTimeUpdate(stopUpdate.arrivalUpdate()).absoluteTimeSecondsSinceMidnight()
    );
    assertNotNull(stopUpdate.departureUpdate());
    assertEquals(
      30660,
      asTimeUpdate(stopUpdate.departureUpdate()).absoluteTimeSecondsSinceMidnight()
    );
  }

  @Test
  void parseReplacementTrip() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.REPLACEMENT)
      )
      .addStopTimeUpdate(
        GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
          .setStopId("stop1")
          .setStopSequence(0)
          .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(60))
      )
      .build();

    var result = parser.parse(tripUpdate);

    assertTrue(result.isSuccess());
    assertInstanceOf(ParsedModifyTrip.class, result.successValue());
  }

  @Test
  void parseSkippedStop() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
      )
      .addStopTimeUpdate(
        GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
          .setStopId("stop1")
          .setStopSequence(0)
          .setScheduleRelationship(
            GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED
          )
      )
      .build();

    var result = parser.parse(tripUpdate);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedUpdateExisting.class, result.successValue());

    assertEquals(1, parsed.stopTimeUpdates().size());
    var stopUpdate = parsed.stopTimeUpdates().get(0);
    assertEquals(ParsedStopTimeUpdate.StopUpdateStatus.SKIPPED, stopUpdate.status());
    assertTrue(stopUpdate.isSkipped());
  }

  @Test
  void parseWithAssignedStop() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
      )
      .addStopTimeUpdate(
        GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
          .setStopId("stop1")
          .setStopSequence(0)
          .setStopTimeProperties(
            GtfsRealtime.TripUpdate.StopTimeUpdate.StopTimeProperties.newBuilder().setAssignedStopId(
              "stop1_platform_2"
            )
          )
          .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(60))
      )
      .build();

    var result = parser.parse(tripUpdate);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedUpdateExisting.class, result.successValue());

    var stopUpdate = parsed.stopTimeUpdates().get(0);
    assertEquals(new FeedScopedId(FEED_ID, "stop1"), stopUpdate.stopReference().stopId());
    assertEquals(
      new FeedScopedId(FEED_ID, "stop1_platform_2"),
      stopUpdate.stopReference().assignedStopId()
    );
  }

  @Test
  void parseWithTripProperties() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED)
      )
      .setTripProperties(
        GtfsRealtime.TripUpdate.TripProperties.newBuilder()
          .setTripHeadsign("Downtown")
          .setTripShortName("X1")
      )
      .addStopTimeUpdate(
        GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
          .setStopId("stop1")
          .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(TIME_0830))
      )
      .build();

    var result = parser.parse(tripUpdate);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedAddNewTrip.class, result.successValue());

    assertNotNull(parsed.tripCreationInfo());
    assertEquals("Downtown", parsed.tripCreationInfo().headsign().toString());
    assertEquals("X1", parsed.tripCreationInfo().shortName());
  }

  @Test
  void parseWithStopHeadsign() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
      )
      .addStopTimeUpdate(
        GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
          .setStopId("stop1")
          .setStopSequence(0)
          .setStopTimeProperties(
            GtfsRealtime.TripUpdate.StopTimeUpdate.StopTimeProperties.newBuilder().setStopHeadsign(
              "Downtown Express"
            )
          )
          .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(60))
      )
      .build();

    var result = parser.parse(tripUpdate);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedUpdateExisting.class, result.successValue());

    var stopUpdate = parsed.stopTimeUpdates().get(0);
    assertNotNull(stopUpdate.stopHeadsign());
    assertEquals("Downtown Express", stopUpdate.stopHeadsign().toString());
  }

  // Direction parsing not implemented yet - requires DirectionMapper integration
  // @Test
  // void parseWithDirection() { ... }

  @Test
  void parseMissingTripId() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder().setScheduleRelationship(
          GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED
        )
      )
      .build();

    var result = parser.parse(tripUpdate);

    assertTrue(result.isFailure());
  }

  @Test
  void parseUnscheduledTrip() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.UNSCHEDULED)
      )
      .build();

    var result = parser.parse(tripUpdate);

    assertTrue(result.isFailure());
  }

  @Test
  void parseDuplicatedTrip() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.DUPLICATED)
      )
      .build();

    var result = parser.parse(tripUpdate);

    assertTrue(result.isFailure());
  }

  @Test
  void parseStopTimeUpdateWithPickupDropoff() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
      )
      .addStopTimeUpdate(
        GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
          .setStopId("stop1")
          .setStopSequence(0)
          .setStopTimeProperties(
            GtfsRealtime.TripUpdate.StopTimeUpdate.StopTimeProperties.newBuilder()
              .setPickupType(
                GtfsRealtime.TripUpdate.StopTimeUpdate.StopTimeProperties.DropOffPickupType.PHONE_AGENCY
              )
              .setDropOffType(
                GtfsRealtime.TripUpdate.StopTimeUpdate.StopTimeProperties.DropOffPickupType.NONE
              )
          )
          .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(60))
      )
      .build();

    var result = parser.parse(tripUpdate);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedUpdateExisting.class, result.successValue());

    var stopUpdate = parsed.stopTimeUpdates().get(0);
    assertNotNull(stopUpdate.pickup());
    assertNotNull(stopUpdate.dropoff());
  }

  @Test
  void parseNewTripWithScheduledTimes() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED)
      )
      .addStopTimeUpdate(
        GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
          .setStopId("stop1")
          .setStopSequence(0)
          .setArrival(
            GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
              .setTime(TIME_0830)
              .setScheduledTime(TIME_0820)
          )
          .setDeparture(
            GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
              .setTime(TIME_0831)
              .setScheduledTime(TIME_0821)
          )
      )
      .build();

    var result = parser.parse(tripUpdate);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedAddNewTrip.class, result.successValue());

    var stopUpdate = parsed.stopTimeUpdates().get(0);
    assertNotNull(stopUpdate.arrivalUpdate());
    assertEquals(
      30600,
      asTimeUpdate(stopUpdate.arrivalUpdate()).absoluteTimeSecondsSinceMidnight()
    );
    assertEquals(
      30000,
      asTimeUpdate(stopUpdate.arrivalUpdate()).scheduledTimeSecondsSinceMidnight()
    );

    assertNotNull(stopUpdate.departureUpdate());
    assertEquals(
      30660,
      asTimeUpdate(stopUpdate.departureUpdate()).absoluteTimeSecondsSinceMidnight()
    );
    assertEquals(
      30060,
      asTimeUpdate(stopUpdate.departureUpdate()).scheduledTimeSecondsSinceMidnight()
    );
  }

  @Test
  void parseEmptyStopTimeUpdates() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
      )
      .build();

    var result = parser.parse(tripUpdate);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedUpdateExisting.class, result.successValue());

    assertTrue(parsed.stopTimeUpdates().isEmpty());
  }

  @Test
  void parseOptionsPreserved() {
    var result = parser.parse(
      GtfsRealtime.TripUpdate.newBuilder()
        .setTrip(
          GtfsRealtime.TripDescriptor.newBuilder()
            .setTripId("trip1")
            .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
        )
        .build()
    );

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedUpdateExisting.class, result.successValue());

    assertEquals(ForwardsDelayPropagationType.DEFAULT, parsed.options().forwardsPropagation());
    assertEquals(BackwardsDelayPropagationType.ALWAYS, parsed.options().backwardsPropagation());
  }

  private static TimeUpdate asTimeUpdate(ParsedTimeUpdate parsedTimeUpdate) {
    return (TimeUpdate) parsedTimeUpdate;
  }
}
