package org.opentripplanner.updater.trip.gtfs.moduletests.propagation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;

/**
 * Switching a direction of delay propagation off is how an operator tells OTP that it would rather
 * have no real-time data for a trip than data OTP made up: both propagation types document
 * rejecting an update that does not state the times the interpolator would otherwise have filled
 * in. Publishing the scheduled time instead is the one thing the configuration rules out.
 */
class IncompleteUpdateTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop stopA = envBuilder.stop(STOP_A_ID);
  private final RegularStop stopB = envBuilder.stop(STOP_B_ID);
  private final RegularStop stopC = envBuilder.stop(STOP_C_ID);
  private final RegularStop stopD = envBuilder.stop(STOP_D_ID);

  private final TripInput tripInput = TripInput.of(TRIP_1_ID)
    .addStop(stopA, "10:00", "10:01")
    .addStop(stopB, "10:10", "10:11")
    .addStop(stopC, "10:20", "10:21")
    .addStop(stopD, "10:30", "10:31");

  /**
   * With neither direction propagating, an update that times one stop leaves the other three with
   * nothing to publish - in both directions - so the whole trip update is rejected.
   */
  @Test
  void noPropagationRejectsAnUpdateThatTimesOnlySomeStops() {
    var env = envBuilder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(
      env,
      ForwardsDelayPropagationType.NONE,
      BackwardsDelayPropagationType.NONE
    );

    var tripUpdate = rt.tripUpdateScheduled(TRIP_1_ID).addDelayedStopTime(1, 300).build();

    assertFailure(UpdateErrorType.INVALID_ARRIVAL_TIME, rt.applyTripUpdate(tripUpdate));
  }

  /**
   * Filling in the stops after the last one the update times is forwards propagation's job, so
   * switching it off rejects an update that stops short of the end. Leaving backwards propagation
   * on does not save it: that one only ever reaches backwards, and here there is nothing before the
   * first timed stop for it to reach.
   */
  @Test
  void noForwardPropagationRejectsAnUpdateThatStopsShortOfTheEnd() {
    var env = envBuilder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(
      env,
      ForwardsDelayPropagationType.NONE,
      BackwardsDelayPropagationType.REQUIRED_NO_DATA
    );

    var tripUpdate = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addDelayedStopTime(0, 60)
      .addDelayedStopTime(1, 60)
      .build();

    assertFailure(UpdateErrorType.INVALID_ARRIVAL_TIME, rt.applyTripUpdate(tripUpdate));
  }

  /**
   * The mirror image: filling in the stops before the first one the update times is backwards
   * propagation's job, so switching it off rejects an update that starts mid-trip. Forwards
   * propagation, still on, only ever reaches forwards.
   */
  @Test
  void noBackwardPropagationRejectsAnUpdateThatStartsMidTrip() {
    var env = envBuilder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(
      env,
      ForwardsDelayPropagationType.DEFAULT,
      BackwardsDelayPropagationType.NONE
    );

    var tripUpdate = rt.tripUpdateScheduled(TRIP_1_ID).addDelayedStopTime(3, 300).build();

    assertFailure(UpdateErrorType.INVALID_ARRIVAL_TIME, rt.applyTripUpdate(tripUpdate));
  }

  /**
   * A NO_DATA stop carries no times of its own, so it is a gap like any other - which is why the
   * documentation of {@code ForwardsDelayPropagationType.NONE} warns that it rejects every update
   * containing one.
   */
  @Test
  void noForwardPropagationRejectsANoDataStop() {
    var env = envBuilder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(
      env,
      ForwardsDelayPropagationType.NONE,
      BackwardsDelayPropagationType.REQUIRED_NO_DATA
    );

    var tripUpdate = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addDelayedStopTime(0, 60)
      .addDelayedStopTime(1, 60)
      .addDelayedStopTime(2, 60)
      .addNoDataStop(3)
      .build();

    assertFailure(UpdateErrorType.INVALID_ARRIVAL_TIME, rt.applyTripUpdate(tripUpdate));
  }

  /**
   * The other half of the contract: an update that times every stop needs no propagation, so
   * switching propagation off must not stand in its way.
   */
  @Test
  void noPropagationAcceptsAnUpdateThatTimesEveryStop() {
    var env = envBuilder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(
      env,
      ForwardsDelayPropagationType.NONE,
      BackwardsDelayPropagationType.NONE
    );

    var tripUpdate = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addDelayedStopTime(0, 60)
      .addDelayedStopTime(1, 120)
      .addDelayedStopTime(2, 180)
      .addDelayedStopTime(3, 240)
      .build();

    assertSuccess(rt.applyTripUpdate(tripUpdate));
    assertEquals(
      "U | A 10:01 10:02 | B 10:12 10:13 | C 10:23 10:24 | D 10:34 10:35",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }
}
