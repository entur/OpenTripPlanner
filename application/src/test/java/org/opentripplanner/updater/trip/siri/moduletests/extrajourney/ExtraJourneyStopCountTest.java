package org.opentripplanner.updater.trip.siri.moduletests.extrajourney;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.updater.spi.UpdateErrorType.TOO_FEW_STOPS;
import static org.opentripplanner.updater.spi.UpdateErrorType.TOO_MANY_STOPS;
import static org.opentripplanner.updater.spi.UpdateErrorType.UNKNOWN_STOP;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.organization.Operator;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.siri.SiriEtBuilder;
import org.opentripplanner.updater.trip.siri.SiriTestHelper;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;

/**
 * A second message for an extra journey that has already been added revises the trip on the pattern
 * it was added to, so the message has to describe that pattern call for call. A message that calls
 * more or fewer times, or at a quay the transit model does not know, cannot be applied to it and is
 * rejected.
 */
class ExtraJourneyStopCountTest implements RealtimeTestConstants {

  private static final String OPERATOR_ID = "operatorId";
  private static final String ROUTE_ID = "routeId";

  /** The timetable of the added journey, left untouched by a rejected revision. */
  private static final String ADDED_JOURNEY_TIMETABLE = "A U | A 0:02 0:02 | B 0:04 0:04";

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop STOP_A = envBuilder.stop(STOP_A_ID);
  private final RegularStop STOP_B = envBuilder.stop(STOP_B_ID);
  private final RegularStop STOP_C = envBuilder.stop(STOP_C_ID);

  private final Operator OPERATOR = envBuilder.operator(OPERATOR_ID);
  private final Route ROUTE = envBuilder.route(ROUTE_ID, OPERATOR);

  /** Only there to register the route the extra journey runs on. */
  private final TripInput TRIP_1_INPUT = TripInput.of(TRIP_1_ID)
    .withRoute(ROUTE)
    .addStop(STOP_A, "0:00:10", "0:00:11")
    .addStop(STOP_B, "0:00:20", "0:00:21");

  private final TransitTestEnvironment env = envBuilder.addTrip(TRIP_1_INPUT).build();
  private final SiriTestHelper siri = SiriTestHelper.of(env);

  @Test
  void rejectsARevisionWithMoreCallsThanTheAddedTrip() {
    addTheExtraJourney();

    var revision = extraJourney()
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:01", "00:06")
          .call(STOP_B)
          .arriveAimedExpected("00:03", "00:08")
          .departAimedExpected("00:03", "00:08")
          .call(STOP_C)
          .arriveAimedExpected("00:05", "00:10")
      )
      .buildEstimatedTimetableDeliveries();

    assertFailure(TOO_MANY_STOPS, siri.applyEstimatedTimetable(revision));
    assertEquals(ADDED_JOURNEY_TIMETABLE, env.tripData(ADDED_TRIP_ID).showTimetable());
  }

  @Test
  void rejectsARevisionWithFewerCallsThanTheAddedTrip() {
    addTheExtraJourney();

    var revision = extraJourney()
      .withEstimatedCalls(builder -> builder.call(STOP_A).departAimedExpected("00:01", "00:06"))
      .buildEstimatedTimetableDeliveries();

    assertFailure(TOO_FEW_STOPS, siri.applyEstimatedTimetable(revision));
    assertEquals(ADDED_JOURNEY_TIMETABLE, env.tripData(ADDED_TRIP_ID).showTimetable());
  }

  @Test
  void rejectsARevisionCallingAtAnUnknownQuay() {
    addTheExtraJourney();

    var revision = extraJourney()
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:01", "00:06")
          .call("noSuchQuay")
          .arriveAimedExpected("00:03", "00:08")
      )
      .buildEstimatedTimetableDeliveries();

    assertFailure(UNKNOWN_STOP, siri.applyEstimatedTimetable(revision));
    assertEquals(ADDED_JOURNEY_TIMETABLE, env.tripData(ADDED_TRIP_ID).showTimetable());
  }

  /**
   * The control: a revision that does describe the pattern call for call is applied, so the three
   * rejections above are about the message not matching the trip, not about the message shape.
   */
  @Test
  void revisesTheTimesOfTheAddedTrip() {
    addTheExtraJourney();

    var revision = extraJourney()
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:01", "00:06")
          .call(STOP_B)
          .arriveAimedExpected("00:03", "00:08")
      )
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(revision));
    assertEquals("A U | A 0:06 0:06 | B 0:08 0:08", env.tripData(ADDED_TRIP_ID).showTimetable());
  }

  private void addTheExtraJourney() {
    assertSuccess(siri.applyEstimatedTimetable(addedJourney()));
    assertEquals(ADDED_JOURNEY_TIMETABLE, env.tripData(ADDED_TRIP_ID).showTimetable());
  }

  private List<EstimatedTimetableDeliveryStructure> addedJourney() {
    return extraJourney()
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:01", "00:02")
          .call(STOP_B)
          .arriveAimedExpected("00:03", "00:04")
      )
      .buildEstimatedTimetableDeliveries();
  }

  private SiriEtBuilder extraJourney() {
    return siri
      .etBuilder()
      .withEstimatedVehicleJourneyCode(ADDED_TRIP_ID)
      .withIsExtraJourney(true)
      .withOperatorRef(OPERATOR_ID)
      .withLineRef(ROUTE_ID);
  }
}
