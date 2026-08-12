package org.opentripplanner.updater.trip.siri.moduletests.extrajourney;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.core.model.id.FeedScopedIdForTestFactory.id;
import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_DEPARTURE_TIME;
import static org.opentripplanner.updater.spi.UpdateErrorType.NO_START_DATE;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.organization.Operator;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.LegacyUpdaterOnly;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.UnifiedUpdaterOnly;
import org.opentripplanner.updater.trip.siri.SiriEtBuilder;
import org.opentripplanner.updater.trip.siri.SiriTestHelper;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;

/**
 * An extra journey states its service date in one of three ways: explicitly in
 * {@code FramedVehicleJourneyRef.DataFrameRef}, indirectly through a {@code DatedVehicleJourneyRef}
 * naming an existing dated service journey, or implicitly through the aimed departure time of its
 * first call. A journey that states it in none of them cannot be placed on a day, and is rejected
 * with {@link org.opentripplanner.updater.spi.UpdateErrorType#NO_START_DATE}.
 * <p>
 * The interesting case is the third one going missing while a {@code DatedVehicleJourneyRef} is
 * present: for an extra journey that reference names the journey being created rather than an
 * existing one, so it cannot supply the day either.
 * <p>
 * A journey without aimed times also violates the Nordic-profile time rules, which the unified
 * implementation validates at parse - before the day is resolved. It therefore rejects the same
 * journeys with {@code INVALID_DEPARTURE_TIME}; its {@code NO_START_DATE} remains reachable only
 * for a journey whose calls are profile-complete but undatable, which cannot be expressed with
 * timed calls.
 */
class ExtraJourneyWithoutServiceDateTest implements RealtimeTestConstants {

  private static final String UNIFIED_REJECTS_TIMES_FIRST =
    "The unified implementation rejects the incomplete call times at parse, before the day is " +
    "resolved - see the companion test.";
  private static final String LEGACY_REJECTS_DATE_FIRST =
    "The legacy implementation tolerates the incomplete call times and rejects only for the " +
    "unresolvable day - see the companion test.";

  private static final String ADDED_TRIP_ID = "newJourney";
  private static final String ROUTE_ID = "routeId";
  private static final String OPERATOR_ID = "operatorId";

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop stopA = envBuilder.stop(STOP_A_ID);
  private final RegularStop stopB = envBuilder.stop(STOP_B_ID);
  private final RegularStop stopC = envBuilder.stop(STOP_C_ID);
  private final RegularStop stopD = envBuilder.stop(STOP_D_ID);
  private final Operator operator = envBuilder.operator(OPERATOR_ID);
  private final Route route = envBuilder.route(ROUTE_ID, operator);

  private final TripInput tripInput = TripInput.of(TRIP_1_ID)
    .withWithTripOnServiceDate(TRIP_1_ID)
    .withRoute(route)
    .addStop(stopA, "0:00:10", "0:00:11")
    .addStop(stopB, "0:00:20", "0:00:21");

  @LegacyUpdaterOnly(UNIFIED_REJECTS_TIMES_FIRST)
  @Test
  void extraJourneyWithoutAnyAimedTimeCannotBeDated() {
    var env = envBuilder.addTrip(tripInput).build();
    var siri = SiriTestHelper.of(env);

    var result = siri.applyEstimatedTimetable(
      extraJourneyWithoutServiceDate(siri).buildEstimatedTimetableDeliveries()
    );

    assertFailure(NO_START_DATE, result);
    assertThat(env.transitService().getTrip(id(ADDED_TRIP_ID))).isNull();
  }

  @UnifiedUpdaterOnly(LEGACY_REJECTS_DATE_FIRST)
  @Test
  void extraJourneyWithoutAnyAimedTimeIsRejected() {
    var env = envBuilder.addTrip(tripInput).build();
    var siri = SiriTestHelper.of(env);

    var result = siri.applyEstimatedTimetable(
      extraJourneyWithoutServiceDate(siri).buildEstimatedTimetableDeliveries()
    );

    assertFailure(INVALID_DEPARTURE_TIME, result);
    assertThat(env.transitService().getTrip(id(ADDED_TRIP_ID))).isNull();
  }

  /**
   * A journey that cannot be placed on a day costs that journey, not the message it arrived in.
   * The remaining journeys of the delivery are applied as if it had not been there.
   */
  @LegacyUpdaterOnly(UNIFIED_REJECTS_TIMES_FIRST)
  @Test
  void aJourneyRejectedForItsDateDoesNotDiscardTheRestOfTheDelivery() {
    var env = envBuilder.addTrip(tripInput).build();
    var siri = SiriTestHelper.of(env);

    var result = siri.applyEstimatedTimetable(undatableAndDelayedJourneys(siri));

    assertEquals(1, result.successful(), "the journey after the rejected one should be applied");
    assertFailure(NO_START_DATE, result);
    assertEquals(
      "U | A 0:00:15 0:00:16 | B 0:00:25 0:00:26",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }

  @UnifiedUpdaterOnly(LEGACY_REJECTS_DATE_FIRST)
  @Test
  void aJourneyRejectedForItsTimesDoesNotDiscardTheRestOfTheDelivery() {
    var env = envBuilder.addTrip(tripInput).build();
    var siri = SiriTestHelper.of(env);

    var result = siri.applyEstimatedTimetable(undatableAndDelayedJourneys(siri));

    assertEquals(1, result.successful(), "the journey after the rejected one should be applied");
    assertFailure(INVALID_DEPARTURE_TIME, result);
    assertEquals(
      "U | A 0:00:15 0:00:16 | B 0:00:25 0:00:26",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }

  private List<EstimatedTimetableDeliveryStructure> undatableAndDelayedJourneys(
    SiriTestHelper siri
  ) {
    return SiriEtBuilder.deliveryOf(
      extraJourneyWithoutServiceDate(siri).buildEstimatedVehicleJourney(),
      delayedTrip1(siri).buildEstimatedVehicleJourney()
    );
  }

  /**
   * An extra journey referencing a dated service journey that does not exist, and whose calls carry
   * only expected times - so neither the reference nor the calls say which day it runs on.
   */
  private SiriEtBuilder extraJourneyWithoutServiceDate(SiriTestHelper siri) {
    return siri
      .etBuilder()
      .withEstimatedVehicleJourneyCode(ADDED_TRIP_ID)
      .withIsExtraJourney(true)
      .withDatedVehicleJourneyRef(ADDED_TRIP_ID)
      .withOperatorRef(OPERATOR_ID)
      .withLineRef(ROUTE_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(stopC)
          .withExpectedDepartureTime("00:02")
          .call(stopD)
          .withExpectedArrivalTime("00:04")
      );
  }

  private SiriEtBuilder delayedTrip1(SiriTestHelper siri) {
    return siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(stopA)
          .arriveAimedExpected("00:00:10", "00:00:15")
          .departAimedExpected("00:00:11", "00:00:16")
          .call(stopB)
          .arriveAimedExpected("00:00:20", "00:00:25")
          .departAimedExpected("00:00:21", "00:00:26")
      );
  }
}
