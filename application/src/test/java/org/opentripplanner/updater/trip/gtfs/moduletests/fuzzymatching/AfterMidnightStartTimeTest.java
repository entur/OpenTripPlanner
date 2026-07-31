package org.opentripplanner.updater.trip.gtfs.moduletests.fuzzymatching;

import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.DUPLICATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.timetable.Direction;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.UnifiedUpdaterOnly;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;

/**
 * GTFS times a trip that starts after midnight past 24:00:00 on the previous service date, and
 * GTFS-RT identifies such a trip instance the same way - the spec's own {@code start_time} example
 * is 25:15:00. The field must therefore be treated as a time relative to the service date's
 * midnight, not as a time of day: for a feed without stable trip ids, the after-midnight trips are
 * exactly the ones whose identifying tuple carries a start time no {@code java.time.LocalTime} can
 * express.
 */
class AfterMidnightStartTimeTest implements RealtimeTestConstants {

  private static final String ROUTE_ID = "route-1";
  private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 6, 22);
  private static final int OUTBOUND_DIRECTION_ID = 0;
  private static final int LAST_STOP_SEQUENCE = 1;
  private static final int DELAY = 60;

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop stopA = envBuilder.stop(STOP_A_ID);
  private final RegularStop stopB = envBuilder.stop(STOP_B_ID);
  private final Route route = envBuilder.route(ROUTE_ID);

  private final TransitTestEnvironment env = envBuilder
    .addTrip(
      TripInput.of(TRIP_1_ID)
        .withRoute(route)
        .withServiceDates(SERVICE_DATE, SERVICE_DATE.plusDays(2))
        .addStop(stopA, "25:15")
        .addStop(stopB, "25:25"),
      b -> b.withDirection(Direction.OUTBOUND)
    )
    .build();

  /**
   * The load-bearing case for fuzzy trip matching: an id-less update names the night trip by the
   * tuple, whose start time is the trip's scheduled first departure - 25:15:00, the form the static
   * schedule uses.
   */
  @Test
  void appliesADelayToTheNightTripTheTupleNames() {
    var rt = GtfsRtTestHelper.ofFuzzyMatching(env);

    var result = rt.applyTripUpdate(
      rt
        .tripUpdateScheduled(TRIP_1_ID, SERVICE_DATE)
        .withoutTripId()
        .withRouteId(ROUTE_ID)
        .withStartTime("25:15:00")
        .withDirectionId(OUTBOUND_DIRECTION_ID)
        .addDelayedStopTime(LAST_STOP_SEQUENCE, DELAY)
        .build()
    );

    assertSuccess(result);
    assertEquals(
      "U | A [ND] 1:15+1d 1:15+1d | B 1:26+1d 1:26+1d",
      env.tripData(TRIP_1_ID, SERVICE_DATE).showTimetable()
    );
  }

  /**
   * The start time is not read at all when the message names its trip by id, so an update that
   * fills in the optional field correctly must not be punished for it.
   */
  @Test
  void appliesADelayToATripNamedByIdThatAlsoReportsItsStartTime() {
    var rt = GtfsRtTestHelper.of(env);

    var result = rt.applyTripUpdate(
      rt
        .tripUpdateScheduled(TRIP_1_ID, SERVICE_DATE)
        .withStartTime("25:15:00")
        .addDelayedStopTime(LAST_STOP_SEQUENCE, DELAY)
        .build()
    );

    assertSuccess(result);
    assertEquals(
      "U | A [ND] 1:15+1d 1:15+1d | B 1:26+1d 1:26+1d",
      env.tripData(TRIP_1_ID, SERVICE_DATE).showTimetable()
    );
  }

  /**
   * In a DUPLICATED message the start time is the departure time of the duplicate being created,
   * and a duplicate of a night trip departs after midnight like its original.
   */
  @Test
  @UnifiedUpdaterOnly(
    "The legacy implementation parses the start_time of a DUPLICATED message as a " +
      "java.time.LocalTime, which cannot express a time past 24:00:00, so the spec-valid message " +
      "is rejected as INVALID_INPUT_STRUCTURE."
  )
  void duplicatesANightTripAnHourLater() {
    var rt = GtfsRtTestHelper.of(env);

    var result = rt.applyTripUpdate(
      rt.tripUpdate(TRIP_1_ID, SERVICE_DATE, DUPLICATED).withStartTime("26:15:00").build()
    );

    assertSuccess(result);
    var duplicatedTripId = TRIP_1_ID + ":duplicated:" + SERVICE_DATE.plusDays(1) + "T02:15";
    assertEquals(
      "A U | A 2:15+1d 2:15+1d | B 2:25+1d 2:25+1d",
      env.tripData(duplicatedTripId, SERVICE_DATE).showTimetable()
    );
  }
}
