package org.opentripplanner.updater.trip.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.trip.gtfs.BackwardsDelayPropagationType.ALWAYS;
import static org.opentripplanner.updater.trip.gtfs.BackwardsDelayPropagationType.NONE;
import static org.opentripplanner.updater.trip.gtfs.BackwardsDelayPropagationType.REQUIRED_NO_DATA;
import static org.opentripplanner.updater.trip.gtfs.ForwardsDelayPropagationType.DEFAULT;
import static org.opentripplanner.updater.trip.model.StopReplacementConstraint.ANY_STOP;
import static org.opentripplanner.updater.trip.model.StopReplacementConstraint.SAME_PARENT_STATION;

import org.junit.jupiter.api.Test;
import org.opentripplanner.updater.trip.gtfs.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.ForwardsDelayPropagationType;

class TripUpdateOptionsTest {

  @Test
  void siriDefaultsNoDelayPropagation() {
    var options = TripUpdateOptions.siriDefaults();

    assertEquals(ForwardsDelayPropagationType.NONE, options.forwardsPropagation());
    assertEquals(BackwardsDelayPropagationType.NONE, options.backwardsPropagation());
    assertTrue(options.allowStopPatternModification());
    assertFalse(options.propagatesDelays());
    assertEquals(SAME_PARENT_STATION, options.stopReplacementConstraint());
    assertEquals(StopUpdateStrategy.FULL_UPDATE, options.stopUpdateStrategy());
    assertEquals(StopCancellationTrackingStrategy.NO_TRACK, options.stopCancellationTracking());
  }

  @Test
  void gtfsRtDefaultsWithDelayPropagation() {
    var options = TripUpdateOptions.gtfsRtDefaults(DEFAULT, REQUIRED_NO_DATA);

    assertEquals(DEFAULT, options.forwardsPropagation());
    assertEquals(REQUIRED_NO_DATA, options.backwardsPropagation());
    assertTrue(options.allowStopPatternModification());
    assertTrue(options.propagatesDelays());
    assertEquals(ANY_STOP, options.stopReplacementConstraint());
    assertEquals(StopUpdateStrategy.PARTIAL_UPDATE, options.stopUpdateStrategy());
    assertEquals(
      StopCancellationTrackingStrategy.TRACK_AS_PICKUP_DROPOFF_CHANGE,
      options.stopCancellationTracking()
    );
  }

  @Test
  void gtfsRtWithNoDelayPropagation() {
    var options = TripUpdateOptions.gtfsRtDefaults(
      ForwardsDelayPropagationType.NONE,
      BackwardsDelayPropagationType.NONE
    );

    assertEquals(ForwardsDelayPropagationType.NONE, options.forwardsPropagation());
    assertEquals(BackwardsDelayPropagationType.NONE, options.backwardsPropagation());
    assertFalse(options.propagatesDelays());
  }

  @Test
  void builderCreatesCustomOptions() {
    var options = TripUpdateOptions.builder()
      .withForwardsPropagation(DEFAULT)
      .withBackwardsPropagation(ALWAYS)
      .withAllowStopPatternModification(false)
      .withStopReplacementConstraint(SAME_PARENT_STATION)
      .build();

    assertEquals(DEFAULT, options.forwardsPropagation());
    assertEquals(ALWAYS, options.backwardsPropagation());
    assertFalse(options.allowStopPatternModification());
    assertTrue(options.propagatesDelays());
    assertEquals(SAME_PARENT_STATION, options.stopReplacementConstraint());
  }

  @Test
  void propagatesDelaysWhenOnlyForwardsPropagation() {
    var options = TripUpdateOptions.builder()
      .withForwardsPropagation(DEFAULT)
      .withBackwardsPropagation(NONE)
      .build();

    assertTrue(options.propagatesDelays());
  }

  @Test
  void propagatesDelaysWhenOnlyBackwardsPropagation() {
    var options = TripUpdateOptions.builder()
      .withForwardsPropagation(ForwardsDelayPropagationType.NONE)
      .withBackwardsPropagation(ALWAYS)
      .build();

    assertTrue(options.propagatesDelays());
  }

  @Test
  void builderDefaultsToAnyStopConstraint() {
    var options = TripUpdateOptions.builder().build();

    assertEquals(ANY_STOP, options.stopReplacementConstraint());
  }

  @Test
  void builderDefaultsToPartialUpdateStrategy() {
    var options = TripUpdateOptions.builder().build();

    assertEquals(StopUpdateStrategy.PARTIAL_UPDATE, options.stopUpdateStrategy());
  }

  @Test
  void builderCanSetStopUpdateStrategy() {
    var options = TripUpdateOptions.builder()
      .withStopUpdateStrategy(StopUpdateStrategy.FULL_UPDATE)
      .build();

    assertEquals(StopUpdateStrategy.FULL_UPDATE, options.stopUpdateStrategy());
  }

  @Test
  void builderDefaultsToNoTrackCancellationStrategy() {
    var options = TripUpdateOptions.builder().build();

    assertEquals(StopCancellationTrackingStrategy.NO_TRACK, options.stopCancellationTracking());
  }

  @Test
  void builderCanSetCancellationTrackingStrategy() {
    var options = TripUpdateOptions.builder()
      .withStopCancellationTracking(StopCancellationTrackingStrategy.TRACK_AS_PICKUP_DROPOFF_CHANGE)
      .build();

    assertEquals(
      StopCancellationTrackingStrategy.TRACK_AS_PICKUP_DROPOFF_CHANGE,
      options.stopCancellationTracking()
    );
  }
}
