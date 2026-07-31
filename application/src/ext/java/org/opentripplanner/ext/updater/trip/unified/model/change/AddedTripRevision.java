package org.opentripplanner.ext.updater.trip.unified.model.change;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.ext.updater.trip.unified.model.command.AddTrip;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;

/**
 * The revision of a previously added real-time trip: the same trip is sent again as ADD_NEW_TRIP
 * after it has already been integrated in the transit model (a subsequent update to an extra
 * journey).
 * <p>
 * The revision applies itself through {@link #apply()}: the existing trip and pattern are
 * reused verbatim, only the trip times are rebuilt from the baseline times and the incoming call
 * data. {@link org.opentripplanner.ext.updater.trip.unified.service.AddedTripReviser} drives it.
 * <p>
 * The calls are applied to the pattern the trip already runs, <em>by position</em>, so a message
 * that does not describe that pattern call for call cannot be applied to it at all - see
 * {@link #validate()}. A format that rebuilds an added trip on every message never gets here, it
 * creates the trip anew instead; the
 * {@link org.opentripplanner.ext.updater.trip.unified.policy.RepeatedAdditionPolicy} decides which
 * it is.
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
    validate();
  }

  /**
   * The precondition of a revision: the calls are applied to the pattern the trip already runs, by
   * position, so the message has to call at every stop of that pattern and at no other. A
   * journey-level cancellation is exempt - it publishes the scheduled times and applies no calls at
   * all, so whatever calls it carries do not have to line up. Legacy decides the same way, and in
   * the same order, in {@code ModifiedTripBuilder.build}.
   *
   * @throws UpdateException if the message cannot revise the trip
   */
  private void validate() {
    if (isCancelledAtJourneyLevel()) {
      return;
    }

    var calls = stopTimeUpdates();
    int stopsInPattern = pattern.numberOfStops();
    if (calls.size() < stopsInPattern) {
      throw UpdateException.of(tripId(), UpdateErrorType.TOO_FEW_STOPS);
    }
    if (calls.size() > stopsInPattern) {
      throw UpdateException.of(tripId(), UpdateErrorType.TOO_MANY_STOPS);
    }

    // A call at a stop the transit model does not know is dropped before the calls are applied,
    // which would move every later call one position up the pattern. A format that rejects an
    // unknown stop says so here, before that can happen.
    if (formatPolicy().unknownStop().failOnUnknownStop()) {
      for (int i = 0; i < calls.size(); i++) {
        if (calls.get(i).referencedStop() == null) {
          throw UpdateException.of(tripId(), UpdateErrorType.UNKNOWN_STOP, i);
        }
      }
    }
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
    // The revision applies the calls to the pattern the trip already runs on, so that pattern is what
    // decides whether the trip still runs.
    if (isCancelled(pattern.getStopPattern())) {
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
