package org.opentripplanner.updater.trip.siri.moduletests.fuzzymatching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.SiriTestHelper;
import uk.org.siri.siri21.VehicleModesEnumeration;

class FuzzyTripMatchingTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of();
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stop(STOP_B_ID);
  private final RegularStop STOP_C = ENV_BUILDER.stop(STOP_C_ID);
  private final RegularStop STOP_D = ENV_BUILDER.stop(STOP_D_ID);

  private final TripInput TRIP_INPUT = TripInput.of(TRIP_1_ID)
    .addStop(STOP_A, "0:00:10", "0:00:11")
    .addStop(STOP_B, "0:00:20", "0:00:21");

  private final TripInput FOUR_STOP_TRIP = TripInput.of(TRIP_1_ID)
    .addStop(STOP_A, "0:00:10", "0:00:11")
    .addStop(STOP_B, "0:00:20", "0:00:21")
    .addStop(STOP_C, "0:00:30", "0:00:31")
    .addStop(STOP_D, "0:00:40", "0:00:41");

  /**
   * Update calls without changing the pattern. Fuzzy matching.
   */
  @Test
  void testUpdateJourneyWithFuzzyMatching() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.of(env);

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
    assertSuccess(result);
    assertEquals(
      "UPDATED | A 0:00:15 0:00:15 | B 0:00:25 0:00:25",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }

  /**
   * Update calls without changing the pattern. Fuzzy matching.
   * Edge case: invalid reference to vehicle journey and missing aimed departure time.
   */
  @Test
  void testUpdateJourneyWithFuzzyMatchingAndMissingAimedDepartureTime() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.of(env);

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
   * Test fuzzy matching when SIRI calls are out of order due to the Nordic SIRI Profile's
   * "missed recording" exception. In this case, the current stop (C, Order 3) is placed in
   * EstimatedCalls, while a later stop (D, Order 4) appears in RecordedCalls.
   * Without sorting by Order, getLast() returns C instead of D, causing fuzzy matching to fail.
   */
  @Test
  void testFuzzyMatchingWithOutOfOrderCalls() {
    var env = ENV_BUILDER.addTrip(FOUR_STOP_TRIP).build();
    var siri = SiriTestHelper.of(env);

    // Build a SIRI message where:
    // RecordedCalls: A (Order 1), B (Order 2), D (Order 4)
    // EstimatedCalls: C (Order 3)
    // This simulates the "missed recording" exception where C is not yet recorded
    // but D has already been recorded (e.g., the vehicle skipped ahead).
    var updates = siri
      .etBuilder()
      .withRecordedCalls(builder ->
        builder
          .call(STOP_A)
          .withOrder(1)
          .departAimedActual("00:00:11", "00:00:15")
          .call(STOP_B)
          .withOrder(2)
          .departAimedActual("00:00:21", "00:00:25")
          .call(STOP_D)
          .withOrder(4)
          .arriveAimedActual("00:00:40", "00:00:48")
      )
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_C)
          .withOrder(3)
          .arriveAimedExpected("00:00:30", "00:00:35")
          .departAimedExpected("00:00:31", "00:00:36")
      )
      .buildEstimatedTimetableDeliveries();
    var result = siri.applyEstimatedTimetableWithFuzzyMatcher(updates);
    assertSuccess(result);
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
      .withNetexInternalPlanningCode("47")
      .addStop(STOP_A, "0:00:10", "0:00:11")
      .addStop(STOP_B, "0:00:20", "0:00:21");

    var railTrip2 = TripInput.of("RailTrip2")
      .withRoute(railRoute)
      .withNetexInternalPlanningCode("48")
      .addStop(STOP_A, "0:00:10", "0:00:11")
      .addStop(STOP_B, "0:00:20", "0:00:21");

    var env = ENV_BUILDER.addTrip(railTrip1).addTrip(railTrip2).build();

    var siri = SiriTestHelper.of(env);

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
    assertSuccess(result);
    assertEquals(
      "UPDATED | A 0:00:15 0:00:15 | B 0:00:25 0:00:25",
      env.tripData("RailTrip1").showTimetable()
    );
  }

  /**
   * The fuzzy matcher should still resolve the trip via VehicleRef → internalPlanningCode
   * when only DatedVehicleJourneyRef is provided.
   */
  @Test
  void testFuzzyMatchByVehicleRefWithDatedVehicleJourneyRefOnly() {
    var railRoute = ENV_BUILDER.route("RailRoute2", r -> r.withMode(TransitMode.RAIL));

    var railTrip = TripInput.of("RailTrip3")
      .withRoute(railRoute)
      .withNetexInternalPlanningCode("406")
      .addStop(STOP_A, "0:00:10", "0:00:11")
      .addStop(STOP_B, "0:00:20", "0:00:21");

    var env = ENV_BUILDER.addTrip(railTrip).build();

    var siri = SiriTestHelper.of(env);

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
    assertSuccess(result);
    assertEquals(
      "UPDATED | A 0:00:15 0:00:15 | B 0:00:25 0:00:25",
      env.tripData("RailTrip3").showTimetable()
    );
  }

  @Test
  void visitNumber() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.of(env);

    var updates = siri
      .etBuilder()
      .withRecordedCalls(builder ->
        builder
          .call(STOP_A)
          .clearOrder()
          .withVisitNumber(1)
          .departAimedActual("00:00:11", "00:00:15")
      )
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_B)
          .clearOrder()
          .withVisitNumber(2)
          .arriveAimedExpected("00:00:20", "00:00:25")
      )
      .buildEstimatedTimetableDeliveries();
    var result = siri.applyEstimatedTimetableWithFuzzyMatcher(updates);
    assertSuccess(result);
    assertEquals(
      "UPDATED | A [R] 0:00:15 0:00:15 | B 0:00:25 0:00:25",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }
}
