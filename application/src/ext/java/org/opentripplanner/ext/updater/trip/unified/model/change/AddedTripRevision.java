package org.opentripplanner.ext.updater.trip.unified.model.change;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.ext.updater.trip.unified.model.command.AddTrip;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.model.network.StopPattern;
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
 * The revision applies itself through {@link #apply}: the message is applied the way an ordinary
 * trip update is applied to a scheduled trip (see {@link TripRevision}), except that the baseline
 * is the pattern the trip was <em>added</em> to and the aimed times it was added with. The calls
 * can replace a stop with one in the same station, restrict or cancel boarding and alighting, and
 * cancel calls - a call that does not describe the added pattern at its position is rejected.
 * Legacy decides the same way: a repeat of an extra journey takes the ordinary trip-update path,
 * {@code ModifiedTripBuilder}.
 * {@link org.opentripplanner.ext.updater.trip.unified.service.AddedTripReviser} drives it.
 * <p>
 * A format that rebuilds an added trip on every message never gets here, it creates the trip anew
 * instead; the {@link org.opentripplanner.ext.updater.trip.unified.policy.RepeatedAdditionPolicy}
 * decides which it is.
 */
public final class AddedTripRevision extends TripAddition {

  /** The previously added trip. */
  private final Trip trip;

  /** The pattern the previously added trip was added to. */
  private final TripPattern pattern;

  /** The baseline times the real-time times are rebuilt from: the aimed times of the addition. */
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
   * The precondition of a revision: the calls are matched against the pattern the trip was added
   * to, so the message has to call at every stop of that pattern and at no other. A journey-level
   * cancellation is exempt - it publishes the aimed times and applies no calls at all, so whatever
   * calls it carries do not have to line up. Legacy decides the same way, and in the same order, in
   * {@code ModifiedTripBuilder.build}.
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
  }

  /**
   * Apply the real-time data to the trip as it was added: seed the trip times from the aimed times
   * of the addition, run the stop time updates over them, and settle on the pattern the trip ends
   * up running - the added one unless the calls changed it.
   *
   * @param patternLookup finds the real-time pattern of a modified stop pattern, creating it if the
   *                      trip is the first to run it
   * @throws DataValidationException if the resulting trip times are invalid
   */
  public TripUpdateResult apply(ModifiedPatternLookup patternLookup) {
    var policy = formatPolicy();

    // A journey-level cancellation of an already-added trip is a clean cancellation: keep the
    // aimed times and do not re-apply the real-time call data, so the previously applied
    // real-time flags are dropped (matching the legacy ModifiedTripBuilder.cancelTrip behaviour).
    if (isCancelledAtJourneyLevel()) {
      return cancelTrip();
    }

    var builder = policy.unreportedTime().seedTimes(tripTimes);
    applyJourneyDescription(builder);
    // Extra journeys always keep the "added" flag, even when their pattern is modified,
    // because they were never part of the static schedule.
    builder.withAdded();

    // Apply the stop time updates, accumulating the resulting pattern changes.
    PatternModification modification = new StopTimeUpdateApplication(
      builder,
      pattern,
      tripTimes,
      stopTimeUpdates(),
      policy,
      tripId()
    ).run();

    StopPattern updatedStopPattern = modification.hasPatternChanges()
      ? modification.applyTo(pattern)
      : pattern.getStopPattern();

    // The update may cancel the trip without saying so at journey level - whether the pattern the
    // calls leave behind means the trip does not run is the format's answer to give. The times
    // reported for a trip that does not run carry no meaning, so the cancellation starts over from
    // the aimed times.
    if (policy.implicitCancellation().cancelsTrip(updatedStopPattern)) {
      return cancelTrip();
    }

    // Determine the pattern to use. The accumulated changes may still resolve to the added stop
    // pattern (e.g. a cancelled call at an end that was never routable), so the pattern only
    // changes when the resulting stop pattern actually differs.
    TripPattern finalPattern = pattern;
    TripPattern patternToDeleteFrom = null;
    boolean patternChanged = false;

    if (!pattern.getStopPattern().equals(updatedStopPattern)) {
      finalPattern = patternLookup.findOrCreate(updatedStopPattern, trip, pattern);
      patternChanged = true;
      patternToDeleteFrom = pattern;
    }

    // Mark whatever the message states about the trip's real-time state on top of the times already
    // applied - which of them a format states, and whether a changed pattern is reported as MODIFIED
    // or UPDATED, is the format's answer to give.
    policy.realTimeState().mark(builder, patternChanged);

    // Neither the trip nor its route is created here - both already exist in the transit model.
    var realTimeTripUpdate = RealTimeTripUpdate.of(finalPattern, builder.build(), serviceDate())
      .withProducer(dataSource())
      .withRevertPreviousRealTimeUpdates(true)
      .withHideTripInScheduledPattern(patternToDeleteFrom)
      .build();

    return new TripUpdateResult(realTimeTripUpdate);
  }

  /**
   * Cancel the trip on the pattern it was added to, keeping the aimed times: a trip that does not
   * run has no real-time times worth publishing, and the trip is reverted onto its added pattern
   * rather than left on one an earlier message modified.
   */
  private TripUpdateResult cancelTrip() {
    var builder = tripTimes.createRealTimeFromScheduledTimes();
    applyJourneyDescription(builder);
    // Extra journeys always keep the "added" flag, even when all stops are cancelled,
    // because they were never part of the static schedule.
    builder.withAdded();
    builder.withCanceled();
    var cancellation = RealTimeTripUpdate.of(pattern, builder.build(), serviceDate())
      .withProducer(dataSource())
      .withRevertPreviousRealTimeUpdates(true)
      .build();
    return new TripUpdateResult(cancellation);
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
