package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;

/**
 * Resolved data for an update to the real-time times of an existing scheduled trip: delays, changed
 * times and minor pattern adjustments such as replaced stops or pick/drop changes.
 * <p>
 * {@link org.opentripplanner.updater.trip.ScheduledTripUpdater} applies it.
 */
public final class ResolvedScheduledTripUpdate extends ResolvedExistingTrip {

  /** The pattern the trip currently runs on, which may be a real-time modified pattern. */
  private final TripPattern pattern;

  /** The scheduled times of the trip, the baseline the real-time times are built from. */
  private final TripTimes scheduledTripTimes;

  /**
   * Whether the calls of the message are numbered. A format that matches calls by position must not
   * number them.
   */
  private final boolean hasStopSequences;

  public ResolvedScheduledTripUpdate(
    ScheduledTripUpdate parsedUpdate,
    LocalDate serviceDate,
    Trip trip,
    TripPattern pattern,
    TripPattern scheduledPattern,
    TripTimes scheduledTripTimes,
    List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates
  ) {
    super(parsedUpdate, serviceDate, trip, scheduledPattern, resolvedStopTimeUpdates);
    this.pattern = Objects.requireNonNull(pattern, "pattern must not be null");
    this.scheduledTripTimes = Objects.requireNonNull(
      scheduledTripTimes,
      "scheduledTripTimes must not be null"
    );
    this.hasStopSequences = parsedUpdate.hasStopSequences();
  }

  public TripPattern pattern() {
    return pattern;
  }

  public TripTimes scheduledTripTimes() {
    return scheduledTripTimes;
  }

  public boolean hasStopSequences() {
    return hasStopSequences;
  }

  /**
   * Whether every stop of the trip is cancelled/skipped, which cancels the trip implicitly. The
   * stop updates must cover the full pattern: a partial update only cancels the stops it mentions.
   */
  public boolean isCancelledAtEveryStop() {
    return (
      ResolvedStopTimeUpdate.allSkipped(stopTimeUpdates()) &&
      stopTimeUpdates().size() == pattern.numberOfStops()
    );
  }

  @Override
  public String toString() {
    return (
      "ResolvedScheduledTripUpdate{" +
      "serviceDate=" +
      serviceDate() +
      ", trip=" +
      trip().getId() +
      ", pattern=" +
      pattern.getId() +
      ", scheduledPattern=" +
      scheduledPattern().getId() +
      '}'
    );
  }
}
