package org.opentripplanner.ext.updater.trip.unified.policy;

import org.opentripplanner.transit.model.network.StopPattern;

/**
 * Decides when an update cancels the trip without saying so at journey level. Each format binds the
 * matching policy constant once at the boundary (see {@link FormatPolicy}).
 */
public sealed interface ImplicitCancellationPolicy
  permits ImplicitCancellationPolicy.NothingRoutable, ImplicitCancellationPolicy.NeverCancels {
  /**
   * @param updatedStopPattern the stop pattern the trip ends up running after the update is applied
   */
  boolean cancelsTrip(StopPattern updatedStopPattern);

  /** SIRI-ET: a trip nobody can board or alight anywhere does not run. */
  ImplicitCancellationPolicy NOTHING_ROUTABLE = new NothingRoutable();

  /** GTFS-RT: only the trip's own schedule relationship cancels it. */
  ImplicitCancellationPolicy NEVER = new NeverCancels();

  /**
   * SIRI-ET: the cancellation follows from the pattern, not from how the message stated it, which is
   * how a journey that cancels its calls one by one - through the {@code Cancellation} element or an
   * arrival/departure status - ends up cancelled as a whole. Legacy decides the same way, in
   * {@code ModifiedTripBuilder}, {@code AddedTripBuilder} and {@code ExtraCallTripBuilder}.
   */
  final class NothingRoutable implements ImplicitCancellationPolicy {

    @Override
    public boolean cancelsTrip(StopPattern updatedStopPattern) {
      return updatedStopPattern.isAllStopsNonRoutable();
    }
  }

  /**
   * GTFS-RT: nothing the calls say cancels the trip - only the schedule relationship of the trip
   * itself, {@code CANCELED} or {@code DELETED}, does. A skipped call cancels that call, so a trip
   * whose every call is skipped still runs, it just cannot be used anywhere. Legacy decides the same
   * way, by never cancelling a trip in {@code TripTimesUpdater}.
   */
  final class NeverCancels implements ImplicitCancellationPolicy {

    @Override
    public boolean cancelsTrip(StopPattern updatedStopPattern) {
      return false;
    }
  }
}
