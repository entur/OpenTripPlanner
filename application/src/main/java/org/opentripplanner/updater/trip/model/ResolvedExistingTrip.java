package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;

/**
 * Resolved data for updates to existing scheduled trips.
 * <p>
 * Used by {@link org.opentripplanner.updater.trip.handlers.UpdateExistingTripHandler}
 * and {@link org.opentripplanner.updater.trip.handlers.ModifyTripHandler}.
 */
public final class ResolvedExistingTrip {

  private final TripUpdateOptions options;

  @Nullable
  private final TripCreationInfo tripCreationInfo;

  @Nullable
  private final String dataSource;

  private final boolean hasStopSequences;

  private final LocalDate serviceDate;
  private final Trip trip;
  private final TripPattern pattern;
  private final TripPattern scheduledPattern;
  private final TripTimes scheduledTripTimes;
  private final List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates;

  public ResolvedExistingTrip(
    ParsedExistingTripUpdate parsedUpdate,
    LocalDate serviceDate,
    Trip trip,
    TripPattern pattern,
    TripPattern scheduledPattern,
    TripTimes scheduledTripTimes,
    List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates
  ) {
    this.options = parsedUpdate.options();
    this.tripCreationInfo = parsedUpdate.tripCreationInfo();
    this.dataSource = parsedUpdate.dataSource();
    this.hasStopSequences = parsedUpdate.hasStopSequences();
    this.serviceDate = Objects.requireNonNull(serviceDate, "serviceDate must not be null");
    this.trip = Objects.requireNonNull(trip, "trip must not be null");
    this.pattern = Objects.requireNonNull(pattern, "pattern must not be null");
    this.scheduledPattern = Objects.requireNonNull(
      scheduledPattern,
      "scheduledPattern must not be null"
    );
    this.scheduledTripTimes = Objects.requireNonNull(
      scheduledTripTimes,
      "scheduledTripTimes must not be null"
    );
    this.resolvedStopTimeUpdates = Objects.requireNonNull(
      resolvedStopTimeUpdates,
      "resolvedStopTimeUpdates must not be null"
    );
  }

  // ========== Resolved data accessors ==========

  public LocalDate serviceDate() {
    return serviceDate;
  }

  public Trip trip() {
    return trip;
  }

  public TripPattern pattern() {
    return pattern;
  }

  /**
   * The original scheduled pattern.
   * If the current pattern is a real-time modified pattern, this returns the original.
   * Otherwise, returns the same as pattern().
   */
  public TripPattern scheduledPattern() {
    return scheduledPattern;
  }

  public TripTimes scheduledTripTimes() {
    return scheduledTripTimes;
  }

  public TripUpdateOptions options() {
    return options;
  }

  public List<ResolvedStopTimeUpdate> stopTimeUpdates() {
    return resolvedStopTimeUpdates;
  }

  /**
   * Returns true if every stop in the update is cancelled/skipped and the number of
   * stop updates covers the full pattern. When true, the trip should be treated as
   * implicitly cancelled at the trip level.
   */
  public boolean isAllStopsCancelled() {
    return (
      ResolvedStopTimeUpdate.allSkipped(resolvedStopTimeUpdates) &&
      resolvedStopTimeUpdates.size() == pattern.numberOfStops()
    );
  }

  public boolean hasSiriExtraCalls() {
    return resolvedStopTimeUpdates.stream().anyMatch(ResolvedStopTimeUpdate::isExtraCall);
  }

  @Nullable
  public TripCreationInfo tripCreationInfo() {
    return tripCreationInfo;
  }

  @Nullable
  public String dataSource() {
    return dataSource;
  }

  public boolean hasStopSequences() {
    return hasStopSequences;
  }

  @Override
  public String toString() {
    return (
      "ResolvedExistingTrip{" +
      "serviceDate=" +
      serviceDate +
      ", trip=" +
      trip.getId() +
      ", pattern=" +
      pattern.getId() +
      ", scheduledPattern=" +
      scheduledPattern.getId() +
      '}'
    );
  }
}
