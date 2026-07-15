package org.opentripplanner.updater.trip;

import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ResolvedTripRemoval;
import org.opentripplanner.updater.trip.model.TripDeletion;

/**
 * Deletes a trip on one service date: like a cancellation, but the trip is hidden from
 * passenger-facing results instead of shown as cancelled.
 * Maps to GTFS-RT DELETED.
 */
public final class TripDeleter extends TripRemover {

  public TripDeleter(TripRemovalResolver resolver) {
    super(resolver);
  }

  public TripUpdateResult delete(TripDeletion parsedUpdate) throws UpdateException {
    return remove(parsedUpdate);
  }

  public TripUpdateResult delete(ResolvedTripRemoval resolvedUpdate) {
    return remove(resolvedUpdate);
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
