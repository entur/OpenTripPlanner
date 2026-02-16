package org.opentripplanner.updater.trip.siri.moduletests.cancellation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.SiriTestHelper;

/**
 * When a RecordedCall has both cancellation=true and an actual departure time, the cancelled
 * state should take precedence over the recorded state. This matches the legacy SIRI adapter
 * behavior where [C] is shown instead of [R].
 */
class CancelledRecordedCallTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of();
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stop(STOP_B_ID);

  private final TripInput TRIP_INPUT = TripInput.of(TRIP_1_ID)
    .withWithTripOnServiceDate(TRIP_1_ID)
    .addStop(STOP_A, "0:01:00", "0:01:01")
    .addStop(STOP_B, "0:01:10", "0:01:11");

  /**
   * A RecordedCall with both cancellation=true and an actual departure time should show [C]
   * (cancelled), not [R] (recorded). Cancelled takes precedence.
   */
  @Test
  void cancelledRecordedCallShouldShowCancelled() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.of(env);

    var updates = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withRecordedCalls(builder ->
        builder.call(STOP_A).withIsCancellation(true).departAimedActual("00:01:01", "00:01:01")
      )
      .withEstimatedCalls(builder ->
        builder.call(STOP_B).arriveAimedExpected("00:01:10", "00:01:10")
      )
      .buildEstimatedTimetableDeliveries();

    var result = siri.applyEstimatedTimetable(updates);

    assertSuccess(result);
    assertEquals(
      "MODIFIED | A [C] 0:01:01 0:01:01 | B 0:01:10 0:01:10",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }
}
