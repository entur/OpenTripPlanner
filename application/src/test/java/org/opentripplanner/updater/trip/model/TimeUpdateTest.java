package org.opentripplanner.updater.trip.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TimeUpdateTest {

  // 1 hour = 3600 seconds after midnight
  private static final int SCHEDULED_TIME = 3600;
  // 5 minutes delay
  private static final int DELAY = 300;
  // 1 hour 5 minutes after midnight
  private static final int ABSOLUTE_TIME = 3900;

  @Test
  void ofDelayCreatesDelayBasedUpdate() {
    var update = TimeUpdate.ofDelay(DELAY);

    assertEquals(DELAY, update.delaySeconds());
    assertNull(update.absoluteTimeSecondsSinceMidnight());
    assertNull(update.scheduledTimeSecondsSinceMidnight());
    assertTrue(update.hasDelay());
    assertFalse(update.hasAbsoluteTime());
  }

  @Test
  void ofAbsoluteCreatesAbsoluteTimeUpdate() {
    var update = TimeUpdate.ofAbsolute(ABSOLUTE_TIME, SCHEDULED_TIME);

    assertNull(update.delaySeconds());
    assertEquals(ABSOLUTE_TIME, update.absoluteTimeSecondsSinceMidnight());
    assertEquals(SCHEDULED_TIME, update.scheduledTimeSecondsSinceMidnight());
    assertFalse(update.hasDelay());
    assertTrue(update.hasAbsoluteTime());
  }

  @Test
  void ofAbsoluteWithoutScheduledTime() {
    var update = TimeUpdate.ofAbsolute(ABSOLUTE_TIME, null);

    assertNull(update.delaySeconds());
    assertEquals(ABSOLUTE_TIME, update.absoluteTimeSecondsSinceMidnight());
    assertNull(update.scheduledTimeSecondsSinceMidnight());
    assertFalse(update.hasDelay());
    assertTrue(update.hasAbsoluteTime());
  }

  @Test
  void resolveTimeWithDelay() {
    var update = TimeUpdate.ofDelay(DELAY);

    assertEquals(SCHEDULED_TIME + DELAY, update.resolveTime(SCHEDULED_TIME));
  }

  @Test
  void resolveTimeWithAbsoluteTime() {
    var update = TimeUpdate.ofAbsolute(ABSOLUTE_TIME, SCHEDULED_TIME);

    // Absolute time should be returned regardless of scheduled time parameter
    assertEquals(ABSOLUTE_TIME, update.resolveTime(SCHEDULED_TIME));
    assertEquals(ABSOLUTE_TIME, update.resolveTime(0));
  }

  @Test
  void resolveTimeWithNegativeDelay() {
    // 2 minutes early
    var earlyDelay = -120;
    var update = TimeUpdate.ofDelay(earlyDelay);

    assertEquals(SCHEDULED_TIME + earlyDelay, update.resolveTime(SCHEDULED_TIME));
  }

  @Test
  void resolveDelayWithDelayBasedUpdate() {
    var update = TimeUpdate.ofDelay(DELAY);

    assertEquals(DELAY, update.resolveDelay(SCHEDULED_TIME));
  }

  @Test
  void resolveDelayWithAbsoluteTimeUpdate() {
    var update = TimeUpdate.ofAbsolute(ABSOLUTE_TIME, SCHEDULED_TIME);

    // Delay should be calculated as absolute - scheduled
    assertEquals(ABSOLUTE_TIME - SCHEDULED_TIME, update.resolveDelay(SCHEDULED_TIME));
  }

  @Test
  void resolveDelayWithAbsoluteTimeAndNoStoredScheduled() {
    var update = TimeUpdate.ofAbsolute(ABSOLUTE_TIME, null);

    // Delay should be calculated using provided scheduled time
    assertEquals(ABSOLUTE_TIME - SCHEDULED_TIME, update.resolveDelay(SCHEDULED_TIME));
  }
}
