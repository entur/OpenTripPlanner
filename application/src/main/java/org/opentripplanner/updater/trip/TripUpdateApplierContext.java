package org.opentripplanner.updater.trip;

import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.updater.trip.siri.SiriTripPatternCache;

/**
 * Context information needed by the applier when converting parsed trip updates
 * to real-time trip updates.
 */
public final class TripUpdateApplierContext {

  private final String feedId;

  @Nullable
  private final TimetableSnapshotManager snapshotManager;

  private final TripResolver tripResolver;

  private final ServiceDateResolver serviceDateResolver;

  private final StopResolver stopResolver;

  private final SiriTripPatternCache tripPatternCache;

  /**
   * @param feedId The feed ID for this update source
   * @param snapshotManager The timetable snapshot manager for accessing and updating trip data
   * @param tripResolver The resolver for looking up trips from trip references
   * @param serviceDateResolver The resolver for extracting service dates from trip updates
   * @param stopResolver The resolver for looking up stops from stop references
   * @param tripPatternCache The cache for creating and reusing modified trip patterns
   */
  public TripUpdateApplierContext(
    String feedId,
    @Nullable TimetableSnapshotManager snapshotManager,
    TripResolver tripResolver,
    ServiceDateResolver serviceDateResolver,
    StopResolver stopResolver,
    SiriTripPatternCache tripPatternCache
  ) {
    this.feedId = Objects.requireNonNull(feedId, "feedId must not be null");
    this.snapshotManager = snapshotManager;
    this.tripResolver = Objects.requireNonNull(tripResolver, "tripResolver must not be null");
    this.serviceDateResolver = Objects.requireNonNull(
      serviceDateResolver,
      "serviceDateResolver must not be null"
    );
    this.stopResolver = Objects.requireNonNull(stopResolver, "stopResolver must not be null");
    this.tripPatternCache = Objects.requireNonNull(
      tripPatternCache,
      "tripPatternCache must not be null"
    );
  }

  public String feedId() {
    return feedId;
  }

  @Nullable
  public TimetableSnapshotManager snapshotManager() {
    return snapshotManager;
  }

  public TripResolver tripResolver() {
    return tripResolver;
  }

  public ServiceDateResolver serviceDateResolver() {
    return serviceDateResolver;
  }

  public StopResolver stopResolver() {
    return stopResolver;
  }

  public SiriTripPatternCache tripPatternCache() {
    return tripPatternCache;
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
      Objects.equals(tripResolver, that.tripResolver) &&
      Objects.equals(serviceDateResolver, that.serviceDateResolver) &&
      Objects.equals(stopResolver, that.stopResolver) &&
      Objects.equals(tripPatternCache, that.tripPatternCache)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      feedId,
      snapshotManager,
      tripResolver,
      serviceDateResolver,
      stopResolver,
      tripPatternCache
    );
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
      ", tripResolver=" +
      tripResolver +
      ", serviceDateResolver=" +
      serviceDateResolver +
      ", stopResolver=" +
      stopResolver +
      ", tripPatternCache=" +
      tripPatternCache +
      '}'
    );
  }
}
