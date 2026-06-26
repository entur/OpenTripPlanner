package org.opentripplanner.updater.trip.policy;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.updater.trip.gtfs.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.ForwardsDelayPropagationType;
import org.opentripplanner.updater.trip.model.TripUpdateOptions;

/**
 * Proves that {@link FormatPolicy} composes the exact same choices as the {@link TripUpdateOptions}
 * {@code siriDefaults()}/{@code gtfsRtDefaults()} factories, and that each derived policy reproduces
 * the corresponding enum branch. This is the behaviour-identical gate for the incremental migration
 * (#7220): consumers may migrate to the policy without changing emitted output.
 */
class FormatPolicyTest {

  @Test
  void siriWrapsSiriDefaults() {
    assertThat(FormatPolicy.siri().options()).isEqualTo(TripUpdateOptions.siriDefaults());
  }

  @Test
  void gtfsRtWrapsGtfsRtDefaults() {
    for (var f : ForwardsDelayPropagationType.values()) {
      for (var b : BackwardsDelayPropagationType.values()) {
        assertThat(FormatPolicy.gtfsRt(f, b).options()).isEqualTo(
          TripUpdateOptions.gtfsRtDefaults(f, b)
        );
      }
    }
  }

  @Test
  void fromOptionsRoundTrips() {
    var options = TripUpdateOptions.siriDefaults();
    assertSame(options, FormatPolicy.fromOptions(options).options());
  }

  @Test
  void pickDropMapsToTheEnumBranch() {
    assertSame(PickDropPolicy.ROUTABILITY_CHANGE_ONLY, FormatPolicy.siri().pickDrop());
    assertSame(
      PickDropPolicy.EXACT_MATCH,
      FormatPolicy.gtfsRt(
        ForwardsDelayPropagationType.DEFAULT,
        BackwardsDelayPropagationType.REQUIRED_NO_DATA
      ).pickDrop()
    );
  }

  @Test
  void realTimeStateMapsToTheEnumBranch() {
    assertSame(RealTimeStatePolicy.MODIFIED_ON_PATTERN_CHANGE, FormatPolicy.siri().realTimeState());
    assertSame(
      RealTimeStatePolicy.ALWAYS_UPDATED,
      FormatPolicy.gtfsRt(
        ForwardsDelayPropagationType.DEFAULT,
        BackwardsDelayPropagationType.REQUIRED_NO_DATA
      ).realTimeState()
    );
  }

  @Test
  void exactMatchReturnsParsedVerbatim() {
    assertSame(PickDrop.NONE, PickDropPolicy.EXACT_MATCH.effective(PickDrop.NONE, PickDrop.SCHEDULED));
    assertSame(
      PickDrop.SCHEDULED,
      PickDropPolicy.EXACT_MATCH.effective(PickDrop.SCHEDULED, PickDrop.NONE)
    );
  }

  @Test
  void routabilityChangeOnlyReproducesTheLegacyBranch() {
    // routable -> routable: no change
    assertNull(PickDropPolicy.ROUTABILITY_CHANGE_ONLY.effective(PickDrop.SCHEDULED, PickDrop.SCHEDULED));
    // non-routable -> routable: re-enable the stop
    assertSame(
      PickDrop.SCHEDULED,
      PickDropPolicy.ROUTABILITY_CHANGE_ONLY.effective(PickDrop.SCHEDULED, PickDrop.NONE)
    );
    // any -> non-routable: apply the parsed value
    assertSame(
      PickDrop.NONE,
      PickDropPolicy.ROUTABILITY_CHANGE_ONLY.effective(PickDrop.NONE, PickDrop.SCHEDULED)
    );
  }
}
