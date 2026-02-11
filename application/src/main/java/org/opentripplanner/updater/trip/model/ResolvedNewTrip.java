package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;

/**
 * Resolved data for adding new trips or updating previously added trips.
 * <p>
 * Used by {@link org.opentripplanner.updater.trip.handlers.AddNewTripHandler}.
 * <p>
 * If {@link #existingTrip()} is non-null, this is an update to a previously added trip.
 * Otherwise, this is a new trip creation.
 */
public final class ResolvedNewTrip {

  private final ParsedTripUpdate parsedUpdate;
  private final LocalDate serviceDate;

  // For updates to existing added trips
  @Nullable
  private final Trip existingTrip;

  @Nullable
  private final TripPattern existingPattern;

  @Nullable
  private final TripTimes existingTripTimes;

  private ResolvedNewTrip(
    ParsedTripUpdate parsedUpdate,
    LocalDate serviceDate,
    @Nullable Trip existingTrip,
    @Nullable TripPattern existingPattern,
    @Nullable TripTimes existingTripTimes
  ) {
    this.parsedUpdate = Objects.requireNonNull(parsedUpdate, "parsedUpdate must not be null");
    this.serviceDate = Objects.requireNonNull(serviceDate, "serviceDate must not be null");
    this.existingTrip = existingTrip;
    this.existingPattern = existingPattern;
    this.existingTripTimes = existingTripTimes;
  }

  /**
   * Create for a brand new trip (no existing trip).
   */
  public static ResolvedNewTrip forNewTrip(ParsedTripUpdate parsedUpdate, LocalDate serviceDate) {
    return new ResolvedNewTrip(parsedUpdate, serviceDate, null, null, null);
  }

  /**
   * Create for updating an existing added trip.
   */
  public static ResolvedNewTrip forExistingAddedTrip(
    ParsedTripUpdate parsedUpdate,
    LocalDate serviceDate,
    Trip existingTrip,
    TripPattern existingPattern,
    TripTimes existingTripTimes
  ) {
    return new ResolvedNewTrip(
      parsedUpdate,
      serviceDate,
      Objects.requireNonNull(existingTrip),
      Objects.requireNonNull(existingPattern),
      Objects.requireNonNull(existingTripTimes)
    );
  }

  // ========== Resolved data accessors ==========

  public LocalDate serviceDate() {
    return serviceDate;
  }

  /**
   * Returns true if this is an update to an existing added trip.
   */
  public boolean isUpdateToExistingTrip() {
    return existingTrip != null;
  }

  /**
   * Returns true if this is a new trip creation.
   */
  public boolean isNewTrip() {
    return existingTrip == null;
  }

  /**
   * The existing trip if this is an update to a previously added trip.
   */
  @Nullable
  public Trip existingTrip() {
    return existingTrip;
  }

  /**
   * The existing pattern if this is an update to a previously added trip.
   */
  @Nullable
  public TripPattern existingPattern() {
    return existingPattern;
  }

  /**
   * The existing trip times if this is an update to a previously added trip.
   */
  @Nullable
  public TripTimes existingTripTimes() {
    return existingTripTimes;
  }

  // ========== Delegated accessors from parsedUpdate ==========

  public ParsedTripUpdate parsedUpdate() {
    return parsedUpdate;
  }

  public TripUpdateOptions options() {
    return parsedUpdate.options();
  }

  public List<ParsedStopTimeUpdate> stopTimeUpdates() {
    return parsedUpdate.stopTimeUpdates();
  }

  @Nullable
  public TripCreationInfo tripCreationInfo() {
    return parsedUpdate.tripCreationInfo();
  }

  @Nullable
  public String dataSource() {
    return parsedUpdate.dataSource();
  }

  @Override
  public String toString() {
    return (
      "ResolvedNewTrip{" +
      "serviceDate=" +
      serviceDate +
      ", isUpdateToExisting=" +
      isUpdateToExistingTrip() +
      ", existingTrip=" +
      (existingTrip != null ? existingTrip.getId() : "null") +
      '}'
    );
  }
}
