package org.opentripplanner.updater.trip.siri.moduletests.cancellation;

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

class CancelledStopTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of();
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stop(STOP_B_ID);
  private final RegularStop STOP_D = ENV_BUILDER.stop(STOP_D_ID);

  private final TripInput TRIP_INPUT = TripInput.of(TRIP_1_ID)
    .withWithTripOnServiceDate(TRIP_1_ID)
    .addStop(STOP_A, "0:01:00", "0:01:01")
    .addStop(STOP_B, "0:01:10", "0:01:11")
    .addStop(STOP_D, "0:01:20", "0:01:21");

  @Test
  void testCancelStop() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.of(env);

    var updates = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:01:01", "00:01:01")
          .call(STOP_B)
          .withIsCancellation(true)
          .call(STOP_D)
          .arriveAimedExpected("00:01:30", "00:01:30")
      )
      .buildEstimatedTimetableDeliveries();

    var result = siri.applyEstimatedTimetable(updates);

    assertSuccess(result);
    assertEquals(
      "P U | A 0:01:01 0:01:01 | B [C] 0:01:10 0:01:11 | D 0:01:30 0:01:30",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }

  /**
   * When a journey has predictionInaccurate=true and a stop has isCancellation=true,
   * the cancelled flag [C] must not be overwritten by prediction inaccurate [PI].
   */
  @Test
  void testCancelledStopWithPredictionInaccurate() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.of(env);

    var updates = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withPredictionInaccurate(true)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:01:01", "00:01:01")
          .call(STOP_B)
          .withIsCancellation(true)
          .call(STOP_D)
          .arriveAimedExpected("00:01:30", "00:01:30")
      )
      .buildEstimatedTimetableDeliveries();

    var result = siri.applyEstimatedTimetable(updates);

    assertSuccess(result);
    assertEquals(
      "P U | A [PI] 0:01:01 0:01:01 | B [C] 0:01:10 0:01:11 | D [PI] 0:01:30 0:01:30",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }

  /**
   * A cancelled call forbids boarding and alighting at its stop - but only where the timetable
   * allowed them in the first place. SIRI-ET states routability changes, so a stop that was already
   * scheduled with no boarding stays {@link PickDrop#NONE}: cancelling it would claim a pattern
   * change the message does not make.
   */
  @Test
  void cancelledCallOnlyCancelsWhatWasRoutable() {
    var tripInput = TripInput.of(TRIP_1_ID)
      .withWithTripOnServiceDate(TRIP_1_ID)
      .addStop(STOP_A, "0:01:00", "0:01:01")
      .addStop(STOP_B, "0:01:10", "0:01:11", PickDrop.NONE, PickDrop.SCHEDULED)
      .addStop(STOP_D, "0:01:20", "0:01:21");

    var env = ENV_BUILDER.addTrip(tripInput).build();
    var siri = SiriTestHelper.of(env);

    var updates = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:01:01", "00:01:01")
          .call(STOP_B)
          .withIsCancellation(true)
          .call(STOP_D)
          .arriveAimedExpected("00:01:30", "00:01:30")
      )
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(updates));

    assertEquals(
      "P U | A 0:01:01 0:01:01 | B [C] 0:01:10 0:01:11 | D 0:01:30 0:01:30",
      env.tripData(TRIP_1_ID).showTimetable()
    );

    var pattern = env.tripData(TRIP_1_ID).tripPattern();
    assertEquals(PickDrop.NONE, pattern.getBoardType(1), "boarding was already not possible");
    assertEquals(PickDrop.CANCELLED, pattern.getAlightType(1), "alighting is cancelled");
  }

  /**
   * A cancelled call is not, by itself, real-time information about when the trip runs. When the
   * journey reports no times at all and the cancellation changes no pattern - the stop is routable
   * in neither direction - nothing about the trip has been updated, so it stays SCHEDULED. SIRI-ET
   * states the pattern it runs and the times it predicts; it never declares itself updated.
   */
  @Test
  void timelessJourneyWithACancelledNonRoutableCallStaysScheduled() {
    var tripInput = TripInput.of(TRIP_1_ID)
      .withWithTripOnServiceDate(TRIP_1_ID)
      .addStop(STOP_A, "0:01:00", "0:01:01")
      .addStop(STOP_B, "0:01:10", "0:01:11", PickDrop.NONE, PickDrop.NONE)
      .addStop(STOP_D, "0:01:20", "0:01:21");

    var env = ENV_BUILDER.addTrip(tripInput).build();
    var siri = SiriTestHelper.of(env);

    var updates = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withEstimatedCalls(builder ->
        builder.call(STOP_A).call(STOP_B).withIsCancellation(true).call(STOP_D)
      )
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(updates));

    assertEquals(
      "S | A [ND] 0:01 0:01:01 | B [C] 0:01:10 0:01:11 | D [ND] 0:01:20 0:01:21",
      env.tripData(TRIP_1_ID).showTimetable()
    );

    var tripData = env.tripData(TRIP_1_ID);
    assertSame(
      tripData.scheduledTripPattern(),
      tripData.tripPattern(),
      "no real-time pattern should have been created"
    );
  }

  /**
   * Cancelling a call at a stop that is not routable in either direction - a technical stop the
   * vehicle only passes through - changes nothing about the stop pattern, so the trip keeps running
   * on its scheduled pattern and is UPDATED rather than pattern-modified.
   */
  @Test
  void cancelledCallOnANonRoutableStopChangesNoPattern() {
    var tripInput = TripInput.of(TRIP_1_ID)
      .withWithTripOnServiceDate(TRIP_1_ID)
      .addStop(STOP_A, "0:01:00", "0:01:01")
      .addStop(STOP_B, "0:01:10", "0:01:11", PickDrop.NONE, PickDrop.NONE)
      .addStop(STOP_D, "0:01:20", "0:01:21");

    var env = ENV_BUILDER.addTrip(tripInput).build();
    var siri = SiriTestHelper.of(env);

    var updates = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:01:01", "00:01:01")
          .call(STOP_B)
          .withIsCancellation(true)
          .call(STOP_D)
          .arriveAimedExpected("00:01:30", "00:01:30")
      )
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(updates));

    assertEquals(
      "U | A 0:01:01 0:01:01 | B [C] 0:01:10 0:01:11 | D 0:01:30 0:01:30",
      env.tripData(TRIP_1_ID).showTimetable()
    );

    var tripData = env.tripData(TRIP_1_ID);
    assertSame(
      tripData.scheduledTripPattern(),
      tripData.tripPattern(),
      "no real-time pattern should have been created"
    );
  }
}
