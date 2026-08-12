package org.opentripplanner.updater.trip.siri.moduletests.extrajourney;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * An extra journey brings its own aimed times, and midnight of the service date is one a journey
 * can legitimately be aimed at. The pattern created for it must therefore carry the reported
 * midnight in its scheduled timetable, so the journey is published as late against midnight rather
 * than on time at its predicted time.
 */
class MidnightAimedTimeTest implements RealtimeTestConstants {

  private static final String ADDED_TRIP_ID = "newJourney";
  private static final String OPERATOR_ID = "operatorId";
  private static final String ROUTE_ID = "routeId";

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop stopA = envBuilder.stop(STOP_A_ID);
  private final RegularStop stopB = envBuilder.stop(STOP_B_ID);
  private final RegularStop stopC = envBuilder.stop(STOP_C_ID);
  private final RegularStop stopD = envBuilder.stop(STOP_D_ID);

  private final Operator operator = envBuilder.operator(OPERATOR_ID);
  private final Route route = envBuilder.route(ROUTE_ID, operator);

  private final TransitTestEnvironment env = envBuilder
    .addTrip(
      TripInput.of(TRIP_1_ID)
        .withRoute(route)
        .addStop(stopA, "0:00:10", "0:00:11")
        .addStop(stopB, "0:00:20", "0:00:21")
    )
    .build();
  private final SiriTestHelper siri = SiriTestHelper.of(env);

  @Test
  void extraJourneyAimedAtMidnight() {
    var updates = siri
      .etBuilder()
      .withEstimatedVehicleJourneyCode(ADDED_TRIP_ID)
      .withIsExtraJourney(true)
      .withOperatorRef(OPERATOR_ID)
      .withLineRef(ROUTE_ID)
      .withRecordedCalls(builder -> builder.call(stopC).departAimedActual("00:00", "00:02"))
      .withEstimatedCalls(builder -> builder.call(stopD).arriveAimedExpected("00:03", "00:04"))
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(updates));

    var tripData = env.tripData(ADDED_TRIP_ID);
    assertEquals("S | C 0:00 0:00 | D 0:03 0:03", tripData.showScheduledTimetable());
    assertEquals("A U | C [R] 0:02 0:02 | D 0:04 0:04", tripData.showTimetable());
  }
}
