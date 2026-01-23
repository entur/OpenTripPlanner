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

  private final TripIdResolver tripIdResolver;

  /**
   * @param feedId The feed ID for this update source
   * @param snapshotManager The timetable snapshot manager for accessing and updating trip data
   * @param tripIdResolver The resolver for looking up trips from trip references
   */
  public TripUpdateApplierContext(
    String feedId,
    @Nullable TimetableSnapshotManager snapshotManager,
    TripIdResolver tripIdResolver
  ) {
    this.feedId = Objects.requireNonNull(feedId, "feedId must not be null");
    this.snapshotManager = snapshotManager;
    this.tripIdResolver = Objects.requireNonNull(tripIdResolver, "tripIdResolver must not be null");
  }

  public String feedId() {
    return feedId;
  }

  @Nullable
  public TimetableSnapshotManager snapshotManager() {
    return snapshotManager;
  }

  public TripIdResolver tripIdResolver() {
    return tripIdResolver;
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
      Objects.equals(feedId, that.feedId) &&
      Objects.equals(snapshotManager, that.snapshotManager) &&
      Objects.equals(tripIdResolver, that.tripIdResolver)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(feedId, snapshotManager, tripIdResolver);
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
      ", tripIdResolver=" +
      tripIdResolver +
      '}'
    );
  }
}
