package org.opentripplanner.updater.trip.model;

import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;

/**
 * A unified representation of a stop reference that supports both:
 * <ul>
 *   <li>GTFS stop IDs (FeedScopedId) with direct resolution</li>
 *   <li>SIRI stop point references that may need scheduled stop point mapping</li>
 * </ul>
 * <p>
 * Additionally supports an assigned stop ID which may override the original reference
 * when stop assignment has been performed.
 */
public final class StopReference {

  @Nullable
  private final FeedScopedId stopId;

  @Nullable
  private final FeedScopedId assignedStopId;

  private final StopResolutionStrategy resolutionStrategy;

  /**
   * @param stopId The stop ID
   * @param assignedStopId The stop ID assigned after resolution (e.g., from stop assignment)
   * @param resolutionStrategy The strategy for resolving this stop reference
   */
  public StopReference(
    @Nullable FeedScopedId stopId,
    @Nullable FeedScopedId assignedStopId,
    StopResolutionStrategy resolutionStrategy
  ) {
    this.stopId = stopId;
    this.assignedStopId = assignedStopId;
    this.resolutionStrategy = Objects.requireNonNull(resolutionStrategy);
  }

  /**
   * Create a stop reference from a GTFS-style stop ID with direct resolution.
   */
  public static StopReference ofStopId(FeedScopedId stopId) {
    return new StopReference(stopId, null, StopResolutionStrategy.DIRECT);
  }

  /**
   * Create a stop reference from a GTFS-style stop ID with an assigned stop.
   */
  public static StopReference ofStopId(FeedScopedId stopId, @Nullable FeedScopedId assignedStopId) {
    return new StopReference(stopId, assignedStopId, StopResolutionStrategy.DIRECT);
  }

  /**
   * Create a stop reference that will first try scheduled stop point mapping,
   * then fall back to direct lookup. This is used for SIRI-style stop point references.
   */
  public static StopReference ofScheduledStopPointOrStopId(FeedScopedId stopId) {
    return new StopReference(stopId, null, StopResolutionStrategy.SCHEDULED_STOP_POINT_FIRST);
  }

  @Nullable
  public FeedScopedId stopId() {
    return stopId;
  }

  @Nullable
  public FeedScopedId assignedStopId() {
    return assignedStopId;
  }

  /**
   * Returns the strategy for resolving this stop reference.
   */
  public StopResolutionStrategy resolutionStrategy() {
    return resolutionStrategy;
  }

  /**
   * Returns true if this reference contains a stop ID.
   */
  public boolean hasStopId() {
    return stopId != null;
  }

  /**
   * Returns true if this reference has an assigned stop ID.
   */
  public boolean hasAssignedStopId() {
    return assignedStopId != null;
  }

  /**
   * Returns the primary stop ID to use for routing.
   * If an assigned stop ID is present, it takes precedence.
   * Otherwise, returns the original stop ID.
   */
  @Nullable
  public FeedScopedId primaryId() {
    if (assignedStopId != null) {
      return assignedStopId;
    }
    return stopId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    StopReference that = (StopReference) o;
    return (
      Objects.equals(stopId, that.stopId) &&
      Objects.equals(assignedStopId, that.assignedStopId) &&
      resolutionStrategy == that.resolutionStrategy
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(stopId, assignedStopId, resolutionStrategy);
  }

  @Override
  public String toString() {
    return (
      "StopReference{" +
      "stopId=" +
      stopId +
      ", assignedStopId=" +
      assignedStopId +
      ", resolutionStrategy=" +
      resolutionStrategy +
      '}'
    );
  }
}
