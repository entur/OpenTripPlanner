package org.opentripplanner.ext.updater.trip.unified.service;

import org.opentripplanner.ext.updater.trip.unified.model.change.TripRemoval;
import org.opentripplanner.ext.updater.trip.unified.model.change.TripUpdateResult;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;

/**
 * Deletes a trip on one service date: like a cancellation, but the trip is hidden from
 * passenger-facing results instead of shown as cancelled.
 * Maps to GTFS-RT DELETED.
 */
public final class TripDeleter extends TripRemover {

  public TripUpdateResult delete(TripRemoval removal) {
    return remove(removal);
  }

  @Override
  protected void applyRemoval(RealTimeTripTimesBuilder builder) {
    builder.withDeleted();
  }

  @Override
  protected String getLogAction() {
    return "Deleted";
  }
}
