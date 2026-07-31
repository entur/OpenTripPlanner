package org.opentripplanner.ext.updater.trip.unified.model.change;

import org.opentripplanner.ext.updater.trip.unified.model.command.ParsedStopTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.policy.PickDropPolicy;
import org.opentripplanner.ext.updater.trip.unified.policy.StopReplacementPolicy;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;

/**
 * Applies the stop time updates of one {@link TripRevision} to a {@link
 * RealTimeTripTimesBuilder}, accumulating the resulting changes into an immutable {@link
 * PatternModification}. Run by {@link TripRevision#apply}.
 * <p>
 * Format divergence is handled through the {@code FormatPolicy} carried by the revision:
 * stop matching, stop replacement, pick/drop and delay propagation are all asked of the policy
 * rather than branched on a format flag.
 */
final class StopTimeUpdateApplication {

  private final TripRevision revision;
  private final RealTimeTripTimesBuilder builder;
  private final TripPattern scheduledPattern;

  StopTimeUpdateApplication(
    TripRevision revision,
    RealTimeTripTimesBuilder builder,
    TripPattern scheduledPattern
  ) {
    this.revision = revision;
    this.builder = builder;
    this.scheduledPattern = scheduledPattern;
  }

  PatternModification run() {
    Trip trip = revision.trip();
    var policy = revision.formatPolicy();
    var cursor = policy
      .stopMatching()
      .newCursor(scheduledPattern, revision.scheduledTripTimes(), trip.getId());
    var stopReplacement = policy.stopReplacement();
    var pickDrop = policy.pickDrop();
    var mod = PatternModification.builder();

    for (ResolvedStopTimeUpdate stopUpdate : revision.stopTimeUpdates()) {
      var match = cursor.resolveIndex(stopUpdate);
      int stopIndex = match.index();
      // Absent when the update leaves the scheduled stop alone - which includes a stop assignment
      // the transit model could not resolve: the times still apply, only the pattern is left as
      // scheduled, the way the legacy updaters treat an unknown assignment.
      StopLocation replacementStop = match.replacementStop();

      // Get the scheduled stop from the pattern
      StopLocation scheduledStop = scheduledPattern.getStop(stopIndex);

      // Track stop replacements
      boolean hasStopReplacement =
        replacementStop != null && !replacementStop.getId().equals(scheduledStop.getId());

      if (hasStopReplacement) {
        // Validate the replacement against the format's stop replacement policy
        if (
          stopReplacement.check(scheduledStop, replacementStop) !=
          StopReplacementPolicy.Result.VALID
        ) {
          throw UpdateException.of(trip.getId(), UpdateErrorType.STOP_MISMATCH, stopIndex);
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
    PickDrop scheduled = scheduledPattern.getBoardType(stopIndex);
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
    PickDrop scheduled = scheduledPattern.getAlightType(stopIndex);
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
