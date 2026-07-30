package org.opentripplanner.updater.trip;

import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.updater.trip.model.TripRemoval;

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
