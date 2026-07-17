package org.opentripplanner.updater.trip;

import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;

/**
 * Test helper for writing a {@link RealTimeTripUpdate} produced by a domain operation to the
 * timetable snapshot. The test environment uses atomic commits, so the update is visible to
 * {@link TransitTestEnvironment#transitService()} as soon as this method returns.
 */
public final class SnapshotTestHelper {

  private SnapshotTestHelper() {}

  public static void applyAndCommit(
    TransitTestEnvironment env,
    RealTimeTripUpdate realTimeTripUpdate
  ) {
    try {
      env
        .updateManager()
        .submit(ctx -> {
          var buffer = ctx.repository(env.timetableHandle());
          TripUpdateApplier.apply(buffer, realTimeTripUpdate);
        })
        .get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
