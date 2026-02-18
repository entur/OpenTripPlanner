package org.opentripplanner.updater.trip.siri.moduletests.fuzzymatching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import org.junit.jupiter.api.Test;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.SiriTestHelper;
import uk.org.siri.siri21.ArrivalBoardingActivityEnumeration;
import uk.org.siri.siri21.VehicleModesEnumeration;

class FuzzyTripMatchingTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of();
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stop(STOP_B_ID);

  private final TripInput TRIP_INPUT = TripInput.of(TRIP_1_ID)
    .addStop(STOP_A, "0:00:10", "0:00:11")
    .addStop(STOP_B, "0:00:20", "0:00:21");

  /**
   * Update calls without changing the pattern. Fuzzy matching.
   */
  @Test
  void testUpdateJourneyWithFuzzyMatching() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.ofFuzzyMatching(env);

    var updates = siri
      .etBuilder()
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:00:11", "00:00:15")
          .call(STOP_B)
          .arriveAimedExpected("00:00:20", "00:00:25")
      )
      .buildEstimatedTimetableDeliveries();
    var result = siri.applyEstimatedTimetableWithFuzzyMatcher(updates);
    assertEquals(1, result.successful());
    assertTripUpdated(env);
  }

  /**
   * Update calls without changing the pattern. Fuzzy matching.
   * Edge case: invalid reference to vehicle journey and missing aimed departure time.
   */
  @Test
  void testUpdateJourneyWithFuzzyMatchingAndMissingAimedDepartureTime() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.ofFuzzyMatching(env);

    var updates = siri
      .etBuilder()
      .withFramedVehicleJourneyRef(builder ->
        builder.withServiceDate(env.defaultServiceDate()).withVehicleJourneyRef("XXX")
      )
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected(null, "00:00:12")
          .call(STOP_B)
          .arriveAimedExpected("00:00:20", "00:00:22")
      )
      .buildEstimatedTimetableDeliveries();

    var result = siri.applyEstimatedTimetableWithFuzzyMatcher(updates);
    assertEquals(0, result.successful(), "Should fail gracefully");
    assertFailure(UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH, result);
  }

  /**
   * Two RAIL trips with identical stops and times but different internalPlanningCodes.
   * The SIRI update has a non-matching VehicleJourneyRef but includes a VehicleRef that
   * corresponds to one trip's planning code. The matcher should disambiguate using VehicleRef.
   */
  @Test
  void testFuzzyMatchByVehicleRefForRailTrip() {
    var railRoute = ENV_BUILDER.route("RailRoute", r -> r.withMode(TransitMode.RAIL));

    var railTrip1 = TripInput.of("RailTrip1")
      .withRoute(railRoute)
      .addStop(STOP_A, "0:00:10", "0:00:11")
      .addStop(STOP_B, "0:00:20", "0:00:21");

    var railTrip2 = TripInput.of("RailTrip2")
      .withRoute(railRoute)
      .addStop(STOP_A, "0:00:10", "0:00:11")
      .addStop(STOP_B, "0:00:20", "0:00:21");

    var env = ENV_BUILDER.addTrip(railTrip1, tb -> tb.withNetexInternalPlanningCode("47"))
      .addTrip(railTrip2, tb -> tb.withNetexInternalPlanningCode("48"))
      .build();

    var siri = SiriTestHelper.ofFuzzyMatching(env);

    var updates = siri
      .etBuilder()
      .withFramedVehicleJourneyRef(builder ->
        builder.withServiceDate(env.defaultServiceDate()).withVehicleJourneyRef("NONEXISTENT")
      )
      .withVehicleRef("47")
      .withVehicleMode(VehicleModesEnumeration.RAIL)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:00:11", "00:00:15")
          .call(STOP_B)
          .arriveAimedExpected("00:00:20", "00:00:25")
      )
      .buildEstimatedTimetableDeliveries();

    var result = siri.applyEstimatedTimetableWithFuzzyMatcher(updates);
    assertEquals(1, result.successful());
    assertEquals(
      "UPDATED | A 0:00:15 0:00:15 | B 0:00:25 0:00:25",
      env.tripData("RailTrip1").showTimetable()
    );
  }

  /**
   * BNR sends DatedVehicleJourneyRef at journey level (not inside FramedVehicleJourneyRef)
   * with numeric IDs that don't match any NeTEx DatedServiceJourney.
   * The fuzzy matcher should still resolve the trip via VehicleRef → internalPlanningCode.
   */
  @Test
  void testFuzzyMatchByVehicleRefWithDatedVehicleJourneyRefOnly() {
    var railRoute = ENV_BUILDER.route("RailRoute2", r -> r.withMode(TransitMode.RAIL));

    var railTrip = TripInput.of("RailTrip3")
      .withRoute(railRoute)
      .addStop(STOP_A, "0:00:10", "0:00:11")
      .addStop(STOP_B, "0:00:20", "0:00:21");

    var env = ENV_BUILDER.addTrip(railTrip, tb -> tb.withNetexInternalPlanningCode("406")).build();

    var siri = SiriTestHelper.ofFuzzyMatching(env);

    var updates = siri
      .etBuilder()
      .withDatedVehicleJourneyRef("406:2026-02-17")
      .withVehicleRef("406")
      .withVehicleMode(VehicleModesEnumeration.RAIL)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:00:11", "00:00:15")
          .call(STOP_B)
          .arriveAimedExpected("00:00:20", "00:00:25")
      )
      .buildEstimatedTimetableDeliveries();

    var result = siri.applyEstimatedTimetableWithFuzzyMatcher(updates);
    assertEquals(1, result.successful());
    assertEquals(
      "UPDATED | A 0:00:15 0:00:15 | B 0:00:25 0:00:25",
      env.tripData("RailTrip3").showTimetable()
    );
  }

  /**
   * Re-processing a fuzzy-matched trip with a routability change should produce MODIFIED
   * on both the first and second update, because the pattern differs from the scheduled
   * pattern regardless of how many times the update is applied.
   *
   * Scenario: RAIL trip with first-stop dropoff=NONE (board-only).
   * SIRI sends ArrivalBoardingActivity=ALIGHTING at the first stop, changing dropoff to SCHEDULED.
   * Both updates → MODIFIED (pattern changed relative to scheduled pattern).
   */
  @Test
  void reprocessedFuzzyMatchedTripWithRoutabilityChangeShouldRemainModified() {
    var railRoute = ENV_BUILDER.route("RailRoute", r -> r.withMode(TransitMode.RAIL));

    var railTrip = TripInput.of("RailTrip")
      .withRoute(railRoute)
      .addStop(STOP_A, "0:00:10", "0:00:11", PickDrop.SCHEDULED, PickDrop.NONE)
      .addStop(STOP_B, "0:00:20", "0:00:21");

    var env = ENV_BUILDER.addTrip(railTrip, tb -> tb.withNetexInternalPlanningCode("100")).build();

    var siri = SiriTestHelper.ofFuzzyMatching(env);

    // Verify scheduled pattern has NONE dropoff at first stop
    var scheduledPattern = env.tripData("RailTrip").scheduledTripPattern();
    assertEquals(PickDrop.NONE, scheduledPattern.getAlightType(0));

    // Build a SIRI update with ArrivalBoardingActivity=ALIGHTING at first stop
    // (re-enables dropoff: NONE → SCHEDULED, a routability change)
    var updates = siri
      .etBuilder()
      .withFramedVehicleJourneyRef(builder ->
        builder.withServiceDate(env.defaultServiceDate()).withVehicleJourneyRef("NONEXISTENT")
      )
      .withVehicleRef("100")
      .withVehicleMode(VehicleModesEnumeration.RAIL)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .arriveAimedExpected("00:00:10", "00:00:10")
          .withArrivalBoardingActivity(ArrivalBoardingActivityEnumeration.ALIGHTING)
          .departAimedExpected("00:00:11", "00:00:15")
          .call(STOP_B)
          .arriveAimedExpected("00:00:20", "00:00:25")
      )
      .buildEstimatedTimetableDeliveries();

    // First update: should produce MODIFIED (pattern changed due to routability change)
    var result1 = siri.applyEstimatedTimetableWithFuzzyMatcher(updates);
    assertSuccess(result1);
    assertEquals(
      "MODIFIED | A 0:00:10 0:00:15 | B 0:00:25 0:00:25",
      env.tripData("RailTrip").showTimetable()
    );

    // Second update (re-processing): should still be MODIFIED (pattern differs from scheduled)
    var result2 = siri.applyEstimatedTimetableWithFuzzyMatcher(updates);
    assertSuccess(result2);
    assertEquals(
      "MODIFIED | A 0:00:10 0:00:15 | B 0:00:25 0:00:25",
      env.tripData("RailTrip").showTimetable()
    );
  }

  private static void assertTripUpdated(TransitTestEnvironment env) {
    assertEquals(
      "UPDATED | A 0:00:15 0:00:15 | B 0:00:25 0:00:25",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }
}
