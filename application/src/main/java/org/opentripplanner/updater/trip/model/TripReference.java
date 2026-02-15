package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.timetable.Direction;

/**
 * Identifies a trip for real-time updates. May contain various combinations of identifiers
 * depending on what information is available in the real-time feed.
 */
public final class TripReference {

  @Nullable
  private final FeedScopedId tripId;

  @Nullable
  private final FeedScopedId tripOnServiceDateId;

  @Nullable
  private final FeedScopedId routeId;

  @Nullable
  private final String startTime;

  @Nullable
  private final LocalDate startDate;

  @Nullable
  private final Direction direction;

  /**
   * @param tripId The trip ID (may be null if fuzzy matching by route/time is used)
   * @param tripOnServiceDateId The TripOnServiceDate ID (dated service journey ID)
   * @param routeId The route ID (used for fuzzy matching when trip ID is ambiguous)
   * @param startTime The scheduled start time of the trip (e.g., "08:30:00")
   * @param startDate The service date for the trip
   * @param direction The direction of travel (inbound/outbound)
   */
  public TripReference(
    @Nullable FeedScopedId tripId,
    @Nullable FeedScopedId tripOnServiceDateId,
    @Nullable FeedScopedId routeId,
    @Nullable String startTime,
    @Nullable LocalDate startDate,
    @Nullable Direction direction
  ) {
    this.tripId = tripId;
    this.tripOnServiceDateId = tripOnServiceDateId;
    this.routeId = routeId;
    this.startTime = startTime;
    this.startDate = startDate;
    this.direction = direction;
  }

  /**
   * Create a trip reference with just a trip ID.
   */
  public static TripReference ofTripId(FeedScopedId tripId) {
    return new TripReference(tripId, null, null, null, null, null);
  }

  /**
   * Create a builder for constructing trip references with multiple fields.
   */
  public static Builder builder() {
    return new Builder();
  }

  @Nullable
  public FeedScopedId tripId() {
    return tripId;
  }

  @Nullable
  public FeedScopedId tripOnServiceDateId() {
    return tripOnServiceDateId;
  }

  @Nullable
  public FeedScopedId routeId() {
    return routeId;
  }

  @Nullable
  public String startTime() {
    return startTime;
  }

  @Nullable
  public LocalDate startDate() {
    return startDate;
  }

  @Nullable
  public Direction direction() {
    return direction;
  }

  /**
   * Returns true if this reference has a trip ID.
   */
  public boolean hasTripId() {
    return tripId != null;
  }

  /**
   * Returns true if this reference has a TripOnServiceDate ID.
   */
  public boolean hasTripOnServiceDateId() {
    return tripOnServiceDateId != null;
  }

  /**
   * Returns true if this reference has a route ID.
   */
  public boolean hasRouteId() {
    return routeId != null;
  }

  /**
   * Returns true if this reference has a start time.
   */
  public boolean hasStartTime() {
    return startTime != null;
  }

  /**
   * Returns true if this reference has a start date.
   */
  public boolean hasStartDate() {
    return startDate != null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TripReference that = (TripReference) o;
    return (
      Objects.equals(tripId, that.tripId) &&
      Objects.equals(tripOnServiceDateId, that.tripOnServiceDateId) &&
      Objects.equals(routeId, that.routeId) &&
      Objects.equals(startTime, that.startTime) &&
      Objects.equals(startDate, that.startDate) &&
      direction == that.direction
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(tripId, tripOnServiceDateId, routeId, startTime, startDate, direction);
  }

  @Override
  public String toString() {
    return (
      "TripReference{" +
      "tripId=" +
      tripId +
      ", tripOnServiceDateId=" +
      tripOnServiceDateId +
      ", routeId=" +
      routeId +
      ", startTime='" +
      startTime +
      '\'' +
      ", startDate=" +
      startDate +
      ", direction=" +
      direction +
      '}'
    );
  }

  /**
   * Builder for creating TripReference instances.
   */
  public static class Builder {

    private FeedScopedId tripId;
    private FeedScopedId tripOnServiceDateId;
    private FeedScopedId routeId;
    private String startTime;
    private LocalDate startDate;
    private Direction direction;

    public Builder withTripId(FeedScopedId tripId) {
      this.tripId = tripId;
      return this;
    }

    public Builder withTripOnServiceDateId(FeedScopedId tripOnServiceDateId) {
      this.tripOnServiceDateId = tripOnServiceDateId;
      return this;
    }

    public Builder withRouteId(FeedScopedId routeId) {
      this.routeId = routeId;
      return this;
    }

    public Builder withStartTime(String startTime) {
      this.startTime = startTime;
      return this;
    }

    public Builder withStartDate(LocalDate startDate) {
      this.startDate = startDate;
      return this;
    }

    public Builder withDirection(Direction direction) {
      this.direction = direction;
      return this;
    }

    public TripReference build() {
      return new TripReference(
        tripId,
        tripOnServiceDateId,
        routeId,
        startTime,
        startDate,
        direction
      );
    }
  }
}
