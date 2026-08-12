package org.opentripplanner.updater.trip.siri.moduletests.fuzzymatching;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.organization.Operator;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.trip.LegacyUpdaterOnly;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.UnifiedUpdaterOnly;
import org.opentripplanner.updater.trip.siri.SiriTestHelper;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.VehicleModesEnumeration;

/**
 * A producer whose journey ids name no trip cancels a journey by describing where it starts and
 * ends, which is what the fuzzy trip matcher identifies it by.
 */
class FuzzyCancellationTest implements RealtimeTestConstants {

  private static final String RAIL_ROUTE_ID = "RailRoute";
  private static final String OPERATOR_ID = "operator-id";
  private static final String UNKNOWN_JOURNEY_ID = "unknown-journey";

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of();
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stopAtStation(STOP_B_ID, STATION_OMEGA_ID);
  private final RegularStop STOP_D = ENV_BUILDER.stopAtStation(STOP_D_ID, STATION_OMEGA_ID);
  private final Operator OPERATOR = ENV_BUILDER.operator(OPERATOR_ID);
  private final Route RAIL_ROUTE = ENV_BUILDER.route(RAIL_ROUTE_ID, r ->
    r.withMode(TransitMode.RAIL).withOperator(OPERATOR)
  );

  private final TripInput TRIP_INPUT = TripInput.of(TRIP_1_ID)
    .withRoute(RAIL_ROUTE)
    .addStop(STOP_A, "0:00:10", "0:00:11")
    .addStop(STOP_B, "0:00:20", "0:00:21");

  /** A cancellation whose journey ref names no trip is matched by its calls and applied. */
  @Test
  void cancellationWithUnknownJourneyRefIsFuzzyMatched() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.ofFuzzyMatching(env);

    assertFalse(env.tripData(TRIP_1_ID).tripTimes().isCanceled());

    assertSuccess(siri.applyEstimatedTimetable(unknownJourneyCancellation(siri)));

    assertTrue(env.tripData(TRIP_1_ID).tripTimes().isCanceled());
  }

  /** A cancellation states only aimed times, so those alone must identify the journey. */
  @Test
  void cancellationWithAimedTimesOnlyIsFuzzyMatched() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.ofFuzzyMatching(env);

    var updates = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(UNKNOWN_JOURNEY_ID)
      .withCancellation(true)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("0:00:11", null)
          .call(STOP_B)
          .arriveAimedExpected("0:00:20", null)
      )
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(updates));

    assertTrue(env.tripData(TRIP_1_ID).tripTimes().isCanceled());
  }

  /**
   * Two rail trips run the same stops at the same times, so only the internal planning code the
   * VehicleRef names tells the cancelled one from the other.
   */
  @Test
  void railCancellationIsMatchedByVehicleRef() {
    var railTrip1 = TripInput.of("RailTrip1")
      .withRoute(RAIL_ROUTE)
      .withNetexInternalPlanningCode("47")
      .addStop(STOP_A, "0:00:10", "0:00:11")
      .addStop(STOP_B, "0:00:20", "0:00:21");
    var railTrip2 = TripInput.of("RailTrip2")
      .withRoute(RAIL_ROUTE)
      .withNetexInternalPlanningCode("48")
      .addStop(STOP_A, "0:00:10", "0:00:11")
      .addStop(STOP_B, "0:00:20", "0:00:21");

    var env = ENV_BUILDER.addTrip(railTrip1).addTrip(railTrip2).build();
    var siri = SiriTestHelper.ofFuzzyMatching(env);

    var updates = siri
      .etBuilder()
      .withFramedVehicleJourneyRef(builder ->
        builder.withServiceDate(env.defaultServiceDate()).withVehicleJourneyRef(UNKNOWN_JOURNEY_ID)
      )
      .withVehicleRef("47")
      .withVehicleMode(VehicleModesEnumeration.RAIL)
      .withCancellation(true)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("0:00:11", "0:00:11")
          .call(STOP_B)
          .arriveAimedExpected("0:00:20", "0:00:20")
      )
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(updates));

    assertTrue(env.tripData("RailTrip1").tripTimes().isCanceled());
    assertFalse(env.tripData("RailTrip2").tripTimes().isCanceled());
  }

  /** A cancellation of a journey no scheduled trip runs is rejected as unmatched. */
  @Test
  void cancellationOfAnUnknownJourneyIsRejected() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.ofFuzzyMatching(env);

    var updates = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(UNKNOWN_JOURNEY_ID)
      .withCancellation(true)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("8:00:00", "8:00:00")
          .call(STOP_B)
          .arriveAimedExpected("9:00:00", "9:00:00")
      )
      .buildEstimatedTimetableDeliveries();

    var result = siri.applyEstimatedTimetable(updates);

    assertEquals(0, result.successful());
    assertFailure(UpdateErrorType.NO_FUZZY_TRIP_MATCH, result);
    assertFalse(env.tripData(TRIP_1_ID).tripTimes().isCanceled());
  }

  /**
   * A cancellation whose first call states no aimed departure cannot identify its journey, even
   * when the message states its service date outright.
   */
  @Test
  void cancellationWithoutAnAimedDepartureAtTheFirstCallIsRejected() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.ofFuzzyMatching(env);

    var updates = siri
      .etBuilder()
      .withFramedVehicleJourneyRef(builder ->
        builder.withServiceDate(env.defaultServiceDate()).withVehicleJourneyRef(UNKNOWN_JOURNEY_ID)
      )
      .withCancellation(true)
      .withRecordedCalls(builder -> builder.call(STOP_A).arriveAimedActual("0:00:10", "0:00:10"))
      .withEstimatedCalls(builder -> builder.call(STOP_B).arriveAimedExpected("0:00:20", "0:00:20"))
      .buildEstimatedTimetableDeliveries();

    assertFailure(UpdateErrorType.INVALID_DEPARTURE_TIME, siri.applyEstimatedTimetable(updates));
    assertFalse(env.tripData(TRIP_1_ID).tripTimes().isCanceled());
  }

  /**
   * Two scheduled journeys share the endpoints the cancellation states, so it names neither of
   * them.
   */
  @Test
  void cancellationMatchingTwoJourneysIsRejected() {
    var twin = TripInput.of(TRIP_2_ID)
      .withRoute(RAIL_ROUTE)
      .addStop(STOP_A, "0:00:10", "0:00:11")
      .addStop(STOP_B, "0:00:20", "0:00:21");
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).addTrip(twin).build();
    var siri = SiriTestHelper.ofFuzzyMatching(env);

    var result = siri.applyEstimatedTimetable(unknownJourneyCancellation(siri));

    assertFailure(UpdateErrorType.MULTIPLE_FUZZY_TRIP_MATCHES, result);
    assertFalse(env.tripData(TRIP_1_ID).tripTimes().isCanceled());
    assertFalse(env.tripData(TRIP_2_ID).tripTimes().isCanceled());
  }

  /** With fuzzy matching switched off, an unresolvable cancellation names no trip at all. */
  @Test
  @UnifiedUpdaterOnly("Legacy reports the missing matcher as TRIP_NOT_FOUND.")
  void cancellationIsRejectedAsNoTripForCancellationWithoutFuzzyMatching() {
    assertFailure(
      UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND,
      applyCancellationWithoutFuzzyMatching()
    );
  }

  /** Legacy reports the same cancellation as a trip it could not find. */
  @Test
  @LegacyUpdaterOnly(
    "The unified updater reports the failed removal lookup rather than the missing matcher."
  )
  void cancellationIsRejectedAsTripNotFoundWithoutFuzzyMatching() {
    assertFailure(UpdateErrorType.TRIP_NOT_FOUND, applyCancellationWithoutFuzzyMatching());
  }

  /**
   * A cancellation whose last call names a quay the model does not know: both adapters reject, with
   * different codes.
   */
  @Test
  @UnifiedUpdaterOnly(
    "Legacy resolves the last-call quay before reading its times and reports UNKNOWN_STOP."
  )
  void cancellationWithAnUnknownLastQuayIsRejectedAsNoValidStops() {
    assertFailure(UpdateErrorType.NO_VALID_STOPS, applyCancellationWithUnknownLastQuay());
  }

  /** Legacy names the unknown quay of the last call as the reason. */
  @Test
  @LegacyUpdaterOnly(
    "The unified matcher reads the call times before resolving the stops, and reports " +
      "NO_VALID_STOPS for either end."
  )
  void cancellationWithAnUnknownLastQuayIsRejectedAsUnknownStop() {
    assertFailure(UpdateErrorType.UNKNOWN_STOP, applyCancellationWithUnknownLastQuay());
  }

  /**
   * The cancellation of a journey an earlier message added is applied to that journey, not to the
   * scheduled journey it repeats the calls of. The added journey resolves by its id, so the matcher
   * is never asked - which is what keeps the two apart.
   */
  @Test
  void cancellationOfAnAddedJourneyPrefersTheAddedTrip() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.ofFuzzyMatching(env);

    var addition = siri
      .etBuilder()
      .withEstimatedVehicleJourneyCode(ADDED_TRIP_ID)
      .withIsExtraJourney(true)
      .withOperatorRef(OPERATOR_ID)
      .withLineRef(RAIL_ROUTE_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .arriveAimedExpected("0:00:10", "0:00:10")
          .departAimedExpected("0:00:11", "0:00:11")
          .call(STOP_B)
          .arriveAimedExpected("0:00:20", "0:00:20")
          .departAimedExpected("0:00:21", "0:00:21")
      )
      .buildEstimatedTimetableDeliveries();
    assertSuccess(siri.applyEstimatedTimetable(addition));

    var cancellation = siri
      .etBuilder()
      .withEstimatedVehicleJourneyCode(ADDED_TRIP_ID)
      .withCancellation(true)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("0:00:11", "0:00:11")
          .call(STOP_B)
          .arriveAimedExpected("0:00:20", "0:00:20")
      )
      .buildEstimatedTimetableDeliveries();
    assertSuccess(siri.applyEstimatedTimetable(cancellation));

    assertTrue(env.tripData(ADDED_TRIP_ID).tripTimes().isCanceled());
    assertFalse(env.tripData(TRIP_1_ID).tripTimes().isCanceled());
  }

  /**
   * A cancellation listing no call states no time either, so an unresolvable journey ref leaves it
   * with no day to be placed on.
   */
  @Test
  void cancellationWithoutCallsOrServiceDateIsRejected() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.ofFuzzyMatching(env);

    var updates = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(UNKNOWN_JOURNEY_ID)
      .withCancellation(true)
      .buildEstimatedTimetableDeliveries();

    assertFailure(UpdateErrorType.NO_START_DATE, siri.applyEstimatedTimetable(updates));
  }

  /**
   * A cancellation that names its service date but lists no call describes no journey, so the
   * matcher declines it and the unresolved journey ref is the last word.
   */
  @Test
  @UnifiedUpdaterOnly("Legacy asks the matcher, which reports the missing calls as NO_VALID_STOPS.")
  void cancellationWithoutCallsIsRejectedAsNoTripForCancellation() {
    assertFailure(UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND, applyCallLessCancellation());
  }

  /** Legacy reports the same call-less cancellation as a journey without usable stops. */
  @Test
  @LegacyUpdaterOnly(
    "The unified updater declines to match a cancellation that describes no journey, and reports " +
      "the unresolved journey ref instead."
  )
  void cancellationWithoutCallsIsRejectedAsNoValidStops() {
    assertFailure(UpdateErrorType.NO_VALID_STOPS, applyCallLessCancellation());
  }

  /**
   * A journey moved to another quay and then cancelled: the cancellation is published on the
   * scheduled pattern, reverting the quay change.
   */
  @Test
  @UnifiedUpdaterOnly(
    "Legacy cancels on the real-time modified pattern the fuzzy matcher returns, keeping the " +
      "changed quay."
  )
  void quayChangedThenFuzzyCancelledRevertsToTheScheduledPattern() {
    var env = changeQuayThenFuzzyCancel();

    assertEquals(
      "C U | A 0:00:10 0:00:11 | B 0:00:20 0:00:21",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }

  /** Legacy keeps the changed quay when the cancellation is fuzzy-matched. */
  @Test
  @LegacyUpdaterOnly(
    "The unified updater reverts a cancelled trip to its scheduled pattern, as it does for an " +
      "id-matched cancellation."
  )
  void quayChangedThenFuzzyCancelledKeepsTheChangedQuay() {
    var env = changeQuayThenFuzzyCancel();

    assertEquals(
      "C U | A 0:00:10 0:00:11 | D 0:00:20 0:00:21",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }

  private TransitTestEnvironment changeQuayThenFuzzyCancel() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.ofFuzzyMatching(env);

    var quayChange = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("0:00:11", "0:00:15")
          // another quay of the same station
          .call(STOP_D)
          .arriveAimedExpected("0:00:20", "0:00:25")
      )
      .buildEstimatedTimetableDeliveries();
    assertSuccess(siri.applyEstimatedTimetable(quayChange));

    assertSuccess(siri.applyEstimatedTimetable(unknownJourneyCancellation(siri)));

    assertThat(env.tripData(TRIP_1_ID).tripTimes().isCanceled()).isTrue();
    return env;
  }

  private UpdateResult applyCancellationWithoutFuzzyMatching() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.of(env);
    var result = siri.applyEstimatedTimetable(unknownJourneyCancellation(siri));
    assertEquals(0, result.successful());
    assertFalse(env.tripData(TRIP_1_ID).tripTimes().isCanceled());
    return result;
  }

  private UpdateResult applyCancellationWithUnknownLastQuay() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.ofFuzzyMatching(env);
    var updates = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(UNKNOWN_JOURNEY_ID)
      .withCancellation(true)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("0:00:11", "0:00:11")
          .call("no-such-quay")
          .arriveAimedExpected("0:00:20", "0:00:20")
      )
      .buildEstimatedTimetableDeliveries();
    var result = siri.applyEstimatedTimetable(updates);
    assertEquals(0, result.successful());
    assertFalse(env.tripData(TRIP_1_ID).tripTimes().isCanceled());
    return result;
  }

  private UpdateResult applyCallLessCancellation() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var siri = SiriTestHelper.ofFuzzyMatching(env);
    var updates = siri
      .etBuilder()
      .withFramedVehicleJourneyRef(builder ->
        builder.withServiceDate(env.defaultServiceDate()).withVehicleJourneyRef(UNKNOWN_JOURNEY_ID)
      )
      .withCancellation(true)
      .buildEstimatedTimetableDeliveries();
    var result = siri.applyEstimatedTimetable(updates);
    assertEquals(0, result.successful());
    assertFalse(env.tripData(TRIP_1_ID).tripTimes().isCanceled());
    return result;
  }

  private List<EstimatedTimetableDeliveryStructure> unknownJourneyCancellation(
    SiriTestHelper siri
  ) {
    return siri
      .etBuilder()
      .withDatedVehicleJourneyRef(UNKNOWN_JOURNEY_ID)
      .withCancellation(true)
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("0:00:11", "0:00:11")
          .call(STOP_B)
          .arriveAimedExpected("0:00:20", "0:00:20")
      )
      .buildEstimatedTimetableDeliveries();
  }
}
