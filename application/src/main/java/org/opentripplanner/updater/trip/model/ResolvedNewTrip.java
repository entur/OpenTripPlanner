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

  private final TripUpdateOptions options;

  @Nullable
  private final TripCreationInfo tripCreationInfo;

  @Nullable
  private final String dataSource;

  private final LocalDate serviceDate;
  private final List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates;

  // For updates to existing added trips
  @Nullable
  private final Trip existingTrip;

  @Nullable
  private final TripPattern existingPattern;

  @Nullable
  private final TripTimes existingTripTimes;

  private ResolvedNewTrip(
    TripUpdateOptions options,
    @Nullable TripCreationInfo tripCreationInfo,
    @Nullable String dataSource,
    LocalDate serviceDate,
    List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates,
    @Nullable Trip existingTrip,
    @Nullable TripPattern existingPattern,
    @Nullable TripTimes existingTripTimes
  ) {
    this.options = Objects.requireNonNull(options, "options must not be null");
    this.tripCreationInfo = tripCreationInfo;
    this.dataSource = dataSource;
    this.serviceDate = Objects.requireNonNull(serviceDate, "serviceDate must not be null");
    this.resolvedStopTimeUpdates = Objects.requireNonNull(
      resolvedStopTimeUpdates,
      "resolvedStopTimeUpdates must not be null"
    );
    this.existingTrip = existingTrip;
    this.existingPattern = existingPattern;
    this.existingTripTimes = existingTripTimes;
  }

  /**
   * Create for a brand new trip (no existing trip).
   */
  public static ResolvedNewTrip forNewTrip(
    ParsedAddNewTrip parsedUpdate,
    LocalDate serviceDate,
    List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates
  ) {
    return new ResolvedNewTrip(
      parsedUpdate.options(),
      parsedUpdate.tripCreationInfo(),
      parsedUpdate.dataSource(),
      serviceDate,
      resolvedStopTimeUpdates,
      null,
      null,
      null
    );
  }

  /**
   * Create for updating an existing added trip.
   */
  public static ResolvedNewTrip forExistingAddedTrip(
    ParsedAddNewTrip parsedUpdate,
    LocalDate serviceDate,
    List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates,
    Trip existingTrip,
    TripPattern existingPattern,
    TripTimes existingTripTimes
  ) {
    return new ResolvedNewTrip(
      parsedUpdate.options(),
      parsedUpdate.tripCreationInfo(),
      parsedUpdate.dataSource(),
      serviceDate,
      resolvedStopTimeUpdates,
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
   * Returns true if every stop in the update is cancelled/skipped.
   * When true, the trip should be treated as implicitly cancelled at the trip level.
   */
  public boolean isAllStopsCancelled() {
    return ResolvedStopTimeUpdate.allSkipped(resolvedStopTimeUpdates);
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

  public TripUpdateOptions options() {
    return options;
  }

  public List<ResolvedStopTimeUpdate> stopTimeUpdates() {
    return resolvedStopTimeUpdates;
  }

  @Nullable
  public TripCreationInfo tripCreationInfo() {
    return tripCreationInfo;
  }

  @Nullable
  public String dataSource() {
    return dataSource;
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
