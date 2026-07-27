package org.opentripplanner.updater.trip.model;

import static com.google.common.truth.Truth.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class DelayUpdateTest {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 1, 15);
  private static final ZoneId TIME_ZONE = ZoneId.of("Europe/Oslo");
  // 1 hour = 3600 seconds after midnight
  private static final int SCHEDULED_TIME = 3600;
  // 5 minutes delay
  private static final int DELAY = 300;

  @Test
  void ofDelayCreatesDelayBasedUpdate() {
    assertThat(TimeUpdate.ofDelay(DELAY)).isEqualTo(new DelayUpdate(DELAY));
  }

  @Test
  void resolveTimeAddsDelayToScheduledTime() {
    var update = TimeUpdate.ofDelay(DELAY);

    assertThat(update.resolveTime(SCHEDULED_TIME)).isEqualTo(SCHEDULED_TIME + DELAY);
  }

  @Test
  void resolveTimeWithNegativeDelay() {
    // 2 minutes early
    var earlyDelay = -120;
    var update = TimeUpdate.ofDelay(earlyDelay);

    assertThat(update.resolveTime(SCHEDULED_TIME)).isEqualTo(SCHEDULED_TIME + earlyDelay);
  }

  @Test
  void resolveReturnsItself() {
    var update = TimeUpdate.ofDelay(DELAY);

    assertThat(update.resolve(SERVICE_DATE, TIME_ZONE)).isSameInstanceAs(update);
  }
}
