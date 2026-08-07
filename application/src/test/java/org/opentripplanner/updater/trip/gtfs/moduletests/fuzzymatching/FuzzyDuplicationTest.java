package org.opentripplanner.updater.trip.gtfs.moduletests.fuzzymatching;

import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.DUPLICATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.updater.spi.UpdateErrorType.TRIP_NOT_FOUND;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.timetable.Direction;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;
import org.opentripplanner.updater.trip.gtfs.TripUpdateBuilder;

/**
 * DUPLICATED participates in fuzzy trip matching like every other schedule relationship. The
 * descriptor's start time does double duty in a DUPLICATED message - it is the departure time of
 * the duplicate being created - so a fuzzy-matched duplication only finds its original where a
 * scheduled trip departs at that same time. The duplicated trip's id is minted from the
 * <em>matched</em> trip's id, since the message named no other.
 */
class FuzzyDuplicationTest implements RealtimeTestConstants {

  private static final String ROUTE_ID = "route-1";
  private static final String UNKNOWN_TRIP_ID = "not-in-the-schedule";
  private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 6, 22);
  private static final LocalTime START_TIME = LocalTime.of(12, 0);
  private static final int OUTBOUND_DIRECTION_ID = 0;
  private static final String DUPLICATED_ID =
    TRIP_1_ID + ":duplicated:" + SERVICE_DATE + "T" + START_TIME;

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop stopA = envBuilder.stop(STOP_A_ID);
  private final RegularStop stopB = envBuilder.stop(STOP_B_ID);
  private final Route route = envBuilder.route(ROUTE_ID);

  private final TransitTestEnvironment env = envBuilder
    .addTrip(
      TripInput.of(TRIP_1_ID)
        .withRoute(route)
        .withServiceDates(SERVICE_DATE, SERVICE_DATE.plusDays(2))
        .addStop(stopA, "12:00")
        .addStop(stopB, "12:10"),
      b -> b.withDirection(Direction.OUTBOUND)
    )
    .build();

  private final GtfsRtTestHelper gtfsRt = GtfsRtTestHelper.ofFuzzyMatching(env);

  @Test
  void duplicatesTheTripMatchedForAnUnknownTripId() {
    var result = gtfsRt.applyTripUpdate(fuzzyUpdate().build());

    assertSuccess(result);
    assertEquals(
      "A U | A 12:00 12:00 | B 12:10 12:10",
      env.tripData(DUPLICATED_ID, SERVICE_DATE).showTimetable()
    );
  }

  @Test
  void duplicatesTheTripTheTupleNames() {
    var result = gtfsRt.applyTripUpdate(fuzzyUpdate().withoutTripId().build());

    assertSuccess(result);
    assertEquals(
      "A U | A 12:00 12:00 | B 12:10 12:10",
      env.tripData(DUPLICATED_ID, SERVICE_DATE).showTimetable()
    );
  }

  /**
   * No scheduled trip departs at the duplicate's start time, so the unknown trip id stays the only
   * identifier and the duplication reports it as not found.
   */
  @Test
  void rejectsADuplicationWhenNothingMatches() {
    var result = gtfsRt.applyTripUpdate(fuzzyUpdate().withStartTime(LocalTime.of(13, 30)).build());

    assertFailure(TRIP_NOT_FOUND, result);
  }

  /**
   * A duplication naming its original by the tuple: route, direction, the start date and - doing
   * double duty - the duplicate's start time.
   */
  private TripUpdateBuilder fuzzyUpdate() {
    return gtfsRt
      .tripUpdate(UNKNOWN_TRIP_ID, SERVICE_DATE, DUPLICATED)
      .withRouteId(ROUTE_ID)
      .withStartTime(START_TIME)
      .withDirectionId(OUTBOUND_DIRECTION_ID);
  }
}
