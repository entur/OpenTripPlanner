package org.opentripplanner.updater.trip.handlers;

import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;

/**
 * Handles trip cancellation updates.
 * Maps to GTFS-RT CANCELED and SIRI-ET cancellation=true.
 */
public class CancelTripHandler extends AbstractTripRemovalHandler {

  @Override
  protected void applyRemoval(RealTimeTripTimesBuilder builder) {
    builder.withCanceled();
  }

  @Override
  protected String getLogAction() {
    return "Cancelled";
  }
}
