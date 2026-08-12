package org.opentripplanner.ext.updater.trip.unified.model.command;

import java.time.LocalDate;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.model.ServiceTime;
import org.opentripplanner.transit.model.timetable.Direction;

/**
 * Identifies a trip for real-time updates. May contain various combinations of identifiers
 * depending on what information is available in the real-time feed.
 */
public final class TripReference {

  @Nullable
  private final FeedScopedId statedTripId;

  @Nullable
  private final FeedScopedId previouslyAddedTripId;

  @Nullable
  private final FeedScopedId statedTripOnServiceDateId;

  @Nullable
  private final FeedScopedId previouslyAddedTripOnServiceDateId;

  @Nullable
  private final FeedScopedId routeId;

  @Nullable
  private final ServiceTime startTime;

  @Nullable
  private final LocalDate startDate;

  @Nullable
  private final Direction direction;

  @Nullable
  private final String internalPlanningCode;

  /**
   * @param statedTripId The trip ID the feed states outright (may be null if fuzzy matching by
   *                     route/time is used)
   * @param previouslyAddedTripId The ID an earlier real-time message added the trip under
   * @param statedTripOnServiceDateId The TripOnServiceDate ID (dated service journey ID) the feed
   *                                  states outright
   * @param previouslyAddedTripOnServiceDateId The TripOnServiceDate ID an earlier real-time
   *                                           message added the dated trip under
   * @param routeId The route ID (used for fuzzy matching when trip ID is ambiguous)
   * @param startTime The scheduled start time of the trip, relative to the service date's midnight
   * @param startDate The service date for the trip
   * @param direction The direction of travel (inbound/outbound)
   * @param internalPlanningCode The NeTEx internal planning code (from VehicleRef for RAIL trips)
   */
  private TripReference(
    @Nullable FeedScopedId statedTripId,
    @Nullable FeedScopedId previouslyAddedTripId,
    @Nullable FeedScopedId statedTripOnServiceDateId,
    @Nullable FeedScopedId previouslyAddedTripOnServiceDateId,
    @Nullable FeedScopedId routeId,
    @Nullable ServiceTime startTime,
    @Nullable LocalDate startDate,
    @Nullable Direction direction,
    @Nullable String internalPlanningCode
  ) {
    this.statedTripId = statedTripId;
    this.previouslyAddedTripId = previouslyAddedTripId;
    this.statedTripOnServiceDateId = statedTripOnServiceDateId;
    this.previouslyAddedTripOnServiceDateId = previouslyAddedTripOnServiceDateId;
    this.routeId = routeId;
    this.startTime = startTime;
    this.startDate = startDate;
    this.direction = direction;
    this.internalPlanningCode = internalPlanningCode;
  }

  /**
   * Create a trip reference with just a trip ID.
   */
  public static TripReference ofTripId(FeedScopedId tripId) {
    return new TripReference(tripId, null, null, null, null, null, null, null, null);
  }

  /**
   * Create a builder for constructing trip references with multiple fields.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * The trip id the feed states outright: the SIRI FramedVehicleJourneyRef or the GTFS-RT
   * {@code trip_id}. It names a trip the schedule is expected to know.
   */
  @Nullable
  public FeedScopedId statedTripId() {
    return statedTripId;
  }

  /**
   * The trip id an earlier real-time message added this journey under, taken from the SIRI
   * EstimatedVehicleJourneyCode. It names no scheduled trip, so it is only worth following once
   * the ids the schedule knows have come up empty.
   */
  @Nullable
  public FeedScopedId previouslyAddedTripId() {
    return previouslyAddedTripId;
  }

  /**
   * The trip id this reference names: the stated one, or the id of a previously added journey when
   * the feed states none.
   */
  @Nullable
  public FeedScopedId tripId() {
    return statedTripId != null ? statedTripId : previouslyAddedTripId;
  }

  /**
   * The dated trip id the feed states outright, taken from the SIRI DatedVehicleJourneyRef.
   */
  @Nullable
  public FeedScopedId statedTripOnServiceDateId() {
    return statedTripOnServiceDateId;
  }

  /**
   * The dated trip id an earlier real-time message added this journey under. It is the SIRI
   * EstimatedVehicleJourneyCode read as a dated journey, the form the addition registered it in.
   */
  @Nullable
  public FeedScopedId previouslyAddedTripOnServiceDateId() {
    return previouslyAddedTripOnServiceDateId;
  }

  /**
   * The dated trip id this reference names: the stated one, or the id of a previously added dated
   * journey when the feed states none.
   */
  @Nullable
  public FeedScopedId tripOnServiceDateId() {
    return statedTripOnServiceDateId != null
      ? statedTripOnServiceDateId
      : previouslyAddedTripOnServiceDateId;
  }

  @Nullable
  public FeedScopedId routeId() {
    return routeId;
  }

  @Nullable
  public ServiceTime startTime() {
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
    return tripId() != null;
  }

  /**
   * Returns true if this reference has the trip ID of a previously added journey.
   */
  public boolean hasPreviouslyAddedTripId() {
    return previouslyAddedTripId != null;
  }

  /**
   * Returns true if this reference has a TripOnServiceDate ID.
   */
  public boolean hasTripOnServiceDateId() {
    return tripOnServiceDateId() != null;
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

  /**
   * Returns true if the feed said something about the direction of travel.
   * <p>
   * Absent and {@link Direction#UNKNOWN} are different answers: absent means the feed left the
   * field out, so the direction is not known at all, while {@code UNKNOWN} means the feed sent a
   * direction the mapping does not recognise. A matcher may only fall back on the schedule for the
   * former.
   */
  public boolean hasDirection() {
    return direction != null;
  }

  @Nullable
  public String internalPlanningCode() {
    return internalPlanningCode;
  }

  /**
   * Returns true if this reference has an internal planning code (from VehicleRef for RAIL trips).
   */
  public boolean hasInternalPlanningCode() {
    return internalPlanningCode != null;
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
      Objects.equals(statedTripId, that.statedTripId) &&
      Objects.equals(previouslyAddedTripId, that.previouslyAddedTripId) &&
      Objects.equals(statedTripOnServiceDateId, that.statedTripOnServiceDateId) &&
      Objects.equals(previouslyAddedTripOnServiceDateId, that.previouslyAddedTripOnServiceDateId) &&
      Objects.equals(routeId, that.routeId) &&
      Objects.equals(startTime, that.startTime) &&
      Objects.equals(startDate, that.startDate) &&
      direction == that.direction &&
      Objects.equals(internalPlanningCode, that.internalPlanningCode)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      statedTripId,
      previouslyAddedTripId,
      statedTripOnServiceDateId,
      previouslyAddedTripOnServiceDateId,
      routeId,
      startTime,
      startDate,
      direction,
      internalPlanningCode
    );
  }

  @Override
  public String toString() {
    return (
      "TripReference{" +
      "statedTripId=" +
      statedTripId +
      ", previouslyAddedTripId=" +
      previouslyAddedTripId +
      ", statedTripOnServiceDateId=" +
      statedTripOnServiceDateId +
      ", previouslyAddedTripOnServiceDateId=" +
      previouslyAddedTripOnServiceDateId +
      ", routeId=" +
      routeId +
      ", startTime=" +
      startTime +
      ", startDate=" +
      startDate +
      ", direction=" +
      direction +
      ", internalPlanningCode='" +
      internalPlanningCode +
      '\'' +
      '}'
    );
  }

  /**
   * Builder for creating TripReference instances.
   */
  public static class Builder {

    private FeedScopedId statedTripId;
    private FeedScopedId previouslyAddedTripId;
    private FeedScopedId statedTripOnServiceDateId;
    private FeedScopedId previouslyAddedTripOnServiceDateId;
    private FeedScopedId routeId;
    private ServiceTime startTime;
    private LocalDate startDate;
    private Direction direction;
    private String internalPlanningCode;

    public Builder withTripId(FeedScopedId tripId) {
      this.statedTripId = tripId;
      return this;
    }

    public Builder withPreviouslyAddedTripId(FeedScopedId previouslyAddedTripId) {
      this.previouslyAddedTripId = previouslyAddedTripId;
      return this;
    }

    public Builder withTripOnServiceDateId(FeedScopedId tripOnServiceDateId) {
      this.statedTripOnServiceDateId = tripOnServiceDateId;
      return this;
    }

    public Builder withPreviouslyAddedTripOnServiceDateId(
      FeedScopedId previouslyAddedTripOnServiceDateId
    ) {
      this.previouslyAddedTripOnServiceDateId = previouslyAddedTripOnServiceDateId;
      return this;
    }

    public Builder withRouteId(FeedScopedId routeId) {
      this.routeId = routeId;
      return this;
    }

    public Builder withStartTime(ServiceTime startTime) {
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

    public Builder withInternalPlanningCode(String internalPlanningCode) {
      this.internalPlanningCode = internalPlanningCode;
      return this;
    }

    public TripReference build() {
      return new TripReference(
        statedTripId,
        previouslyAddedTripId,
        statedTripOnServiceDateId,
        previouslyAddedTripOnServiceDateId,
        routeId,
        startTime,
        startDate,
        direction,
        internalPlanningCode
      );
    }
  }
}
