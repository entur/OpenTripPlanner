package org.opentripplanner.updater.trip;

import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.updater.trip.model.ResolvedTripRemoval;

/**
 * Cancels a trip on one service date.
 * Maps to GTFS-RT CANCELED and SIRI-ET cancellation=true.
 */
public final class TripCanceller extends TripRemover {

  public TripUpdateResult cancel(ResolvedTripRemoval resolvedUpdate) {
    return remove(resolvedUpdate);
  }

  @Override
  protected void applyRemoval(RealTimeTripTimesBuilder builder) {
    builder.withCanceled();
  }

  @Override
  protected String getLogAction() {
    return "Cancelled";
  }
}
