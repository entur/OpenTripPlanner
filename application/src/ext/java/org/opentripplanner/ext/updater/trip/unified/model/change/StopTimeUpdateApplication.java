package org.opentripplanner.ext.updater.trip.unified.model.change;

import java.util.List;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.model.command.ParsedStopTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.policy.FormatPolicy;
import org.opentripplanner.ext.updater.trip.unified.policy.PickDropPolicy;
import org.opentripplanner.ext.updater.trip.unified.policy.StopReplacementPolicy;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;

/**
 * Applies the stop time updates of one trip update to a {@link RealTimeTripTimesBuilder},
 * accumulating the resulting changes into an immutable {@link PatternModification}. Run by
 * {@link TripRevision#apply} against the scheduled pattern of an existing trip, and by
 * {@link AddedTripRevision#apply} against the pattern a real-time added trip was added to.
 * <p>
 * Format divergence is handled through the {@code FormatPolicy} carried by the update:
 * stop matching, stop replacement, pick/drop and delay propagation are all asked of the policy
 * rather than branched on a format flag.
 */
final class StopTimeUpdateApplication {

  private final RealTimeTripTimesBuilder builder;

  /** The pattern the calls are applied against: the trip's scheduled or added pattern. */
  private final TripPattern baselinePattern;

  /** The baseline times, which know the {@code stop_sequence} numbering of the trip's calls. */
  private final TripTimes baselineTripTimes;

  private final List<ResolvedStopTimeUpdate> stopTimeUpdates;
  private final FormatPolicy policy;
  private final FeedScopedId tripId;

  StopTimeUpdateApplication(
    RealTimeTripTimesBuilder builder,
    TripPattern baselinePattern,
    TripTimes baselineTripTimes,
    List<ResolvedStopTimeUpdate> stopTimeUpdates,
    FormatPolicy policy,
    FeedScopedId tripId
  ) {
    this.builder = builder;
    this.baselinePattern = baselinePattern;
    this.baselineTripTimes = baselineTripTimes;
    this.stopTimeUpdates = stopTimeUpdates;
    this.policy = policy;
    this.tripId = tripId;
  }

  PatternModification run() {
    var cursor = policy.stopMatching().newCursor(baselinePattern, baselineTripTimes, tripId);
    var stopReplacement = policy.stopReplacement();
    var pickDrop = policy.pickDrop();
    var mod = PatternModification.builder();

    for (ResolvedStopTimeUpdate stopUpdate : stopTimeUpdates) {
      var match = cursor.resolveIndex(stopUpdate);
      int stopIndex = match.index();
      // Absent when the update leaves the scheduled stop alone - which includes a stop assignment
      // the transit model could not resolve: the times still apply, only the pattern is left as
      // scheduled, the way the legacy updaters treat an unknown assignment.
      StopLocation replacementStop = match.replacementStop();

      // Get the scheduled stop from the pattern
      StopLocation scheduledStop = baselinePattern.getStop(stopIndex);

      // Track stop replacements
      boolean hasStopReplacement =
        replacementStop != null && !replacementStop.getId().equals(scheduledStop.getId());

      if (hasStopReplacement) {
        // Validate the replacement against the format's stop replacement policy
        if (
          stopReplacement.check(scheduledStop, replacementStop) !=
          StopReplacementPolicy.Result.VALID
        ) {
          throw UpdateException.of(tripId, UpdateErrorType.STOP_MISMATCH, stopIndex);
        }

        // Valid replacement - track it
        mod.putStopReplacement(stopIndex, replacementStop);
      }

      // A cancelled call cannot be boarded or alighted, and the whole call being cancelled cancels
      // both of its ends.
      if (stopUpdate.isSkipped()) {
        builder.withCanceled(stopIndex);
        mod.markCancellation();
      }

      // Resolve what the message does to boarding and alighting at this stop. Each end is asked
      // separately, because a SIRI-ET call can cancel one of them alone through its arrival or
      // departure status.
      applyPickup(mod, stopIndex, stopUpdate, pickDrop);
      applyDropoff(mod, stopIndex, stopUpdate, pickDrop);

      // For GTFS-RT SKIPPED stops, don't apply time updates - the forward delay
      // interpolator will interpolate times from surrounding stops.
      // For SIRI CANCELLED stops, fall through to apply explicit time updates
      // to avoid NEGATIVE_HOP_TIME errors on delayed trips.
      if (stopUpdate.status() == ParsedStopTimeUpdate.StopUpdateStatus.SKIPPED) {
        continue;
      }

      // Flag NO_DATA stops. Their (absent) real-time times are skipped below, but pick/drop,
      // occupancy, headsign and prediction flags are still applied, matching the legacy SIRI path
      // where these flags follow withNoData so they take precedence.
      boolean noData = stopUpdate.status() == ParsedStopTimeUpdate.StopUpdateStatus.NO_DATA;
      if (noData) {
        builder.withNoData(stopIndex);
      }

      // Apply time updates (NO_DATA stops carry no real-time times, so skip them)
      if (!noData && stopUpdate.hasArrivalUpdate()) {
        var arrivalUpdate = stopUpdate.arrivalUpdate();
        int scheduledArrival = builder.getScheduledArrivalTime(stopIndex);
        int newArrivalTime = arrivalUpdate.resolveTime(scheduledArrival);
        builder.withArrivalTime(stopIndex, newArrivalTime);
      }

      if (!noData && stopUpdate.hasDepartureUpdate()) {
        var departureUpdate = stopUpdate.departureUpdate();
        int scheduledDeparture = builder.getScheduledDepartureTime(stopIndex);
        int newDepartureTime = departureUpdate.resolveTime(scheduledDeparture);
        builder.withDepartureTime(stopIndex, newDepartureTime);
      }

      // Apply stop headsign if provided
      if (stopUpdate.stopHeadsign() != null) {
        builder.withStopHeadsign(stopIndex, stopUpdate.stopHeadsign());
      }

      // Apply stop real-time state flags
      if (stopUpdate.hasArrived()) {
        builder.withHasArrived(stopIndex, true);
      }
      if (stopUpdate.hasDeparted()) {
        builder.withHasDeparted(stopIndex, true);
      }

      if (stopUpdate.predictionInaccurate() && !stopUpdate.isSkipped()) {
        builder.withInaccuratePredictions(stopIndex);
      }

      // Apply occupancy
      if (stopUpdate.occupancy() != null) {
        builder.withOccupancyStatus(stopIndex, stopUpdate.occupancy());
      }
    }

    // Apply delay propagation according to the format policy (forwards then backwards). A stop the
    // interpolators leave without a time is deliberately left that way: the trip times then fail to
    // build, which is how a format that propagates no delays rejects an incomplete update.
    policy.delayPropagation().propagate(builder);

    return mod.build();
  }

  /**
   * Record what the update does to boarding at a stop: cancel it if the message cancels the
   * departure, otherwise apply the boarding restriction it reports, if any. What each of those means
   * for the value in the pattern is the format's answer to give - GTFS-RT states the pick/drop it
   * wants, while SIRI-ET only states routability changes and leaves boarding that was not possible
   * anyway untouched.
   */
  private void applyPickup(
    PatternModification.Builder mod,
    int stopIndex,
    ResolvedStopTimeUpdate stopUpdate,
    PickDropPolicy pickDrop
  ) {
    PickDrop scheduled = baselinePattern.getBoardType(stopIndex);
    PickDrop effective;
    if (stopUpdate.isPickupCancelled()) {
      effective = pickDrop.effectiveWhenCancelled(scheduled);
    } else if (stopUpdate.pickup() != null) {
      effective = pickDrop.effective(stopUpdate.pickup(), scheduled);
    } else {
      return;
    }
    if (effective != null && !effective.equals(scheduled)) {
      mod.putPickup(stopIndex, effective);
    }
  }

  /** Record what the update does to alighting at a stop, see {@link #applyPickup}. */
  private void applyDropoff(
    PatternModification.Builder mod,
    int stopIndex,
    ResolvedStopTimeUpdate stopUpdate,
    PickDropPolicy pickDrop
  ) {
    PickDrop scheduled = baselinePattern.getAlightType(stopIndex);
    PickDrop effective;
    if (stopUpdate.isDropoffCancelled()) {
      effective = pickDrop.effectiveWhenCancelled(scheduled);
    } else if (stopUpdate.dropoff() != null) {
      effective = pickDrop.effective(stopUpdate.dropoff(), scheduled);
    } else {
      return;
    }
    if (effective != null && !effective.equals(scheduled)) {
      mod.putDropoff(stopIndex, effective);
    }
  }
}
