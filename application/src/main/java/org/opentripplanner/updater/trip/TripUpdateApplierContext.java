package org.opentripplanner.updater.trip;

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Context information needed by the applier when converting parsed trip updates
 * to real-time trip updates.
 */
public final class TripUpdateApplierContext {

  private final String feedId;

  @Nullable
  private final TimetableSnapshotManager snapshotManager;

  /**
   * @param feedId The feed ID for this update source
   * @param snapshotManager The timetable snapshot manager for accessing and updating trip data
   */
  public TripUpdateApplierContext(
    String feedId,
    @Nullable TimetableSnapshotManager snapshotManager
  ) {
    this.feedId = Objects.requireNonNull(feedId, "feedId must not be null");
    this.snapshotManager = snapshotManager;
  }

  public String feedId() {
    return feedId;
  }

  @Nullable
  public TimetableSnapshotManager snapshotManager() {
    return snapshotManager;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TripUpdateApplierContext that = (TripUpdateApplierContext) o;
    return (
      Objects.equals(feedId, that.feedId) && Objects.equals(snapshotManager, that.snapshotManager)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(feedId, snapshotManager);
  }

  @Override
  public String toString() {
    return (
      "TripUpdateApplierContext{" +
      "feedId='" +
      feedId +
      '\'' +
      ", snapshotManager=" +
      snapshotManager +
      '}'
    );
  }
}
