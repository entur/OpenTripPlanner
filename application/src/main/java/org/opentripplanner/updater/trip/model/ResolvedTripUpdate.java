package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;
import org.opentripplanner.transit.model.timetable.TripTimes;

/**
 * A ParsedTripUpdate with all referenced data resolved from the transit model.
 * <p>
 * This class centralizes the resolution of:
 * <ul>
 *   <li>Service date (from explicit date, TripOnServiceDate, or aimed departure time)</li>
 *   <li>Trip (from trip ID or TripOnServiceDate)</li>
 *   <li>TripPattern (the pattern containing the trip)</li>
 *   <li>Scheduled pattern (original pattern if current is modified)</li>
 *   <li>Scheduled trip times (baseline for real-time updates)</li>
 *   <li>TripOnServiceDate (for dated vehicle journey lookups)</li>
 * </ul>
 * <p>
 * By resolving all data upfront, handlers can focus on their specific transformation
 * logic without duplicating resolution code.
 */
public final class ResolvedTripUpdate {

  private final ParsedTripUpdate parsedUpdate;
  private final LocalDate serviceDate;

  @Nullable
  private final Trip trip;

  @Nullable
  private final TripPattern pattern;

  @Nullable
  private final TripPattern scheduledPattern;

  @Nullable
  private final TripTimes scheduledTripTimes;

  @Nullable
  private final TripOnServiceDate tripOnServiceDate;

  /**
   * Create a resolved trip update with all resolved data.
   *
   * @param parsedUpdate The original parsed update
   * @param serviceDate The resolved service date (required)
   * @param trip The resolved trip (null for ADD_NEW_TRIP creating a new trip)
   * @param pattern The trip pattern (null for ADD_NEW_TRIP creating a new trip)
   * @param scheduledPattern The original scheduled pattern (same as pattern if not modified)
   * @param scheduledTripTimes The scheduled trip times for the trip
   * @param tripOnServiceDate The TripOnServiceDate if resolved from dated vehicle journey
   */
  public ResolvedTripUpdate(
    ParsedTripUpdate parsedUpdate,
    LocalDate serviceDate,
    @Nullable Trip trip,
    @Nullable TripPattern pattern,
    @Nullable TripPattern scheduledPattern,
    @Nullable TripTimes scheduledTripTimes,
    @Nullable TripOnServiceDate tripOnServiceDate
  ) {
    this.parsedUpdate = Objects.requireNonNull(parsedUpdate, "parsedUpdate must not be null");
    this.serviceDate = Objects.requireNonNull(serviceDate, "serviceDate must not be null");
    this.trip = trip;
    this.pattern = pattern;
    this.scheduledPattern = scheduledPattern;
    this.scheduledTripTimes = scheduledTripTimes;
    this.tripOnServiceDate = tripOnServiceDate;
  }

  /**
   * Create a resolved update for a new trip (ADD_NEW_TRIP with no existing trip).
   */
  public static ResolvedTripUpdate forNewTrip(
    ParsedTripUpdate parsedUpdate,
    LocalDate serviceDate
  ) {
    return new ResolvedTripUpdate(parsedUpdate, serviceDate, null, null, null, null, null);
  }

  /**
   * Create a resolved update for updating an existing added trip (previously added via real-time).
   */
  public static ResolvedTripUpdate forExistingAddedTrip(
    ParsedTripUpdate parsedUpdate,
    LocalDate serviceDate,
    Trip trip,
    TripPattern pattern,
    TripTimes tripTimes
  ) {
    return new ResolvedTripUpdate(
      parsedUpdate,
      serviceDate,
      trip,
      pattern,
      pattern,
      tripTimes,
      null
    );
  }

  /**
   * Create a resolved update for an existing scheduled trip.
   */
  public static ResolvedTripUpdate forExistingTrip(
    ParsedTripUpdate parsedUpdate,
    LocalDate serviceDate,
    Trip trip,
    TripPattern pattern,
    TripPattern scheduledPattern,
    TripTimes scheduledTripTimes,
    @Nullable TripOnServiceDate tripOnServiceDate
  ) {
    return new ResolvedTripUpdate(
      parsedUpdate,
      serviceDate,
      trip,
      pattern,
      scheduledPattern,
      scheduledTripTimes,
      tripOnServiceDate
    );
  }

  // ========== Accessors for resolved data ==========

  /**
   * The original parsed update.
   */
  public ParsedTripUpdate parsedUpdate() {
    return parsedUpdate;
  }

  /**
   * The resolved service date.
   */
  public LocalDate serviceDate() {
    return serviceDate;
  }

  /**
   * The resolved trip, or null for ADD_NEW_TRIP creating a new trip.
   */
  @Nullable
  public Trip trip() {
    return trip;
  }

  /**
   * The trip pattern, or null for ADD_NEW_TRIP creating a new trip.
   */
  @Nullable
  public TripPattern pattern() {
    return pattern;
  }

  /**
   * The original scheduled pattern.
   * If the current pattern is a real-time modified pattern, this returns the original.
   * Otherwise, returns the same as pattern().
   */
  @Nullable
  public TripPattern scheduledPattern() {
    return scheduledPattern;
  }

  /**
   * The scheduled trip times from the pattern's scheduled timetable.
   */
  @Nullable
  public TripTimes scheduledTripTimes() {
    return scheduledTripTimes;
  }

  /**
   * The TripOnServiceDate if resolved from a dated vehicle journey reference.
   */
  @Nullable
  public TripOnServiceDate tripOnServiceDate() {
    return tripOnServiceDate;
  }

  // ========== Convenience accessors delegating to parsedUpdate ==========

  /**
   * The type of update (modify existing, cancel, add new, etc.)
   */
  public TripUpdateType updateType() {
    return parsedUpdate.updateType();
  }

  /**
   * The trip reference from the original update.
   */
  public TripReference tripReference() {
    return parsedUpdate.tripReference();
  }

  /**
   * Processing options for this update.
   */
  public TripUpdateOptions options() {
    return parsedUpdate.options();
  }

  /**
   * Stop time updates from the original update.
   */
  public List<ParsedStopTimeUpdate> stopTimeUpdates() {
    return parsedUpdate.stopTimeUpdates();
  }

  /**
   * Trip creation info for ADD_NEW_TRIP updates.
   */
  @Nullable
  public TripCreationInfo tripCreationInfo() {
    return parsedUpdate.tripCreationInfo();
  }

  /**
   * Stop pattern modification info.
   */
  @Nullable
  public StopPatternModification stopPatternModification() {
    return parsedUpdate.stopPatternModification();
  }

  /**
   * The data source identifier.
   */
  @Nullable
  public String dataSource() {
    return parsedUpdate.dataSource();
  }

  // ========== Query methods ==========

  /**
   * Returns true if this update has a resolved trip (for updates to existing trips).
   */
  public boolean hasTrip() {
    return trip != null;
  }

  /**
   * Returns true if this update has a resolved pattern.
   */
  public boolean hasPattern() {
    return pattern != null;
  }

  /**
   * Returns true if this is an update to an existing added (real-time) trip
   * rather than a scheduled trip.
   */
  public boolean isUpdateToAddedTrip() {
    return (
      trip != null &&
      scheduledTripTimes != null &&
      scheduledTripTimes.getRealTimeState() ==
      org.opentripplanner.transit.model.timetable.RealTimeState.ADDED
    );
  }

  /**
   * Returns true if this update creates a new trip.
   */
  public boolean isNewTrip() {
    return parsedUpdate.isNewTrip() && trip == null;
  }

  /**
   * Returns true if this update cancels or deletes the trip.
   */
  public boolean isCancellation() {
    return parsedUpdate.isCancellation();
  }

  /**
   * Returns true if any stop time update has an explicit stop sequence number.
   */
  public boolean hasStopSequences() {
    return parsedUpdate.hasStopSequences();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ResolvedTripUpdate that = (ResolvedTripUpdate) o;
    return (
      Objects.equals(parsedUpdate, that.parsedUpdate) &&
      Objects.equals(serviceDate, that.serviceDate) &&
      Objects.equals(trip, that.trip) &&
      Objects.equals(pattern, that.pattern) &&
      Objects.equals(scheduledPattern, that.scheduledPattern) &&
      Objects.equals(scheduledTripTimes, that.scheduledTripTimes) &&
      Objects.equals(tripOnServiceDate, that.tripOnServiceDate)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      parsedUpdate,
      serviceDate,
      trip,
      pattern,
      scheduledPattern,
      scheduledTripTimes,
      tripOnServiceDate
    );
  }

  @Override
  public String toString() {
    return (
      "ResolvedTripUpdate{" +
      "updateType=" +
      updateType() +
      ", serviceDate=" +
      serviceDate +
      ", trip=" +
      (trip != null ? trip.getId() : "null") +
      ", pattern=" +
      (pattern != null ? pattern.getId() : "null") +
      ", scheduledPattern=" +
      (scheduledPattern != null ? scheduledPattern.getId() : "null") +
      ", tripOnServiceDate=" +
      (tripOnServiceDate != null ? tripOnServiceDate.getId() : "null") +
      '}'
    );
  }
}
