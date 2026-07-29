package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.ScheduledTripTimes;
import org.opentripplanner.transit.model.timetable.Trip;

/**
 * Resolved data for duplicating an existing scheduled trip.
 * <p>
 * Used by {@link org.opentripplanner.updater.trip.TripDuplicator}.
 */
public final class ResolvedDuplicateTrip {

  private final Trip originalTrip;
  private final TripPattern originalPattern;
  private final ScheduledTripTimes originalScheduledTimes;
  private final FeedScopedId serviceId;
  private final int serviceCode;
  private final LocalDate serviceDate;
  private final LocalTime newStartTime;

  public ResolvedDuplicateTrip(
    Trip originalTrip,
    TripPattern originalPattern,
    ScheduledTripTimes originalScheduledTimes,
    FeedScopedId serviceId,
    int serviceCode,
    LocalDate serviceDate,
    LocalTime newStartTime
  ) {
    this.originalTrip = Objects.requireNonNull(originalTrip, "originalTrip must not be null");
    this.originalPattern = Objects.requireNonNull(
      originalPattern,
      "originalPattern must not be null"
    );
    this.originalScheduledTimes = Objects.requireNonNull(
      originalScheduledTimes,
      "originalScheduledTimes must not be null"
    );
    this.serviceId = Objects.requireNonNull(serviceId, "serviceId must not be null");
    this.serviceCode = serviceCode;
    this.serviceDate = Objects.requireNonNull(serviceDate, "serviceDate must not be null");
    this.newStartTime = Objects.requireNonNull(newStartTime, "newStartTime must not be null");
  }

  // ========== Resolved data accessors ==========

  /** The scheduled trip to duplicate. */
  public Trip originalTrip() {
    return originalTrip;
  }

  /** The pattern of the original trip, which the duplicated trip is added to. */
  public TripPattern originalPattern() {
    return originalPattern;
  }

  /** The scheduled times of the original trip, the template for the duplicated trip. */
  public ScheduledTripTimes originalScheduledTimes() {
    return originalScheduledTimes;
  }

  /** The service id valid for the duplicated trip's service date. */
  public FeedScopedId serviceId() {
    return serviceId;
  }

  /** The service code corresponding to {@link #serviceId()}. */
  public int serviceCode() {
    return serviceCode;
  }

  /** The service date the duplicated trip runs on. */
  public LocalDate serviceDate() {
    return serviceDate;
  }

  /** The departure time from the first stop of the duplicated trip. */
  public LocalTime newStartTime() {
    return newStartTime;
  }

  @Override
  public String toString() {
    return (
      "ResolvedDuplicateTrip{" +
      "originalTrip=" +
      originalTrip.getId() +
      ", serviceDate=" +
      serviceDate +
      ", newStartTime=" +
      newStartTime +
      '}'
    );
  }
}
