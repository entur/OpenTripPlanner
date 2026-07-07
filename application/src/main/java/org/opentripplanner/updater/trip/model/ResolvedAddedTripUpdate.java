package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;

/**
 * Resolved data for an update to a previously added real-time trip: the same trip is sent again
 * as ADD_NEW_TRIP after it has already been integrated in the transit model.
 * <p>
 * Used by {@link org.opentripplanner.updater.trip.handlers.UpdateAddedTripHandler}.
 */
public final class ResolvedAddedTripUpdate extends ResolvedNewTrip {

  private final Trip trip;
  private final TripPattern pattern;
  private final TripTimes tripTimes;

  public ResolvedAddedTripUpdate(
    ParsedAddNewTrip parsedUpdate,
    LocalDate serviceDate,
    List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates,
    Trip trip,
    TripPattern pattern,
    TripTimes tripTimes
  ) {
    super(parsedUpdate, serviceDate, resolvedStopTimeUpdates);
    this.trip = Objects.requireNonNull(trip, "trip must not be null");
    this.pattern = Objects.requireNonNull(pattern, "pattern must not be null");
    this.tripTimes = Objects.requireNonNull(tripTimes, "tripTimes must not be null");
  }

  /** The previously added trip. */
  public Trip trip() {
    return trip;
  }

  /** The pattern the previously added trip runs on. */
  public TripPattern pattern() {
    return pattern;
  }

  /**
   * The baseline trip times for the added trip: the scheduled timetable entry when present
   * (SIRI-style added trips), otherwise the current real-time times (GTFS-RT added trips have
   * no scheduled timetable entry).
   */
  public TripTimes tripTimes() {
    return tripTimes;
  }

  @Override
  public String toString() {
    return (
      "ResolvedAddedTripUpdate{" +
      "trip=" +
      trip.getId() +
      ", serviceDate=" +
      serviceDate() +
      ", pattern=" +
      pattern.getId() +
      '}'
    );
  }
}
