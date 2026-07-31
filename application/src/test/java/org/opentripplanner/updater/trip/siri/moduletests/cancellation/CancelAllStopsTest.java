package org.opentripplanner.updater.trip.siri.moduletests.cancellation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.siri.SiriTestHelper;
import uk.org.siri.siri21.ArrivalBoardingActivityEnumeration;
import uk.org.siri.siri21.CallStatusEnumeration;
import uk.org.siri.siri21.DepartureBoardingActivityEnumeration;

/**
 * Cancelling all individual stops (as opposed to journey-level cancellation) should result in an
 * implicit trip cancellation when all stops are non-routable.
 * TODO RT_VP: This is a non-regression test that captures the existing behavior.
 *             We should verify that this behavior is acceptable/correct.
 */
class CancelAllStopsTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of();
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stop(STOP_B_ID);

  private final TripInput TRIP_INPUT = TripInput.of(TRIP_1_ID)
    .withWithTripOnServiceDate(TRIP_1_ID)
    .addStop(STOP_A, "0:00:10", "0:00:11")
    .addStop(STOP_B, "0:00:20", "0:00:21");

  @Test
  void testCancelAllStopsCancelsTrip() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.of(env);

    var updates = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:00:11", "00:00:11")
          .withIsCancellation(true)
          .call(STOP_B)
          .arriveAimedExpected("00:00:20", "00:00:20")
          .withIsCancellation(true)
      )
      .buildEstimatedTimetableDeliveries();

    var result = siri.applyEstimatedTimetable(updates);

    assertSuccess(result);
    assertTrue(env.tripData(TRIP_1_ID).tripTimes().isCanceled());
  }

  /**
   * A journey can cancel all of its calls through their arrival and departure statuses instead of the
   * {@code Cancellation} element. Nobody can board or alight anywhere either way, so the trip is
   * cancelled - on its scheduled pattern, keeping the scheduled times.
   */
  @Test
  void journeyWithEveryCallCancelledByStatusIsCancelled() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.of(env);

    var updates = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:00:11", "00:00:11")
          .withDepartureStatus(CallStatusEnumeration.CANCELLED)
          .withArrivalStatus(CallStatusEnumeration.CANCELLED)
          .call(STOP_B)
          .arriveAimedExpected("00:00:20", "00:00:20")
          .withArrivalStatus(CallStatusEnumeration.CANCELLED)
          .withDepartureStatus(CallStatusEnumeration.CANCELLED)
      )
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(updates));

    var tripData = env.tripData(TRIP_1_ID);
    assertTrue(tripData.tripTimes().isCanceled());
    assertSame(
      tripData.scheduledTripPattern(),
      tripData.tripPattern(),
      "a cancelled trip stays on its scheduled pattern"
    );
    assertEquals(
      "C U | A 0:00:10 0:00:11 | B 0:00:20 0:00:21",
      tripData.showTimetable(),
      "a cancelled trip keeps its scheduled times"
    );
  }

  /**
   * The rule is about routability, not about how the message phrased it: a journey where no call
   * allows boarding or alighting does not run either, even though nothing is cancelled.
   */
  @Test
  void journeyWhereNoCallAllowsBoardingOrAlightingIsCancelled() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.of(env);

    var updates = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:00:11", "00:00:11")
          .withArrivalBoardingActivity(ArrivalBoardingActivityEnumeration.NO_ALIGHTING)
          .withDepartureBoardingActivity(DepartureBoardingActivityEnumeration.NO_BOARDING)
          .call(STOP_B)
          .arriveAimedExpected("00:00:20", "00:00:20")
          .withArrivalBoardingActivity(ArrivalBoardingActivityEnumeration.NO_ALIGHTING)
          .withDepartureBoardingActivity(DepartureBoardingActivityEnumeration.NO_BOARDING)
      )
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(updates));

    assertTrue(env.tripData(TRIP_1_ID).tripTimes().isCanceled());
  }
}
