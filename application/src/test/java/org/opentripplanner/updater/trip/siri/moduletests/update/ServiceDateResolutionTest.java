package org.opentripplanner.updater.trip.siri.moduletests.update;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.organization.Operator;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.siri.SiriTestHelper;

/**
 * A journey that states no service date and reports no times still runs on the day of the dated
 * journey its code names.
 */
class ServiceDateResolutionTest implements RealtimeTestConstants {

  private static final String ADDED_JOURNEY_CODE = "newJourney";
  private static final String SCHEDULED_TRIP_ID = "ENT:ServiceJourney:1";
  private static final String SCHEDULED_DATED_TRIP_ID = "ENT:DatedServiceJourney:1";
  private static final String ROUTE_ID = "route-id";
  private static final String OPERATOR_ID = "operator-id";

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of();
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stop(STOP_B_ID);
  private final Operator OPERATOR = ENV_BUILDER.operator(OPERATOR_ID);
  private final Route ROUTE = ENV_BUILDER.route(ROUTE_ID, OPERATOR);

  private final TripInput TRIP_INPUT = TripInput.of(SCHEDULED_TRIP_ID)
    .withWithTripOnServiceDate(SCHEDULED_DATED_TRIP_ID)
    .withRoute(ROUTE)
    .addStop(STOP_A, "0:00:10", "0:00:11")
    .addStop(STOP_B, "0:00:20", "0:00:21");

  /** A cancellation naming a scheduled journey by its code alone runs on the dated journey's day. */
  @Test
  void journeyCodeNamesTheDatedJourneyOfAScheduledTrip() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.of(env);

    var cancellation = siri
      .etBuilder()
      .withEstimatedVehicleJourneyCode(SCHEDULED_DATED_TRIP_ID)
      .withCancellation(true)
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(cancellation));
    assertTrue(env.tripData(SCHEDULED_TRIP_ID).tripTimes().isCanceled());
  }

  /** A cancellation naming an added journey by its code alone runs on the added journey's day. */
  @Test
  void journeyCodeNamesTheDatedJourneyOfAnAddedTrip() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.of(env);

    var creation = siri
      .etBuilder()
      .withEstimatedVehicleJourneyCode(ADDED_JOURNEY_CODE)
      .withIsExtraJourney(true)
      .withOperatorRef(OPERATOR_ID)
      .withLineRef(ROUTE_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("0:00:11", "0:00:11")
          .call(STOP_B)
          .arriveAimedExpected("0:00:20", "0:00:20")
      )
      .buildEstimatedTimetableDeliveries();
    assertSuccess(siri.applyEstimatedTimetable(creation));

    var cancellation = siri
      .etBuilder()
      .withEstimatedVehicleJourneyCode(ADDED_JOURNEY_CODE)
      .withCancellation(true)
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(cancellation));
    assertTrue(env.tripData(ADDED_JOURNEY_CODE).tripTimes().isCanceled());
  }
}
