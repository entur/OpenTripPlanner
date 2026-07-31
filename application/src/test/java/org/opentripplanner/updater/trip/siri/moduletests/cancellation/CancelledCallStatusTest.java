package org.opentripplanner.updater.trip.siri.moduletests.cancellation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.siri.SiriEtBuilder;
import org.opentripplanner.updater.trip.siri.SiriTestHelper;
import uk.org.siri.siri21.CallStatusEnumeration;
import uk.org.siri.siri21.DepartureBoardingActivityEnumeration;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;

/**
 * A SIRI-ET call can be cancelled without the {@code Cancellation} element: an {@code ArrivalStatus}
 * or {@code DepartureStatus} of {@code cancelled} cancels that one end of the call. It closes
 * boarding or alighting there, but - unlike the element - it does not cancel the stop itself, so the
 * stop carries no {@code [C]} flag and its times are applied as reported.
 */
class CancelledCallStatusTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of();
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stop(STOP_B_ID);
  private final RegularStop STOP_C = ENV_BUILDER.stop(STOP_C_ID);

  private final TripInput TRIP_INPUT = TripInput.of(TRIP_1_ID)
    .withWithTripOnServiceDate(TRIP_1_ID)
    .addStop(STOP_A, "0:01:00", "0:01:01")
    .addStop(STOP_B, "0:01:10", "0:01:11")
    .addStop(STOP_C, "0:01:20", "0:01:21");

  @Test
  void departureStatusCancelledClosesBoardingOnly() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.of(env);

    assertSuccess(
      siri.applyEstimatedTimetable(
        journeyWithMiddleCall(siri, call ->
          call.withDepartureStatus(CallStatusEnumeration.CANCELLED)
        )
      )
    );

    // The stop itself is not cancelled: no [C] flag, and the reported times apply.
    assertEquals(
      "P U | A 0:01:01 0:01:01 | B 0:01:10 0:01:11 | C 0:01:20 0:01:20",
      env.tripData(TRIP_1_ID).showTimetable()
    );

    var pattern = env.tripData(TRIP_1_ID).tripPattern();
    assertEquals(PickDrop.CANCELLED, pattern.getBoardType(1), "the departure is cancelled");
    assertEquals(PickDrop.SCHEDULED, pattern.getAlightType(1), "the arrival is not");
  }

  @Test
  void arrivalStatusCancelledClosesAlightingOnly() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.of(env);

    assertSuccess(
      siri.applyEstimatedTimetable(
        journeyWithMiddleCall(siri, call -> call.withArrivalStatus(CallStatusEnumeration.CANCELLED))
      )
    );

    var pattern = env.tripData(TRIP_1_ID).tripPattern();
    assertEquals(PickDrop.SCHEDULED, pattern.getBoardType(1), "the departure is not cancelled");
    assertEquals(PickDrop.CANCELLED, pattern.getAlightType(1), "the arrival is");
  }

  /**
   * A cancelled call end cannot be re-opened by the boarding activity of the same end: the
   * cancellation governs boarding, whatever activity the message reports alongside it.
   */
  @Test
  void boardingActivityDoesNotReopenACancelledEnd() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.of(env);

    assertSuccess(
      siri.applyEstimatedTimetable(
        journeyWithMiddleCall(siri, call ->
          call
            .withDepartureStatus(CallStatusEnumeration.CANCELLED)
            .withDepartureBoardingActivity(DepartureBoardingActivityEnumeration.BOARDING)
        )
      )
    );

    assertEquals(
      PickDrop.CANCELLED,
      env.tripData(TRIP_1_ID).tripPattern().getBoardType(1),
      "boarding stays cancelled"
    );
  }

  /**
   * Cancelling an end that the timetable already closed changes nothing, so the trip keeps running on
   * its scheduled pattern. Claiming a change here would publish a real-time pattern identical in
   * routability to the scheduled one.
   */
  @Test
  void cancelledStatusOnAnEndThatWasAlreadyClosedChangesNothing() {
    var tripInput = TripInput.of(TRIP_1_ID)
      .withWithTripOnServiceDate(TRIP_1_ID)
      .addStop(STOP_A, "0:01:00", "0:01:01")
      .addStop(STOP_B, "0:01:10", "0:01:11", PickDrop.NONE, PickDrop.SCHEDULED)
      .addStop(STOP_C, "0:01:20", "0:01:21");

    var env = ENV_BUILDER.addTrip(tripInput).build();
    var siri = SiriTestHelper.of(env);

    assertSuccess(
      siri.applyEstimatedTimetable(
        journeyWithMiddleCall(siri, call ->
          call.withDepartureStatus(CallStatusEnumeration.CANCELLED)
        )
      )
    );

    assertEquals(
      "U | A 0:01:01 0:01:01 | B 0:01:10 0:01:11 | C 0:01:20 0:01:20",
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
   * The journey of every test: three fully reported calls, with the middle one configured by the
   * caller.
   */
  private List<EstimatedTimetableDeliveryStructure> journeyWithMiddleCall(
    SiriTestHelper siri,
    Consumer<SiriEtBuilder.EstimatedCallsBuilder> middleCall
  ) {
    return siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withEstimatedCalls(builder -> {
        builder.call(STOP_A).departAimedExpected("00:01:01", "00:01:01");
        var middle = builder
          .call(STOP_B)
          .arriveAimedExpected("00:01:10", "00:01:10")
          .departAimedExpected("00:01:11", "00:01:11");
        middleCall.accept(middle);
        return middle.call(STOP_C).arriveAimedExpected("00:01:20", "00:01:20");
      })
      .buildEstimatedTimetableDeliveries();
  }
}
