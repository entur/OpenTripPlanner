package org.opentripplanner.ext.updater.trip.unified.service;

import org.opentripplanner.ext.updater.trip.unified.model.change.TripRemoval;
import org.opentripplanner.ext.updater.trip.unified.model.change.TripUpdateResult;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;

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
