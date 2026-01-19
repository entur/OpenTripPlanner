package org.opentripplanner.updater.trip.model;

import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;

/**
 * A unified representation of a stop reference that supports both:
 * <ul>
 *   <li>GTFS stop IDs (FeedScopedId)</li>
 *   <li>SIRI stop point references (quay references like "NSR:Quay:1234")</li>
 * </ul>
 * <p>
 * Additionally supports an assigned stop ID which may override the original reference
 * when stop assignment has been performed.
 */
public final class StopReference {

  @Nullable
  private final FeedScopedId stopId;

  @Nullable
  private final String stopPointRef;

  @Nullable
  private final FeedScopedId assignedStopId;

  /**
   * @param stopId The GTFS-style feed-scoped stop ID
   * @param stopPointRef The SIRI-style stop point reference (quay reference)
   * @param assignedStopId The stop ID assigned after resolution (e.g., from stop assignment)
   */
  public StopReference(
    @Nullable FeedScopedId stopId,
    @Nullable String stopPointRef,
    @Nullable FeedScopedId assignedStopId
  ) {
    this.stopId = stopId;
    this.stopPointRef = stopPointRef;
    this.assignedStopId = assignedStopId;
  }

  /**
   * Create a stop reference from a GTFS-style stop ID.
   */
  public static StopReference ofStopId(FeedScopedId stopId) {
    return new StopReference(stopId, null, null);
  }

  /**
   * Create a stop reference from a GTFS-style stop ID with an assigned stop.
   */
  public static StopReference ofStopId(FeedScopedId stopId, @Nullable FeedScopedId assignedStopId) {
    return new StopReference(stopId, null, assignedStopId);
  }

  /**
   * Create a stop reference from a SIRI-style stop point reference.
   */
  public static StopReference ofStopPointRef(String stopPointRef) {
    return new StopReference(null, stopPointRef, null);
  }

  /**
   * Create a stop reference from a SIRI-style stop point reference with an assigned stop.
   */
  public static StopReference ofStopPointRef(
    String stopPointRef,
    @Nullable FeedScopedId assignedStopId
  ) {
    return new StopReference(null, stopPointRef, assignedStopId);
  }

  @Nullable
  public FeedScopedId stopId() {
    return stopId;
  }

  @Nullable
  public String stopPointRef() {
    return stopPointRef;
  }

  @Nullable
  public FeedScopedId assignedStopId() {
    return assignedStopId;
  }

  /**
   * Returns true if this reference contains a GTFS-style stop ID.
   */
  public boolean hasStopId() {
    return stopId != null;
  }

  /**
   * Returns true if this reference contains a SIRI-style stop point reference.
   */
  public boolean hasStopPointRef() {
    return stopPointRef != null;
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
   * Returns null if only a stop point reference is available and needs resolution.
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
      Objects.equals(stopPointRef, that.stopPointRef) &&
      Objects.equals(assignedStopId, that.assignedStopId)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(stopId, stopPointRef, assignedStopId);
  }

  @Override
  public String toString() {
    return (
      "StopReference{" +
      "stopId=" +
      stopId +
      ", stopPointRef='" +
      stopPointRef +
      '\'' +
      ", assignedStopId=" +
      assignedStopId +
      '}'
    );
  }
}
