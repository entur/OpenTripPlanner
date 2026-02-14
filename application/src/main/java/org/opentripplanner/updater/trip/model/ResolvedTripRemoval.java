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

  private final LocalDate serviceDate;
  private final FeedScopedId tripId;

  // From scheduled data (may be null if trip not found in schedule)
  @Nullable
  private final Trip scheduledTrip;

  @Nullable
  private final TripPattern scheduledPattern;

  @Nullable
  private final TripTimes scheduledTripTimes;

  @Nullable
  private final String dataSource;

  private ResolvedTripRemoval(
    LocalDate serviceDate,
    FeedScopedId tripId,
    @Nullable Trip scheduledTrip,
    @Nullable TripPattern scheduledPattern,
    @Nullable TripTimes scheduledTripTimes,
    @Nullable String dataSource
  ) {
    this.serviceDate = Objects.requireNonNull(serviceDate, "serviceDate must not be null");
    this.tripId = tripId;
    this.scheduledTrip = scheduledTrip;
    this.scheduledPattern = scheduledPattern;
    this.scheduledTripTimes = scheduledTripTimes;
    this.dataSource = dataSource;
  }

  /**
   * Create for a trip that was not found in the scheduled data.
   * The handler will check for previously added trips.
   */
  public static ResolvedTripRemoval notFoundInSchedule(
    LocalDate serviceDate,
    FeedScopedId tripId,
    @Nullable String dataSource
  ) {
    return new ResolvedTripRemoval(serviceDate, tripId, null, null, null, dataSource);
  }

  /**
   * Create for a scheduled trip that was found.
   */
  public static ResolvedTripRemoval forScheduledTrip(
    LocalDate serviceDate,
    Trip trip,
    TripPattern pattern,
    TripTimes tripTimes,
    @Nullable String dataSource
  ) {
    return new ResolvedTripRemoval(
      serviceDate,
      trip.getId(),
      Objects.requireNonNull(trip),
      Objects.requireNonNull(pattern),
      Objects.requireNonNull(tripTimes),
      dataSource
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

  /**
   * The data source / producer of the real-time update.
   */
  @Nullable
  public String dataSource() {
    return dataSource;
  }

  @Override
  public String toString() {
    return (
      "ResolvedTripRemoval{" +
      "serviceDate=" +
      serviceDate +
      ", tripId=" +
      tripId +
      ", scheduledTrip=" +
      (scheduledTrip != null ? scheduledTrip.getId() : "null") +
      '}'
    );
  }
}
