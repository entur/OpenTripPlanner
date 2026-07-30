package org.opentripplanner.updater.trip.siri.moduletests.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.accessibility.Accessibility;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.organization.Operator;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.siri.SiriTestHelper;

/**
 * SIRI-ET says nothing about the accessibility of the vehicle, so a SIRI update must never change
 * the wheelchair accessibility of the trip it updates - not even to "no information".
 */
class WheelchairAccessibilityTest implements RealtimeTestConstants {

  private static final String ADDED_TRIP_ID = "newJourney";
  private static final String OPERATOR_ID = "operatorId";
  private static final String ROUTE_ID = "routeId";

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of();
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stop(STOP_B_ID);
  private final RegularStop STOP_C = ENV_BUILDER.stop(STOP_C_ID);

  private final Operator OPERATOR = ENV_BUILDER.operator(OPERATOR_ID);
  private final Route ROUTE = ENV_BUILDER.route(ROUTE_ID, OPERATOR);

  private final TripInput TRIP_INPUT = TripInput.of(TRIP_1_ID)
    .withRoute(ROUTE)
    .withWithTripOnServiceDate(TRIP_1_ID)
    .addStop(STOP_A, "0:10", "0:10")
    .addStop(STOP_B, "0:20", "0:20");

  /** The trip is accessible according to the static data. */
  private final TransitTestEnvironment env = ENV_BUILDER.addTrip(TRIP_INPUT, trip ->
    trip.withWheelchairBoarding(Accessibility.POSSIBLE)
  ).build();
  private final SiriTestHelper siri = SiriTestHelper.of(env);

  @Test
  void accessibilityOfUpdatedTripIsKept() {
    var update = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:10", "00:11")
          .call(STOP_B)
          .arriveAimedExpected("00:20", "00:21")
      )
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(update));

    assertEquals(
      Accessibility.POSSIBLE,
      env.tripData(TRIP_1_ID).tripTimes().getWheelchairAccessibility()
    );
  }

  /**
   * An extra call replaces the stop pattern and rebuilds the trip times, which must still carry the
   * accessibility of the trip.
   */
  @Test
  void accessibilityOfTripWithExtraCallIsKept() {
    var update = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:10", "00:10")
          .call(STOP_C)
          .withIsExtraCall(true)
          .arriveAimedExpected("00:15", "00:15")
          .departAimedExpected("00:15", "00:15")
          .call(STOP_B)
          .arriveAimedExpected("00:20", "00:20")
      )
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(update));

    assertEquals(
      Accessibility.POSSIBLE,
      env.tripData(TRIP_1_ID).tripTimes().getWheelchairAccessibility()
    );
  }

  /**
   * An extra journey is not part of the static schedule, so there is nothing to preserve and nothing
   * SIRI could state - its accessibility is unknown.
   */
  @Test
  void accessibilityOfExtraJourneyIsUnknown() {
    var update = siri
      .etBuilder()
      .withEstimatedVehicleJourneyCode(ADDED_TRIP_ID)
      .withIsExtraJourney(true)
      .withOperatorRef(OPERATOR_ID)
      .withLineRef(ROUTE_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:30", "00:30")
          .call(STOP_B)
          .arriveAimedExpected("00:40", "00:40")
      )
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(update));

    assertEquals(
      Accessibility.NO_INFORMATION,
      env.tripData(ADDED_TRIP_ID).tripTimes().getWheelchairAccessibility()
    );
  }
}
