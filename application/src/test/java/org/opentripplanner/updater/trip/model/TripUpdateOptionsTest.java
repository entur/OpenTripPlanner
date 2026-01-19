package org.opentripplanner.updater.trip.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.trip.gtfs.BackwardsDelayPropagationType.ALWAYS;
import static org.opentripplanner.updater.trip.gtfs.BackwardsDelayPropagationType.NONE;
import static org.opentripplanner.updater.trip.gtfs.BackwardsDelayPropagationType.REQUIRED_NO_DATA;
import static org.opentripplanner.updater.trip.gtfs.ForwardsDelayPropagationType.DEFAULT;

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
  }

  @Test
  void gtfsRtDefaultsWithDelayPropagation() {
    var options = TripUpdateOptions.gtfsRtDefaults(DEFAULT, REQUIRED_NO_DATA);

    assertEquals(DEFAULT, options.forwardsPropagation());
    assertEquals(REQUIRED_NO_DATA, options.backwardsPropagation());
    assertTrue(options.allowStopPatternModification());
    assertTrue(options.propagatesDelays());
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
      .build();

    assertEquals(DEFAULT, options.forwardsPropagation());
    assertEquals(ALWAYS, options.backwardsPropagation());
    assertFalse(options.allowStopPatternModification());
    assertTrue(options.propagatesDelays());
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
}
