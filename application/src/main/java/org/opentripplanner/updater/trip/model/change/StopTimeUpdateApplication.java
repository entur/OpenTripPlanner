package org.opentripplanner.updater.trip.model.change;

import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.command.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.policy.StopReplacementPolicy;

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
      StopLocation resolvedStop = match.resolvedStop();

      // Get the scheduled stop from the pattern
      StopLocation scheduledStop = scheduledPattern.getStop(stopIndex);

      // Check if we failed to resolve an assigned stop
      if (resolvedStop == null && stopUpdate.stopReference().hasAssignedStopId()) {
        throw UpdateException.of(trip.getId(), UpdateErrorType.UNKNOWN_STOP, stopIndex);
      }

      // Track stop replacements
      boolean hasStopReplacement =
        resolvedStop != null && !resolvedStop.getId().equals(scheduledStop.getId());

      if (hasStopReplacement) {
        // Validate the replacement against the format's stop replacement policy
        if (
          stopReplacement.check(scheduledStop, resolvedStop) != StopReplacementPolicy.Result.VALID
        ) {
          throw UpdateException.of(trip.getId(), UpdateErrorType.STOP_MISMATCH, stopIndex);
        }

        // Valid replacement - track it
        mod.putStopReplacement(stopIndex, resolvedStop);
      }

      // Handle skipped/cancelled stops
      if (stopUpdate.isSkipped()) {
        builder.withCanceled(stopIndex);
        mod.markCancellation();

        // Track cancelled stops as pickup/dropoff changes for both GTFS-RT and SIRI-ET
        // This ensures pattern modification is detected and matches legacy behavior
        mod.putPickup(stopIndex, PickDrop.CANCELLED);
        mod.putDropoff(stopIndex, PickDrop.CANCELLED);

        // For GTFS-RT SKIPPED stops, don't apply time updates - the forward delay
        // interpolator will interpolate times from surrounding stops.
        // For SIRI CANCELLED stops, fall through to apply explicit time updates
        // to avoid NEGATIVE_HOP_TIME errors on delayed trips.
        if (stopUpdate.status() == ParsedStopTimeUpdate.StopUpdateStatus.SKIPPED) {
          continue;
        }
      }

      // Flag NO_DATA stops. Their (absent) real-time times are skipped below, but pick/drop,
      // occupancy, headsign and prediction flags are still applied, matching the legacy SIRI path
      // where these flags follow withNoData so they take precedence.
      boolean noData = stopUpdate.status() == ParsedStopTimeUpdate.StopUpdateStatus.NO_DATA;
      if (noData) {
        builder.withNoData(stopIndex);
      }

      // Track pickup/dropoff changes
      if (stopUpdate.pickup() != null) {
        PickDrop scheduledPickup = scheduledPattern.getBoardType(stopIndex);
        var effectivePickup = pickDrop.effective(stopUpdate.pickup(), scheduledPickup);
        if (effectivePickup != null && !effectivePickup.equals(scheduledPickup)) {
          mod.putPickup(stopIndex, effectivePickup);
        }
      }

      if (stopUpdate.dropoff() != null) {
        PickDrop scheduledDropoff = scheduledPattern.getAlightType(stopIndex);
        var effectiveDropoff = pickDrop.effective(stopUpdate.dropoff(), scheduledDropoff);
        if (effectiveDropoff != null && !effectiveDropoff.equals(scheduledDropoff)) {
          mod.putDropoff(stopIndex, effectiveDropoff);
        }
      }

      // Apply time updates (NO_DATA stops carry no real-time times, so skip them)
      boolean hasTimeUpdate = false;

      if (!noData && stopUpdate.hasArrivalUpdate()) {
        var arrivalUpdate = stopUpdate.arrivalUpdate();
        int scheduledArrival = builder.getScheduledArrivalTime(stopIndex);
        int newArrivalTime = arrivalUpdate.resolveTime(scheduledArrival);
        builder.withArrivalTime(stopIndex, newArrivalTime);
        hasTimeUpdate = true;
      }

      if (!noData && stopUpdate.hasDepartureUpdate()) {
        var departureUpdate = stopUpdate.departureUpdate();
        int scheduledDeparture = builder.getScheduledDepartureTime(stopIndex);
        int newDepartureTime = departureUpdate.resolveTime(scheduledDeparture);
        builder.withDepartureTime(stopIndex, newDepartureTime);
        hasTimeUpdate = true;
      }

      if (hasTimeUpdate) {
        mod.markTimeUpdates();
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

    // Apply delay propagation according to the format policy (forwards then backwards).
    if (policy.delayPropagation().propagate(builder)) {
      mod.markTimeUpdates();
    }

    // Fallback: copy any times still missing after propagation from the scheduled timetable.
    builder.copyMissingTimesFromScheduledTimetable();

    return mod.build();
  }
}
