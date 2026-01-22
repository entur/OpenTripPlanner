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
  private final FeedScopedId routeId;

  @Nullable
  private final String startTime;

  @Nullable
  private final LocalDate startDate;

  @Nullable
  private final Direction direction;

  @Nullable
  private final String vehicleRef;

  @Nullable
  private final String lineRef;

  private final FuzzyMatchingHint fuzzyMatchingHint;

  private final TripReferenceType tripReferenceType;

  /**
   * Type of trip reference, indicating how the trip should be resolved.
   */
  public enum TripReferenceType {
    /**
     * Standard trip reference - resolve using trip ID directly
     */
    STANDARD,

    /**
     * Dated service journey reference - resolve using TripIdAndServiceDate
     * (SIRI DatedVehicleJourneyRef or FramedVehicleJourneyRef)
     */
    DATED_SERVICE_JOURNEY,
  }

  /**
   * @param tripId The trip ID (may be null if fuzzy matching by route/time is used)
   * @param routeId The route ID (used for fuzzy matching when trip ID is ambiguous)
   * @param startTime The scheduled start time of the trip (e.g., "08:30:00")
   * @param startDate The service date for the trip
   * @param direction The direction of travel (inbound/outbound)
   * @param vehicleRef The vehicle reference (SIRI: internal planning code/train number)
   * @param lineRef The line reference (SIRI: line identifier)
   * @param fuzzyMatchingHint Hint for whether fuzzy matching should be attempted
   * @param tripReferenceType Type of trip reference for proper resolution
   */
  public TripReference(
    @Nullable FeedScopedId tripId,
    @Nullable FeedScopedId routeId,
    @Nullable String startTime,
    @Nullable LocalDate startDate,
    @Nullable Direction direction,
    @Nullable String vehicleRef,
    @Nullable String lineRef,
    FuzzyMatchingHint fuzzyMatchingHint,
    TripReferenceType tripReferenceType
  ) {
    this.tripId = tripId;
    this.routeId = routeId;
    this.startTime = startTime;
    this.startDate = startDate;
    this.direction = direction;
    this.vehicleRef = vehicleRef;
    this.lineRef = lineRef;
    this.fuzzyMatchingHint = Objects.requireNonNull(
      fuzzyMatchingHint,
      "fuzzyMatchingHint must not be null"
    );
    this.tripReferenceType = Objects.requireNonNull(
      tripReferenceType,
      "tripReferenceType must not be null"
    );
  }

  /**
   * Hint for whether fuzzy trip matching should be attempted.
   */
  public enum FuzzyMatchingHint {
    /**
     * Exact trip ID match is required. Do not attempt fuzzy matching.
     */
    EXACT_MATCH_REQUIRED,

    /**
     * Fuzzy matching is allowed if exact match fails.
     * The trip may be matched by route, start time, direction, etc.
     */
    FUZZY_MATCH_ALLOWED,
  }

  /**
   * Create a trip reference with just a trip ID (exact match required).
   */
  public static TripReference ofTripId(FeedScopedId tripId) {
    return new TripReference(
      tripId,
      null,
      null,
      null,
      null,
      null,
      null,
      FuzzyMatchingHint.EXACT_MATCH_REQUIRED,
      TripReferenceType.STANDARD
    );
  }

  /**
   * Create a trip reference with a trip ID and specified fuzzy matching hint.
   */
  public static TripReference ofTripId(FeedScopedId tripId, FuzzyMatchingHint fuzzyMatchingHint) {
    return new TripReference(
      tripId,
      null,
      null,
      null,
      null,
      null,
      null,
      fuzzyMatchingHint,
      TripReferenceType.STANDARD
    );
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

  @Nullable
  public String vehicleRef() {
    return vehicleRef;
  }

  @Nullable
  public String lineRef() {
    return lineRef;
  }

  public FuzzyMatchingHint fuzzyMatchingHint() {
    return fuzzyMatchingHint;
  }

  public TripReferenceType tripReferenceType() {
    return tripReferenceType;
  }

  /**
   * Returns true if this reference has a trip ID.
   */
  public boolean hasTripId() {
    return tripId != null;
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
      Objects.equals(routeId, that.routeId) &&
      Objects.equals(startTime, that.startTime) &&
      Objects.equals(startDate, that.startDate) &&
      direction == that.direction &&
      Objects.equals(vehicleRef, that.vehicleRef) &&
      Objects.equals(lineRef, that.lineRef) &&
      fuzzyMatchingHint == that.fuzzyMatchingHint &&
      tripReferenceType == that.tripReferenceType
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      tripId,
      routeId,
      startTime,
      startDate,
      direction,
      vehicleRef,
      lineRef,
      fuzzyMatchingHint,
      tripReferenceType
    );
  }

  @Override
  public String toString() {
    return (
      "TripReference{" +
      "tripId=" +
      tripId +
      ", routeId=" +
      routeId +
      ", startTime='" +
      startTime +
      '\'' +
      ", startDate=" +
      startDate +
      ", direction=" +
      direction +
      ", vehicleRef='" +
      vehicleRef +
      '\'' +
      ", lineRef='" +
      lineRef +
      '\'' +
      ", fuzzyMatchingHint=" +
      fuzzyMatchingHint +
      ", tripReferenceType=" +
      tripReferenceType +
      '}'
    );
  }

  /**
   * Builder for creating TripReference instances.
   */
  public static class Builder {

    private FeedScopedId tripId;
    private FeedScopedId routeId;
    private String startTime;
    private LocalDate startDate;
    private Direction direction;
    private String vehicleRef;
    private String lineRef;
    private FuzzyMatchingHint fuzzyMatchingHint = FuzzyMatchingHint.EXACT_MATCH_REQUIRED;
    private TripReferenceType tripReferenceType = TripReferenceType.STANDARD;

    public Builder withTripId(FeedScopedId tripId) {
      this.tripId = tripId;
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

    public Builder withVehicleRef(String vehicleRef) {
      this.vehicleRef = vehicleRef;
      return this;
    }

    public Builder withLineRef(String lineRef) {
      this.lineRef = lineRef;
      return this;
    }

    public Builder withFuzzyMatchingHint(FuzzyMatchingHint fuzzyMatchingHint) {
      this.fuzzyMatchingHint = fuzzyMatchingHint;
      return this;
    }

    public Builder withTripReferenceType(TripReferenceType tripReferenceType) {
      this.tripReferenceType = tripReferenceType;
      return this;
    }

    public TripReference build() {
      return new TripReference(
        tripId,
        routeId,
        startTime,
        startDate,
        direction,
        vehicleRef,
        lineRef,
        fuzzyMatchingHint,
        tripReferenceType
      );
    }
  }
}
