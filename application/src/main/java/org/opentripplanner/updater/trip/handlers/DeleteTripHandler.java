package org.opentripplanner.updater.trip.handlers;

import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;

/**
 * Handles trip deletion updates.
 * Maps to GTFS-RT DELETED.
 */
public class DeleteTripHandler extends AbstractTripRemovalHandler {

  @Override
  protected void applyRemoval(RealTimeTripTimesBuilder builder) {
    builder.deleteTrip();
  }

  @Override
  protected String getLogAction() {
    return "Deleted";
  }
}
