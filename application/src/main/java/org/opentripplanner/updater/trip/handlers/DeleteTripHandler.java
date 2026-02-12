package org.opentripplanner.updater.trip.handlers;

import javax.annotation.Nullable;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;

/**
 * Handles trip deletion updates.
 * Maps to GTFS-RT DELETED.
 */
public class DeleteTripHandler extends AbstractTripRemovalHandler {

  public DeleteTripHandler(@Nullable TimetableSnapshotManager snapshotManager) {
    super(snapshotManager);
  }

  @Override
  protected void applyRemoval(RealTimeTripTimesBuilder builder) {
    builder.deleteTrip();
  }

  @Override
  protected String getLogAction() {
    return "Deleted";
  }
}
