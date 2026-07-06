package org.opentripplanner.updater.trip.siri.moduletests.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import org.junit.jupiter.api.Test;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.siri.SiriTestHelper;

/**
 * Test that a real-time update is based on the trip's own planned pattern, also when the trip is
 * currently running on a realtime pattern shared with a trip of another route.
 * <p>
 * TripPatternCache caches realtime patterns keyed by StopPattern only. When trips of two routes
 * produce the same modified StopPattern (here: the same stop cancelled on trips with the same
 * stops), they share one cached realtime pattern, and the shared pattern's originalTripPattern
 * belongs to the first route. A later update to the second trip must be based on the planned
 * pattern of that trip: basing it on the shared pattern's originalTripPattern would apply the
 * first route's planned pickup/dropoff values to the second route's trip.
 */
class PlannedPatternRebaseTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of();
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stop(STOP_B_ID);
  private final RegularStop STOP_D = ENV_BUILDER.stop(STOP_D_ID);

  private final TripInput TRIP_1_INPUT = TripInput.of(TRIP_1_ID)
    .withRoute(ENV_BUILDER.route("Route1"))
    .withWithTripOnServiceDate(TRIP_1_ID)
    .addStop(STOP_A, "0:01:00", "0:01:01")
    .addStop(STOP_B, "0:01:10", "0:01:11")
    .addStop(STOP_D, "0:01:20", "0:01:21");

  // Same stops as TRIP_1, but boarding at stop B is planned with a different pickup type
  private final TripInput TRIP_2_INPUT = TripInput.of(TRIP_2_ID)
    .withRoute(ENV_BUILDER.route("Route2"))
    .withWithTripOnServiceDate(TRIP_2_ID)
    .addStop(STOP_A, "0:02:00", "0:02:01")
    .addStop(STOP_B, "0:02:10", "0:02:11", PickDrop.CALL_AGENCY, PickDrop.SCHEDULED)
    .addStop(STOP_D, "0:02:20", "0:02:21");

  @Test
  void updateIsRebasedOnPlannedPatternOfTheTripsOwnRoute() {
    var env = ENV_BUILDER.addTrip(TRIP_1_INPUT).addTrip(TRIP_2_INPUT).build();
    var siri = SiriTestHelper.of(env);

    // Batch 1: Cancel stop B on TRIP_1 — creates the TripPatternCache entry
    var batch1 = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:01:01", "00:01:01")
          .call(STOP_B)
          .withIsCancellation(true)
          .call(STOP_D)
          .arriveAimedExpected("00:01:20", "00:01:20")
      )
      .buildEstimatedTimetableDeliveries();
    assertSuccess(siri.applyEstimatedTimetable(batch1));

    // Batch 2: Cancel stop B on TRIP_2 — the cancellation masks the different planned pickup at
    // stop B, so both trips end up on the same cached realtime pattern
    var batch2 = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_2_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:02:01", "00:02:01")
          .call(STOP_B)
          .withIsCancellation(true)
          .call(STOP_D)
          .arriveAimedExpected("00:02:20", "00:02:20")
      )
      .buildEstimatedTimetableDeliveries();
    assertSuccess(siri.applyEstimatedTimetable(batch2));
    assertSame(
      env.tripData(TRIP_1_ID).tripPattern(),
      env.tripData(TRIP_2_ID).tripPattern(),
      "Precondition: both trips share the same cached realtime pattern"
    );

    // Batch 3: Update TRIP_2 without the cancellation, resolved through fuzzy matching (the
    // only path that resolves the realtime pattern for the trip). The update is based on the
    // planned pattern of TRIP_2's own route and reverts the trip to its scheduled pattern.
    var batch3 = siri
      .etBuilder()
      .withFramedVehicleJourneyRef(builder ->
        builder.withServiceDate(env.defaultServiceDate()).withVehicleJourneyRef("XXX")
      )
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:02:01", "00:02:02")
          .call(STOP_B)
          .arriveAimedExpected("00:02:10", "00:02:12")
          .departAimedExpected("00:02:11", "00:02:13")
          .call(STOP_D)
          .arriveAimedExpected("00:02:20", "00:02:25")
      )
      .buildEstimatedTimetableDeliveries();
    assertSuccess(siri.applyEstimatedTimetableWithFuzzyMatcher(batch3));

    var trip2Data = env.tripData(TRIP_2_ID);
    assertSame(trip2Data.scheduledTripPattern(), trip2Data.tripPattern());
    assertEquals(PickDrop.CALL_AGENCY, trip2Data.tripPattern().getBoardType(1));
    assertEquals(
      "U | A 0:02:02 0:02:02 | B 0:02:12 0:02:13 | D 0:02:25 0:02:25",
      trip2Data.showTimetable()
    );
  }
}
