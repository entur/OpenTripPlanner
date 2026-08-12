package org.opentripplanner.updater.trip.siri.moduletests.extrajourney;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_ARRIVAL_TIME;
import static org.opentripplanner.updater.spi.UpdateErrorType.STOP_MISMATCH;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.organization.Operator;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.LegacyUpdaterOnly;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.UnifiedUpdaterOnly;
import org.opentripplanner.updater.trip.siri.SiriEtBuilder;
import org.opentripplanner.updater.trip.siri.SiriTestHelper;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;

/**
 * A second message for an extra journey that has already been added revises the trip on the
 * pattern it was added to, with the semantics of an ordinary trip update: a call can replace its
 * stop with one in the same station, restrict or cancel boarding and alighting, and cancel the
 * call - and a call that does not describe the added pattern at its position is rejected. The
 * legacy path is {@code ModifiedTripBuilder}, the unified path is {@code AddedTripRevision}.
 * <p>
 * See {@link ExtraJourneyStopCountTest} for the stop-count preconditions of the same path.
 */
class ExtraJourneyRevisionTest implements RealtimeTestConstants {

  private static final String OPERATOR_ID = "operatorId";
  private static final String ROUTE_ID = "routeId";

  /** The timetable of the added journey, left untouched by a rejected revision. */
  private static final String ADDED_JOURNEY_TIMETABLE = "A U | A 0:02 0:02 | B 0:04 0:04";

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop STOP_A = envBuilder.stop(STOP_A_ID);
  private final RegularStop STOP_B = envBuilder.stopAtStation(STOP_B_ID, STATION_OMEGA_ID);
  private final RegularStop STOP_C = envBuilder.stop(STOP_C_ID);
  private final RegularStop STOP_D = envBuilder.stopAtStation(STOP_D_ID, STATION_OMEGA_ID);

  private final Operator OPERATOR = envBuilder.operator(OPERATOR_ID);
  private final Route ROUTE = envBuilder.route(ROUTE_ID, OPERATOR);

  /** Only there to register the route the extra journey runs on. */
  private final TripInput TRIP_1_INPUT = TripInput.of(TRIP_1_ID)
    .withRoute(ROUTE)
    .addStop(STOP_A, "0:00:10", "0:00:11")
    .addStop(STOP_B, "0:00:20", "0:00:21");

  private final TransitTestEnvironment env = envBuilder.addTrip(TRIP_1_INPUT).build();
  private final SiriTestHelper siri = SiriTestHelper.of(env);

  /**
   * A revision whose calls are at known stops the trip does not call at cannot be matched to the
   * pattern the trip was added to.
   */
  @Test
  void revisionCallingAtAStopTheTripDoesNotServeIsRejected() {
    addTheExtraJourney();

    var revision = extraJourney()
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:01", "00:06")
          .call(STOP_C)
          .arriveAimedExpected("00:03", "00:08")
      )
      .buildEstimatedTimetableDeliveries();

    assertFailure(STOP_MISMATCH, siri.applyEstimatedTimetable(revision));
    assertEquals(ADDED_JOURNEY_TIMETABLE, env.tripData(ADDED_TRIP_ID).showTimetable());
  }

  /**
   * A call at a quay in the same station as the one the trip was added with replaces the stop, the
   * way it does on a scheduled trip: the trip moves to a modified pattern serving the new quay.
   */
  @Test
  void revisionMovingACallToASiblingQuayChangesThePattern() {
    addTheExtraJourney();

    assertSuccess(siri.applyEstimatedTimetable(revisionCallingAt(STOP_A, STOP_D)));

    assertEquals("A P U | A 0:06 0:06 | D 0:08 0:08", env.tripData(ADDED_TRIP_ID).showTimetable());
  }

  /**
   * After a quay change, a revision naming the original quay again moves the trip back onto the
   * pattern it was added to.
   */
  @Test
  void revisionBackToTheOriginalQuayRevertsToTheAddedPattern() {
    addTheExtraJourney();
    assertSuccess(siri.applyEstimatedTimetable(revisionCallingAt(STOP_A, STOP_D)));

    assertSuccess(siri.applyEstimatedTimetable(revisionCallingAt(STOP_A, STOP_B)));

    assertEquals("A U | A 0:06 0:06 | B 0:08 0:08", env.tripData(ADDED_TRIP_ID).showTimetable());
    assertThat(env.raptorData().summarizePatterns()).containsExactly(
      "F:Pattern1[S]",
      "F:%s::001:RT[A U]".formatted(ROUTE_ID)
    );
  }

  /**
   * A revision cancelling every call cancels the whole trip, publishing the aimed times it was
   * added with - the same implicit cancellation an ordinary trip update applies. The per-call
   * real-time data of a trip that does not run carries no meaning, so no per-stop cancellation
   * flags remain (contrast with {@code testAddJourneyWithAllStopsCancelledIsImplicitlyCancelled},
   * where the message that cancels every call is the one <em>creating</em> the trip).
   */
  @Test
  void revisionCancellingEveryCallCancelsTheAddedTrip() {
    addTheExtraJourney();

    var revision = extraJourney()
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:01", "00:06")
          .withIsCancellation(true)
          .call(STOP_B)
          .arriveAimedExpected("00:03", "00:08")
          .withIsCancellation(true)
      )
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(revision));
    assertEquals("A C U | A 0:01 0:01 | B 0:03 0:03", env.tripData(ADDED_TRIP_ID).showTimetable());
  }

  /**
   * A revision cancelling one call moves the trip to a modified pattern where that stop can
   * neither be boarded nor alighted, so routing no longer uses it - the pattern is what routing
   * reads, a cancellation flag on the trip times alone is not.
   */
  @Test
  void revisionCancellingOneCallMakesThatStopUnboardable() {
    addTheThreeStopExtraJourney();

    var revision = extraJourney()
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:01", "00:06")
          .call(STOP_B)
          .arriveAimedExpected("00:03", "00:08")
          .departAimedExpected("00:03:30", "00:08:30")
          .withIsCancellation(true)
          .call(STOP_C)
          .arriveAimedExpected("00:05", "00:10")
      )
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(revision));

    assertEquals(
      "A P U | A 0:06 0:06 | B [C] 0:08 0:08:30 | C 0:10 0:10",
      env.tripData(ADDED_TRIP_ID).showTimetable()
    );
    var pattern = env.tripData(ADDED_TRIP_ID).tripPattern();
    assertEquals(PickDrop.CANCELLED, pattern.getBoardType(1));
    assertEquals(PickDrop.CANCELLED, pattern.getAlightType(1));
  }

  /**
   * An extra call on an added trip moves it to a longer pattern; a later revision describing the
   * trip as it was added reverts it. The revision is measured against the pattern the trip was
   * added to, not the pattern the extra call created - measured against the latter, the revert
   * could only ever be rejected for calling at too few stops.
   */
  @Test
  void revisionRevertsAnExtraCallOnAnAddedTrip() {
    addTheExtraJourney();

    var extraCall = extraJourney()
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:01", "00:06")
          .call(STOP_C)
          .withIsExtraCall(true)
          .arriveAimedExpected("00:02", "00:07")
          .departAimedExpected("00:02:30", "00:07:30")
          .call(STOP_B)
          .arriveAimedExpected("00:03", "00:08")
      )
      .buildEstimatedTimetableDeliveries();
    assertSuccess(siri.applyEstimatedTimetable(extraCall));
    assertEquals(
      "A P U | A 0:06 0:06 | C [EC] 0:07 0:07:30 | B 0:08 0:08",
      env.tripData(ADDED_TRIP_ID).showTimetable()
    );

    var revert = extraJourney()
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:01", "00:06")
          .call(STOP_B)
          .arriveAimedExpected("00:03", "00:08")
      )
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(revert));
    assertEquals("A U | A 0:06 0:06 | B 0:08 0:08", env.tripData(ADDED_TRIP_ID).showTimetable());
  }

  /**
   * A call carrying only aimed times reports nothing about how the trip actually runs there, so
   * the stop is flagged NO_DATA and keeps the times the trip was added with.
   */
  @Test
  @LegacyUpdaterOnly(
    "The unified implementation rejects incomplete call times at parse - see the companion test."
  )
  void revisionWithACallCarryingNoRealTimeTimesFlagsItNoData() {
    addTheExtraJourney();

    var result = siri.applyEstimatedTimetable(revisionWithAimedOnlyLastCall());

    assertSuccess(result);
    assertEquals(
      "A U | A 0:02 0:02 | B [ND] 0:03 0:03",
      env.tripData(ADDED_TRIP_ID).showTimetable()
    );
  }

  /** The profile requires an expected arrival on the last call - the rejected revision changes nothing. */
  @UnifiedUpdaterOnly(
    "The legacy implementation tolerates incomplete call times - see the companion test."
  )
  @Test
  void revisionWithACallCarryingNoRealTimeTimesIsRejected() {
    addTheExtraJourney();

    var result = siri.applyEstimatedTimetable(revisionWithAimedOnlyLastCall());

    assertFailure(INVALID_ARRIVAL_TIME, result);
    assertEquals(ADDED_JOURNEY_TIMETABLE, env.tripData(ADDED_TRIP_ID).showTimetable());
  }

  private List<EstimatedTimetableDeliveryStructure> revisionWithAimedOnlyLastCall() {
    return extraJourney()
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:01", "00:02")
          .call(STOP_B)
          .arriveAimedExpected("00:03", null)
      )
      .buildEstimatedTimetableDeliveries();
  }

  /**
   * The unified implementation matches the calls of a revision to the added pattern by position,
   * so a message whose calls are reordered does not describe the pattern and is rejected - the
   * same rule it applies to an ordinary trip update.
   */
  @UnifiedUpdaterOnly(
    "The legacy ModifiedTripBuilder matches calls to pattern stops by stop identity rather than " +
      "by position, so it accepts a reordered message and applies each call to the stop it names."
  )
  @Test
  void revisionWithReorderedCallsIsRejected() {
    addTheExtraJourney();

    var revision = extraJourney()
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_B)
          .departAimedExpected("00:01", "00:06")
          .call(STOP_A)
          .arriveAimedExpected("00:03", "00:08")
      )
      .buildEstimatedTimetableDeliveries();

    assertFailure(STOP_MISMATCH, siri.applyEstimatedTimetable(revision));
    assertEquals(ADDED_JOURNEY_TIMETABLE, env.tripData(ADDED_TRIP_ID).showTimetable());
  }

  private void addTheExtraJourney() {
    var added = extraJourney()
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:01", "00:02")
          .call(STOP_B)
          .arriveAimedExpected("00:03", "00:04")
      )
      .buildEstimatedTimetableDeliveries();
    assertSuccess(siri.applyEstimatedTimetable(added));
    assertEquals(ADDED_JOURNEY_TIMETABLE, env.tripData(ADDED_TRIP_ID).showTimetable());
  }

  private void addTheThreeStopExtraJourney() {
    var added = extraJourney()
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:01", "00:02")
          .call(STOP_B)
          .arriveAimedExpected("00:03", "00:04")
          .departAimedExpected("00:03:30", "00:04:30")
          .call(STOP_C)
          .arriveAimedExpected("00:05", "00:06")
      )
      .buildEstimatedTimetableDeliveries();
    assertSuccess(siri.applyEstimatedTimetable(added));
  }

  /** A revision whose two calls are at the given stops, with the usual expected times. */
  private List<EstimatedTimetableDeliveryStructure> revisionCallingAt(
    RegularStop first,
    RegularStop second
  ) {
    return extraJourney()
      .withEstimatedCalls(builder ->
        builder
          .call(first)
          .departAimedExpected("00:01", "00:06")
          .call(second)
          .arriveAimedExpected("00:03", "00:08")
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
