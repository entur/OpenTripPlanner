package org.opentripplanner.updater.trip.model.change;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.updater.trip.model.command.AddTrip;

/**
 * The revision of a previously added real-time trip: the same trip is sent again as ADD_NEW_TRIP
 * after it has already been integrated in the transit model (a subsequent update to an extra
 * journey).
 * <p>
 * The revision applies itself through {@link #apply()}: the existing trip and pattern are
 * reused verbatim, only the trip times are rebuilt from the baseline times and the incoming call
 * data. {@link org.opentripplanner.updater.trip.service.AddedTripReviser} drives it.
 */
public final class AddedTripRevision extends TripAddition {

  /** The previously added trip. */
  private final Trip trip;

  /** The pattern the previously added trip runs on. */
  private final TripPattern pattern;

  /** The baseline times the real-time times are rebuilt from. */
  private final TripTimes tripTimes;

  public AddedTripRevision(
    AddTrip command,
    LocalDate serviceDate,
    List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates,
    Trip trip,
    TripPattern pattern,
    TripTimes tripTimes
  ) {
    super(command, serviceDate, resolvedStopTimeUpdates);
    this.trip = Objects.requireNonNull(trip, "trip must not be null");
    this.pattern = Objects.requireNonNull(pattern, "pattern must not be null");
    this.tripTimes = Objects.requireNonNull(tripTimes, "tripTimes must not be null");
  }

  /**
   * Rebuild the real-time trip times of the previously added trip from the baseline times and the
   * incoming call data, and return them as an update to the existing trip and pattern.
   *
   * @throws DataValidationException if the resulting trip times are invalid
   */
  public TripUpdateResult apply() {
    StopTimeUpdates.FilteredStopTimeUpdates calls = stopTimeUpdatesWithKnownStops();

    var builder = tripTimes.createRealTimeFromScheduledTimes();
    applyJourneyDescription(builder);
    // A journey-level cancellation of an already-added trip is a clean cancellation: keep the
    // scheduled times and do not re-apply the real-time call data, so the previously applied
    // real-time flags are dropped (matching the legacy ModifiedTripBuilder.cancelTrip behaviour).
    if (!isCancelledAtJourneyLevel()) {
      StopTimeUpdates.applyRealTimeUpdates(builder, calls.updates());
    }
    // Extra journeys always keep the "added" flag, even when all stops are cancelled,
    // because they were never part of the static schedule.
    builder.withAdded();
    if (isCancelled()) {
      builder.withCanceled();
    }

    // Neither the trip nor its route is created here - both already exist in the transit model.
    var realTimeTripUpdate = RealTimeTripUpdate.of(pattern, builder.build(), serviceDate())
      .withProducer(dataSource())
      .withRevertPreviousRealTimeUpdates(true)
      .build();

    return new TripUpdateResult(realTimeTripUpdate, calls.warnings());
  }

  @Override
  public String toString() {
    return (
      "AddedTripRevision{" +
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
