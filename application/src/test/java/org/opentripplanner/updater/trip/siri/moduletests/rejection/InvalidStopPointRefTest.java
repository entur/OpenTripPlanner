package org.opentripplanner.updater.trip.siri.moduletests.rejection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.trip.siri.SiriTestHelper;

class InvalidStopPointRefTest {

  private static Stream<Arguments> cases() {
    return Stream.of("", " ", "   ", "\n", "null", "\t", null).flatMap(id ->
      Stream.of(Arguments.of(id, true), Arguments.of(id, false))
    );
  }

  @ParameterizedTest(name = "invalid id of ''{0}'', extraJourney={1}")
  @MethodSource("cases")
  void rejectEmptyStopPointRef(String invalidRef, boolean extraJourney) {
    var env = TransitTestEnvironment.of().build();
    var siri = SiriTestHelper.of(env);

    // journey contains empty stop point ref elements
    // happens in the South Tyrolian feed: https://github.com/noi-techpark/odh-mentor-otp/issues/213
    var invalidJourney = siri
      .etBuilder()
      .withEstimatedVehicleJourneyCode("invalid-journey")
      .withOperatorRef("unknown-operator")
      .withLineRef("unknown-line")
      .withIsExtraJourney(extraJourney)
      .withEstimatedCalls(builder ->
        builder
          .call(invalidRef)
          .departAimedExpected("10:58", "10:48")
          .call(invalidRef)
          .arriveAimedExpected("10:08", "10:58")
      )
      .buildEstimatedTimetableDeliveries();

    var result = siri.applyEstimatedTimetable(invalidJourney);
    assertEquals(0, result.successful());
    assertFailure(UpdateErrorType.EMPTY_STOP_POINT_REF, result);
  }

  /**
   * A trip update describes calls at fixed stops: a StopPointRef that only resolves to a flex stop
   * names no fixed stop, so the call is at an unknown stop and the update is rejected rather than
   * replacing the scheduled stop with the flex stop.
   */
  @Test
  void rejectUpdateWithFlexStopPointRef() {
    var envBuilder = TransitTestEnvironment.of();
    var stopA = envBuilder.stop("stopA");
    var stopB = envBuilder.stop("stopB");
    var areaStop = envBuilder.areaStop("areaStop");
    var env = envBuilder
      .addTrip(
        TripInput.of("trip1")
          .withWithTripOnServiceDate("trip1")
          .addStop(stopA, "10:00", "10:00")
          .addStop(stopB, "10:10", "10:10")
      )
      .build();
    var siri = SiriTestHelper.of(env);

    var updates = siri
      .etBuilder()
      .withDatedVehicleJourneyRef("trip1")
      .withEstimatedCalls(builder ->
        builder
          .call(stopA)
          .departAimedExpected("10:00", "10:01")
          .call(areaStop)
          .arriveAimedExpected("10:10", "10:11")
      )
      .buildEstimatedTimetableDeliveries();

    var result = siri.applyEstimatedTimetable(updates);

    assertFailure(UpdateErrorType.UNKNOWN_STOP, result);
    assertFalse(
      env.tripData("trip1").tripTimes().hasAnyUpdates(),
      "The scheduled trip must be left alone"
    );
  }
}
