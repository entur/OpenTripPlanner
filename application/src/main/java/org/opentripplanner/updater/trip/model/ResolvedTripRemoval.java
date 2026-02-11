package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;

/**
 * Resolved data for cancelling or deleting trips.
 * <p>
 * Used by {@link org.opentripplanner.updater.trip.handlers.CancelTripHandler}
 * and {@link org.opentripplanner.updater.trip.handlers.DeleteTripHandler}.
 * <p>
 * If trip is null, the handler should check for previously added trips in the snapshot manager.
 */
public final class ResolvedTripRemoval {

  private final ParsedTripUpdate parsedUpdate;
  private final LocalDate serviceDate;
  private final FeedScopedId tripId;

  // From scheduled data (may be null if trip not found in schedule)
  @Nullable
  private final Trip scheduledTrip;

  @Nullable
  private final TripPattern scheduledPattern;

  @Nullable
  private final TripTimes scheduledTripTimes;

  private ResolvedTripRemoval(
    ParsedTripUpdate parsedUpdate,
    LocalDate serviceDate,
    FeedScopedId tripId,
    @Nullable Trip scheduledTrip,
    @Nullable TripPattern scheduledPattern,
    @Nullable TripTimes scheduledTripTimes
  ) {
    this.parsedUpdate = Objects.requireNonNull(parsedUpdate, "parsedUpdate must not be null");
    this.serviceDate = Objects.requireNonNull(serviceDate, "serviceDate must not be null");
    this.tripId = tripId;
    this.scheduledTrip = scheduledTrip;
    this.scheduledPattern = scheduledPattern;
    this.scheduledTripTimes = scheduledTripTimes;
  }

  /**
   * Create for a trip that was not found in the scheduled data.
   * The handler will check for previously added trips.
   */
  public static ResolvedTripRemoval notFoundInSchedule(
    ParsedTripUpdate parsedUpdate,
    LocalDate serviceDate,
    FeedScopedId tripId
  ) {
    return new ResolvedTripRemoval(parsedUpdate, serviceDate, tripId, null, null, null);
  }

  /**
   * Create for a scheduled trip that was found.
   */
  public static ResolvedTripRemoval forScheduledTrip(
    ParsedTripUpdate parsedUpdate,
    LocalDate serviceDate,
    Trip trip,
    TripPattern pattern,
    TripTimes tripTimes
  ) {
    return new ResolvedTripRemoval(
      parsedUpdate,
      serviceDate,
      trip.getId(),
      Objects.requireNonNull(trip),
      Objects.requireNonNull(pattern),
      Objects.requireNonNull(tripTimes)
    );
  }

  // ========== Resolved data accessors ==========

  public LocalDate serviceDate() {
    return serviceDate;
  }

  /**
   * The trip ID from the update.
   */
  @Nullable
  public FeedScopedId tripId() {
    return tripId;
  }

  /**
   * Returns true if a scheduled trip was found.
   */
  public boolean hasScheduledTrip() {
    return scheduledTrip != null;
  }

  /**
   * The scheduled trip, or null if not found in schedule.
   */
  @Nullable
  public Trip scheduledTrip() {
    return scheduledTrip;
  }

  /**
   * The scheduled pattern, or null if trip not found.
   */
  @Nullable
  public TripPattern scheduledPattern() {
    return scheduledPattern;
  }

  /**
   * The scheduled trip times, or null if trip not found.
   */
  @Nullable
  public TripTimes scheduledTripTimes() {
    return scheduledTripTimes;
  }

  // ========== Delegated accessors from parsedUpdate ==========

  public TripUpdateType updateType() {
    return parsedUpdate.updateType();
  }

  public TripReference tripReference() {
    return parsedUpdate.tripReference();
  }

  @Override
  public String toString() {
    return (
      "ResolvedTripRemoval{" +
      "updateType=" +
      updateType() +
      ", serviceDate=" +
      serviceDate +
      ", tripId=" +
      tripId +
      ", hasScheduledTrip=" +
      hasScheduledTrip() +
      '}'
    );
  }
}
