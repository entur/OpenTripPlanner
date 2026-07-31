package org.opentripplanner.ext.updater.trip.unified.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;

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
    assertSame(UnreportedTimePolicy.AIMED_TIME, siri.unreportedTime());
    assertSame(ImplicitCancellationPolicy.NOTHING_ROUTABLE, siri.implicitCancellation());
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
    assertSame(UnreportedTimePolicy.NO_TIME, gtfs.unreportedTime());
    // Only CANCELED or DELETED cancels a GTFS-RT trip: reading a cancellation out of the pattern is
    // the SIRI-ET rule, and the two formats must not be levelled out on this axis.
    assertSame(ImplicitCancellationPolicy.NEVER, gtfs.implicitCancellation());
  }

  /**
   * The seeding is a property of the format, not of how the router configured delay propagation:
   * legacy GTFS-RT starts from no times at all whatever the propagation types are, which is what
   * lets a switched-off direction reject an update that leaves a gap.
   */
  @Test
  void gtfsRtReportsNoTimeWhateverThePropagation() {
    assertSame(
      UnreportedTimePolicy.NO_TIME,
      FormatPolicy.gtfsRt(
        ForwardsDelayPropagationType.NONE,
        BackwardsDelayPropagationType.NONE
      ).unreportedTime()
    );
  }

  @Test
  void exactMatchReturnsParsedVerbatim() {
    assertSame(
      PickDrop.NONE,
      PickDropPolicy.EXACT_MATCH.effective(PickDrop.NONE, PickDrop.SCHEDULED)
    );
    assertSame(
      PickDrop.SCHEDULED,
      PickDropPolicy.EXACT_MATCH.effective(PickDrop.SCHEDULED, PickDrop.NONE)
    );
  }

  @Test
  void exactMatchCancelsBoardingWhateverWasScheduled() {
    assertSame(
      PickDrop.CANCELLED,
      PickDropPolicy.EXACT_MATCH.effectiveWhenCancelled(PickDrop.SCHEDULED)
    );
    assertSame(
      PickDrop.CANCELLED,
      PickDropPolicy.EXACT_MATCH.effectiveWhenCancelled(PickDrop.NONE)
    );
  }

  @Test
  void routabilityChangeOnlyCancelsOnlyWhatWasRoutable() {
    assertSame(
      PickDrop.CANCELLED,
      PickDropPolicy.ROUTABILITY_CHANGE_ONLY.effectiveWhenCancelled(PickDrop.SCHEDULED)
    );
    // Boarding was not possible in the first place: no change, hence no pattern change.
    assertNull(PickDropPolicy.ROUTABILITY_CHANGE_ONLY.effectiveWhenCancelled(PickDrop.NONE));
    assertNull(PickDropPolicy.ROUTABILITY_CHANGE_ONLY.effectiveWhenCancelled(PickDrop.CANCELLED));
  }

  @Test
  void routabilityChangeOnlyReproducesTheLegacyBranch() {
    // routable -> routable: no change
    assertNull(
      PickDropPolicy.ROUTABILITY_CHANGE_ONLY.effective(PickDrop.SCHEDULED, PickDrop.SCHEDULED)
    );
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
