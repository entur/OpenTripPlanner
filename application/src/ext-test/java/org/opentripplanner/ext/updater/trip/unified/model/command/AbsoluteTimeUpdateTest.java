package org.opentripplanner.ext.updater.trip.unified.model.command;

import static com.google.common.truth.Truth.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class AbsoluteTimeUpdateTest {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 1, 15);
  private static final ZoneId TIME_ZONE = ZoneId.of("Europe/Oslo");
  // 1 hour = 3600 seconds after midnight
  private static final int AIMED_TIME = 3600;
  // 1 hour 5 minutes after midnight
  private static final int ACTUAL_TIME = 3900;

  @Test
  void ofAbsoluteCreatesAbsoluteTimeUpdate() {
    assertThat(TimeUpdate.ofAbsolute(ACTUAL_TIME, AIMED_TIME)).isEqualTo(
      new AbsoluteTimeUpdate(ACTUAL_TIME, AIMED_TIME)
    );
  }

  @Test
  void resolveTimeIgnoresScheduledTime() {
    var update = TimeUpdate.ofAbsolute(ACTUAL_TIME, AIMED_TIME);

    assertThat(update.resolveTime(AIMED_TIME)).isEqualTo(ACTUAL_TIME);
    assertThat(update.resolveTime(0)).isEqualTo(ACTUAL_TIME);
  }

  @Test
  void aimedTimeOrActualPrefersAimedTime() {
    var update = TimeUpdate.ofAbsolute(ACTUAL_TIME, AIMED_TIME);

    assertThat(update.aimedTimeOrActual()).isEqualTo(AIMED_TIME);
  }

  @Test
  void aimedTimeOrActualFallsBackWhenFeedReportsNoAimedTime() {
    var update = TimeUpdate.ofAbsolute(ACTUAL_TIME, null);

    assertThat(update.aimedTime()).isNull();
    assertThat(update.aimedTimeOrActual()).isEqualTo(ACTUAL_TIME);
  }

  @Test
  void aimedTimeOrActualFallsBackOnMidnight() {
    // An aimed time of exactly midnight is treated as missing
    var update = TimeUpdate.ofAbsolute(ACTUAL_TIME, 0);

    assertThat(update.aimedTimeOrActual()).isEqualTo(ACTUAL_TIME);
  }

  @Test
  void resolveReturnsItself() {
    var update = TimeUpdate.ofAbsolute(ACTUAL_TIME, AIMED_TIME);

    assertThat(update.resolve(SERVICE_DATE, TIME_ZONE)).isSameInstanceAs(update);
  }
}
