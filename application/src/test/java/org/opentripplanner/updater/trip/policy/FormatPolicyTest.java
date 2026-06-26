package org.opentripplanner.updater.trip.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.updater.trip.gtfs.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.ForwardsDelayPropagationType;

/**
 * Proves that the {@link FormatPolicy#siri()} / {@link FormatPolicy#gtfsRt} factories compose the
 * exact per-axis policies the SIRI-ET / GTFS-RT formats used before the migration (#7220), and that
 * the {@link PickDropPolicy} constants reproduce the legacy {@code resolveEffectivePickDrop} branch.
 */
class FormatPolicyTest {

  @Test
  void siriComposesTheSiriPolicies() {
    var siri = FormatPolicy.siri();
    assertSame(PickDropPolicy.ROUTABILITY_CHANGE_ONLY, siri.pickDrop());
    assertSame(RealTimeStatePolicy.MODIFIED_ON_PATTERN_CHANGE, siri.realTimeState());
    assertSame(StopMatchingPolicy.POSITIONAL, siri.stopMatching());
    assertSame(StopReplacementPolicy.SAME_PARENT_STATION, siri.stopReplacement());
    assertSame(FirstLastStopTimePolicy.ADJUST, siri.firstLastStopTime());
    assertSame(ScheduledDataPolicy.INCLUDE, siri.scheduledData());
    assertSame(UnknownStopPolicy.FAIL, siri.unknownStop());
    assertEquals(
      DelayPropagationPolicy.of(
        ForwardsDelayPropagationType.NONE,
        BackwardsDelayPropagationType.NONE
      ),
      siri.delayPropagation()
    );
  }

  @Test
  void gtfsRtComposesTheGtfsPolicies() {
    var f = ForwardsDelayPropagationType.DEFAULT;
    var b = BackwardsDelayPropagationType.REQUIRED_NO_DATA;
    var gtfs = FormatPolicy.gtfsRt(f, b);
    assertSame(PickDropPolicy.EXACT_MATCH, gtfs.pickDrop());
    assertSame(RealTimeStatePolicy.ALWAYS_UPDATED, gtfs.realTimeState());
    assertSame(StopMatchingPolicy.BY_SEQUENCE_OR_ID, gtfs.stopMatching());
    assertSame(StopReplacementPolicy.ANY_STOP, gtfs.stopReplacement());
    assertSame(FirstLastStopTimePolicy.PRESERVE, gtfs.firstLastStopTime());
    assertSame(ScheduledDataPolicy.EXCLUDE, gtfs.scheduledData());
    assertSame(UnknownStopPolicy.IGNORE, gtfs.unknownStop());
    assertEquals(DelayPropagationPolicy.of(f, b), gtfs.delayPropagation());
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
