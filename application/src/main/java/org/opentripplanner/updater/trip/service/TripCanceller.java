package org.opentripplanner.updater.trip.service;

import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.updater.trip.model.change.TripRemoval;
import org.opentripplanner.updater.trip.model.change.TripUpdateResult;

/**
 * Cancels a trip on one service date.
 * Maps to GTFS-RT CANCELED and SIRI-ET cancellation=true.
 */
public final class TripCanceller extends TripRemover {

  public TripUpdateResult cancel(TripRemoval removal) {
    return remove(removal);
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
