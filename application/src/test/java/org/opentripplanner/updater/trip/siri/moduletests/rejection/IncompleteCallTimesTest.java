package org.opentripplanner.updater.trip.siri.moduletests.rejection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_ARRIVAL_TIME;
import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_DEPARTURE_TIME;
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
import org.opentripplanner.updater.trip.LegacyUpdaterOnly;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.UnifiedUpdaterOnly;
import org.opentripplanner.updater.trip.siri.SiriTestHelper;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;

/**
 * The Nordic SIRI profile requires each call to state an aimed and an expected/actual time on
 * both ends, except the first call's arrival and the last call's departure. The unified
 * implementation rejects a journey that falls short; legacy tolerates the holes (accepted
 * divergence). Each scenario is pinned by a pair of tests, one per implementation.
 */
class IncompleteCallTimesTest implements RealtimeTestConstants {

  private static final String ADDED_TRIP_ID = "newJourney";
  private static final String OPERATOR_ID = "operatorId";
  private static final String ROUTE_ID = "routeId";

  private static final String LEGACY_TOLERATES =
    "The legacy implementation tolerates incomplete call times - see the companion test.";
  private static final String UNIFIED_REJECTS =
    "The unified implementation rejects incomplete call times at parse - see the companion test.";

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of();
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stop(STOP_B_ID);
  private final RegularStop STOP_C = ENV_BUILDER.stop(STOP_C_ID);

  private final Operator OPERATOR = ENV_BUILDER.operator(OPERATOR_ID);
  private final Route ROUTE = ENV_BUILDER.route(ROUTE_ID, OPERATOR);

  private final TripInput TRIP_INPUT = TripInput.of(TRIP_1_ID)
    .withRoute(ROUTE)
    .withWithTripOnServiceDate(TRIP_1_ID)
    .addStop(STOP_A, "0:00:10", "0:00:11")
    .addStop(STOP_B, "0:00:20", "0:00:21")
    .addStop(STOP_C, "0:00:30", "0:00:31");

  private final TransitTestEnvironment env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
  private final SiriTestHelper siri = SiriTestHelper.of(env);

  /* A middle call carrying only aimed times states no real-time information. */

  @LegacyUpdaterOnly(UNIFIED_REJECTS)
  @Test
  void middleCallWithAimedTimesOnlyIsFlaggedNoData() {
    var result = siri.applyEstimatedTimetable(middleCallWithAimedTimesOnly());

    assertSuccess(result);
    assertEquals(
      "U | A 0:00:15 0:00:15 | B [ND] 0:00:20 0:00:21 | C 0:00:35 0:00:35",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }

  @UnifiedUpdaterOnly(LEGACY_TOLERATES)
  @Test
  void middleCallWithAimedTimesOnlyIsRejected() {
    assertFailure(
      INVALID_ARRIVAL_TIME,
      siri.applyEstimatedTimetable(middleCallWithAimedTimesOnly())
    );
  }

  /* A served arrival must state its aimed time as well - expected alone is not enough. */

  @LegacyUpdaterOnly(UNIFIED_REJECTS)
  @Test
  void middleCallWithoutAimedArrivalIsApplied() {
    var result = siri.applyEstimatedTimetable(middleCallWithoutAimedArrival());

    assertSuccess(result);
    assertEquals(
      "U | A 0:00:15 0:00:15 | B 0:00:25 0:00:26 | C 0:00:35 0:00:35",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }

  @UnifiedUpdaterOnly(LEGACY_TOLERATES)
  @Test
  void middleCallWithoutAimedArrivalIsRejected() {
    assertFailure(
      INVALID_ARRIVAL_TIME,
      siri.applyEstimatedTimetable(middleCallWithoutAimedArrival())
    );
  }

  /* The mirror case on the departure end: expected present, aimed missing. */

  @LegacyUpdaterOnly(UNIFIED_REJECTS)
  @Test
  void middleCallWithoutAimedDepartureIsApplied() {
    var result = siri.applyEstimatedTimetable(middleCallWithoutAimedDeparture());

    assertSuccess(result);
    assertEquals(
      "U | A 0:00:15 0:00:15 | B 0:00:25 0:00:26 | C 0:00:35 0:00:35",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }

  @UnifiedUpdaterOnly(LEGACY_TOLERATES)
  @Test
  void middleCallWithoutAimedDepartureIsRejected() {
    assertFailure(
      INVALID_DEPARTURE_TIME,
      siri.applyEstimatedTimetable(middleCallWithoutAimedDeparture())
    );
  }

  /* The first call must state a departure pair - only its arrival side is exempt. */

  @LegacyUpdaterOnly(UNIFIED_REJECTS)
  @Test
  void firstCallWithAimedDepartureOnlyIsFlaggedNoData() {
    var result = siri.applyEstimatedTimetable(firstCallWithAimedDepartureOnly());

    assertSuccess(result);
    assertEquals(
      "U | A [ND] 0:00:10 0:00:11 | B 0:00:25 0:00:26 | C 0:00:35 0:00:35",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }

  @UnifiedUpdaterOnly(LEGACY_TOLERATES)
  @Test
  void firstCallWithAimedDepartureOnlyIsRejected() {
    assertFailure(
      INVALID_DEPARTURE_TIME,
      siri.applyEstimatedTimetable(firstCallWithAimedDepartureOnly())
    );
  }

  /* The last call must state an arrival pair - only its departure side is exempt. */

  @LegacyUpdaterOnly(UNIFIED_REJECTS)
  @Test
  void lastCallWithAimedArrivalOnlyIsFlaggedNoData() {
    var result = siri.applyEstimatedTimetable(lastCallWithAimedArrivalOnly());

    assertSuccess(result);
    assertEquals(
      "U | A 0:00:15 0:00:15 | B 0:00:25 0:00:26 | C [ND] 0:00:30 0:00:31",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }

  @UnifiedUpdaterOnly(LEGACY_TOLERATES)
  @Test
  void lastCallWithAimedArrivalOnlyIsRejected() {
    assertFailure(
      INVALID_ARRIVAL_TIME,
      siri.applyEstimatedTimetable(lastCallWithAimedArrivalOnly())
    );
  }

  /* On a created trip the aimed times are the timetable. */

  @LegacyUpdaterOnly(UNIFIED_REJECTS)
  @Test
  void extraJourneyWithAimedOnlyMiddleCallIsBuiltFromAimedTimes() {
    var result = siri.applyEstimatedTimetable(extraJourneyWithAimedOnlyMiddleCall());

    assertSuccess(result);
    assertEquals(
      "A U | A 0:01 0:01 | B [ND] 0:02 0:03 | C 0:04 0:04",
      env.tripData(ADDED_TRIP_ID).showTimetable()
    );
  }

  @UnifiedUpdaterOnly(LEGACY_TOLERATES)
  @Test
  void extraJourneyWithAimedOnlyMiddleCallIsRejected() {
    assertFailure(
      INVALID_ARRIVAL_TIME,
      siri.applyEstimatedTimetable(extraJourneyWithAimedOnlyMiddleCall())
    );
  }

  /* Valid input for both implementations. */

  /** On a recorded call the actual time satisfies the real-time half of the requirement. */
  @Test
  void recordedCallWithActualTimesOnlyIsAccepted() {
    var updates = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withRecordedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedActual("00:00:11", "00:00:15")
          .call(STOP_B)
          .arriveAimedActual("00:00:20", "00:00:25")
          .departAimedActual("00:00:21", "00:00:26")
      )
      .withEstimatedCalls(builder ->
        builder.call(STOP_C).arriveAimedExpected("00:00:30", "00:00:35")
      )
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(updates));
    assertEquals(
      "U | A [R] 0:00:15 0:00:15 | B [R] 0:00:25 0:00:26 | C 0:00:35 0:00:35",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }

  /** The two exemptions: no arrival times on the first call, no departure times on the last. */
  @Test
  void firstCallArrivalAndLastCallDepartureAreExempt() {
    var updates = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:00:11", "00:00:15")
          .call(STOP_B)
          .arriveAimedExpected("00:00:20", "00:00:25")
          .departAimedExpected("00:00:21", "00:00:26")
          .call(STOP_C)
          .arriveAimedExpected("00:00:30", "00:00:35")
      )
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(updates));
    assertEquals(
      "U | A 0:00:15 0:00:15 | B 0:00:25 0:00:26 | C 0:00:35 0:00:35",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }

  /* Message fixtures, shared by each pair of tests */

  private List<EstimatedTimetableDeliveryStructure> middleCallWithAimedTimesOnly() {
    return siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:00:11", "00:00:15")
          .call(STOP_B)
          .arriveAimedExpected("00:00:20", null)
          .departAimedExpected("00:00:21", null)
          .call(STOP_C)
          .arriveAimedExpected("00:00:30", "00:00:35")
      )
      .buildEstimatedTimetableDeliveries();
  }

  private List<EstimatedTimetableDeliveryStructure> middleCallWithoutAimedArrival() {
    return siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:00:11", "00:00:15")
          .call(STOP_B)
          .arriveAimedExpected(null, "00:00:25")
          .departAimedExpected("00:00:21", "00:00:26")
          .call(STOP_C)
          .arriveAimedExpected("00:00:30", "00:00:35")
      )
      .buildEstimatedTimetableDeliveries();
  }

  private List<EstimatedTimetableDeliveryStructure> middleCallWithoutAimedDeparture() {
    return siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:00:11", "00:00:15")
          .call(STOP_B)
          .arriveAimedExpected("00:00:20", "00:00:25")
          .departAimedExpected(null, "00:00:26")
          .call(STOP_C)
          .arriveAimedExpected("00:00:30", "00:00:35")
      )
      .buildEstimatedTimetableDeliveries();
  }

  private List<EstimatedTimetableDeliveryStructure> firstCallWithAimedDepartureOnly() {
    return siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:00:11", null)
          .call(STOP_B)
          .arriveAimedExpected("00:00:20", "00:00:25")
          .departAimedExpected("00:00:21", "00:00:26")
          .call(STOP_C)
          .arriveAimedExpected("00:00:30", "00:00:35")
      )
      .buildEstimatedTimetableDeliveries();
  }

  private List<EstimatedTimetableDeliveryStructure> lastCallWithAimedArrivalOnly() {
    return siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:00:11", "00:00:15")
          .call(STOP_B)
          .arriveAimedExpected("00:00:20", "00:00:25")
          .departAimedExpected("00:00:21", "00:00:26")
          .call(STOP_C)
          .arriveAimedExpected("00:00:30", null)
      )
      .buildEstimatedTimetableDeliveries();
  }

  private List<EstimatedTimetableDeliveryStructure> extraJourneyWithAimedOnlyMiddleCall() {
    return siri
      .etBuilder()
      .withEstimatedVehicleJourneyCode(ADDED_TRIP_ID)
      .withIsExtraJourney(true)
      .withOperatorRef(OPERATOR_ID)
      .withLineRef(ROUTE_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:01", "00:01")
          .call(STOP_B)
          .arriveAimedExpected("00:02", null)
          .departAimedExpected("00:03", null)
          .call(STOP_C)
          .arriveAimedExpected("00:04", "00:04")
      )
      .buildEstimatedTimetableDeliveries();
  }
}
