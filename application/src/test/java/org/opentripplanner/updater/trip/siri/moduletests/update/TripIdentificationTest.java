package org.opentripplanner.updater.trip.siri.moduletests.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.siri.SiriEtBuilder;
import org.opentripplanner.updater.trip.siri.SiriTestHelper;

/**
 * A journey may be named by its FramedVehicleJourneyRef, its DatedVehicleJourneyRef or its
 * EstimatedVehicleJourneyCode. Each is tried in turn, in that order.
 */
class TripIdentificationTest implements RealtimeTestConstants {

  private static final String DATED_TRIP_1_ID = "TestDatedTrip1";
  private static final String DATED_TRIP_2_ID = "TestDatedTrip2";
  private static final String UNKNOWN_ID = "NoSuchJourney";

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of();
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stop(STOP_B_ID);

  private final TripInput TRIP_1_INPUT = TripInput.of(TRIP_1_ID)
    .withWithTripOnServiceDate(DATED_TRIP_1_ID)
    .addStop(STOP_A, "0:00:10", "0:00:11")
    .addStop(STOP_B, "0:00:20", "0:00:21");

  private final TripInput TRIP_2_INPUT = TripInput.of(TRIP_2_ID)
    .withWithTripOnServiceDate(DATED_TRIP_2_ID)
    .addStop(STOP_A, "0:01:10", "0:01:11")
    .addStop(STOP_B, "0:01:20", "0:01:21");

  /** A framed ref naming no known journey leaves the DatedVehicleJourneyRef to name it. */
  @Test
  void unknownFramedRefFallsBackToDatedVehicleJourneyRef() {
    var env = ENV_BUILDER.addTrip(TRIP_1_INPUT).build();
    var siri = SiriTestHelper.of(env);

    var updates = updatedJourney(siri)
      .withFramedVehicleJourneyRef(builder ->
        builder.withServiceDate(env.defaultServiceDate()).withVehicleJourneyRef(UNKNOWN_ID)
      )
      .withDatedVehicleJourneyRef(DATED_TRIP_1_ID)
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(updates));
    assertTrip1Updated(env);
  }

  /** A framed ref naming no known journey leaves the journey code to name it. */
  @Test
  void unknownFramedRefFallsBackToEstimatedVehicleJourneyCode() {
    var env = ENV_BUILDER.addTrip(TRIP_1_INPUT).build();
    var siri = SiriTestHelper.of(env);

    var updates = updatedJourney(siri)
      .withFramedVehicleJourneyRef(builder ->
        builder.withServiceDate(env.defaultServiceDate()).withVehicleJourneyRef(UNKNOWN_ID)
      )
      .withEstimatedVehicleJourneyCode(TRIP_1_ID)
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(updates));
    assertTrip1Updated(env);
  }

  /** The DatedVehicleJourneyRef names the journey ahead of the journey code. */
  @Test
  void datedVehicleJourneyRefIsPreferredOverEstimatedVehicleJourneyCode() {
    var env = ENV_BUILDER.addTrip(TRIP_1_INPUT).addTrip(TRIP_2_INPUT).build();
    var siri = SiriTestHelper.of(env);

    var updates = updatedJourney(siri)
      .withDatedVehicleJourneyRef(DATED_TRIP_1_ID)
      .withEstimatedVehicleJourneyCode(TRIP_2_ID)
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(updates));
    assertTrip1Updated(env);
    assertFalse(env.tripData(TRIP_2_ID).tripTimes().hasAnyUpdates());
  }

  /** A DatedVehicleJourneyRef naming no known journey leaves the journey code to name it. */
  @Test
  void unknownDatedVehicleJourneyRefFallsBackToEstimatedVehicleJourneyCode() {
    var env = ENV_BUILDER.addTrip(TRIP_1_INPUT).build();
    var siri = SiriTestHelper.of(env);

    var updates = updatedJourney(siri)
      .withDatedVehicleJourneyRef(UNKNOWN_ID)
      .withEstimatedVehicleJourneyCode(TRIP_1_ID)
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(updates));
    assertTrip1Updated(env);
  }

  /** A journey none of the three references names is rejected. */
  @Test
  void journeyNamedByNoKnownReferenceIsRejected() {
    var env = ENV_BUILDER.addTrip(TRIP_1_INPUT).build();
    var siri = SiriTestHelper.of(env);

    var updates = updatedJourney(siri)
      .withFramedVehicleJourneyRef(builder ->
        builder.withServiceDate(env.defaultServiceDate()).withVehicleJourneyRef(UNKNOWN_ID)
      )
      .withDatedVehicleJourneyRef(UNKNOWN_ID)
      .withEstimatedVehicleJourneyCode(UNKNOWN_ID)
      .buildEstimatedTimetableDeliveries();

    assertFailure(UpdateErrorType.TRIP_NOT_FOUND, siri.applyEstimatedTimetable(updates));
    assertFalse(env.tripData(TRIP_1_ID).tripTimes().hasAnyUpdates());
  }

  private SiriEtBuilder updatedJourney(SiriTestHelper siri) {
    return siri
      .etBuilder()
      .withEstimatedCalls(builder ->
        builder
          .call(STOP_A)
          .departAimedExpected("00:00:11", "00:00:15")
          .call(STOP_B)
          .arriveAimedExpected("00:00:20", "00:00:25")
      );
  }

  private void assertTrip1Updated(TransitTestEnvironment env) {
    assertEquals(
      "U | A 0:00:15 0:00:15 | B 0:00:25 0:00:25",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }
}
