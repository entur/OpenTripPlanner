package org.opentripplanner.updater.trip.handlers;

import javax.annotation.Nullable;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;

/**
 * Handles trip cancellation updates.
 * Maps to GTFS-RT CANCELED and SIRI-ET cancellation=true.
 */
public class CancelTripHandler extends AbstractTripRemovalHandler {

  public CancelTripHandler(@Nullable TimetableSnapshotManager snapshotManager) {
    super(snapshotManager);
  }

  @Override
  protected void applyRemoval(RealTimeTripTimesBuilder builder) {
    builder.cancelTrip();
  }

  @Override
  protected String getLogAction() {
    return "Cancelled";
  }
}
