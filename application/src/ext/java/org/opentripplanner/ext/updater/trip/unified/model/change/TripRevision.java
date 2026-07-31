package org.opentripplanner.ext.updater.trip.unified.model.change;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.ext.updater.trip.unified.model.command.ReviseTrip;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;

/**
 * The revision of the real-time times of an existing scheduled trip: delays, changed times and
 * minor pattern adjustments such as replaced stops or pick/drop changes.
 * <p>
 * The revision applies itself through {@link #apply}: the trip keeps its scheduled times as
 * the baseline and the calls of the message are applied on top of them.
 * {@link org.opentripplanner.ext.updater.trip.unified.service.TripReviser} drives it.
 */
public final class TripRevision extends ExistingTripChange {

  /** The pattern the trip currently runs on, which may be a real-time modified pattern. */
  private final TripPattern pattern;

  /** The scheduled times of the trip, the baseline the real-time times are built from. */
  private final TripTimes scheduledTripTimes;

  public TripRevision(
    ReviseTrip command,
    LocalDate serviceDate,
    Trip trip,
    TripPattern pattern,
    TripPattern scheduledPattern,
    TripTimes scheduledTripTimes,
    List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates
  ) {
    super(command, serviceDate, trip, scheduledPattern, resolvedStopTimeUpdates);
    this.pattern = Objects.requireNonNull(pattern, "pattern must not be null");
    this.scheduledTripTimes = Objects.requireNonNull(
      scheduledTripTimes,
      "scheduledTripTimes must not be null"
    );
    validate();
  }

  /**
   * The precondition of an update to the times of an existing trip: a format that matches calls by
   * position (FULL_UPDATE) must send every call of the trip. Matching by stop sequence or id
   * (PARTIAL_UPDATE) puts no constraint on the calls. That a position-matched format must not
   * number its calls is an invariant of the {@link ReviseTrip} command itself.
   *
   * @throws UpdateException if the message cannot update the trip
   */
  private void validate() {
    // The exact-stop-count precondition only applies to position-based (FULL_UPDATE) matching.
    if (!formatPolicy().stopMatching().requiresExactStopCount()) {
      return;
    }

    var tripId = trip().getId();

    // The count is compared against the scheduled pattern, not the current real-time pattern,
    // because a revert update may send fewer stops than a previously modified pattern (e.g. after
    // removing an extra call).
    int scheduledStops = scheduledPattern().numberOfStops();
    if (stopTimeUpdates().size() < scheduledStops) {
      throw UpdateException.of(tripId, UpdateErrorType.TOO_FEW_STOPS);
    }
    if (stopTimeUpdates().size() > scheduledStops) {
      throw UpdateException.of(tripId, UpdateErrorType.TOO_MANY_STOPS);
    }
  }

  /**
   * Apply the real-time data to the trip as it is scheduled today: seed the trip times from the
   * scheduled ones, run the stop time updates over them, and settle on the pattern the trip ends up
   * running - the scheduled one unless the calls changed it.
   *
   * @param patternLookup finds the real-time pattern of a modified stop pattern, creating it if the
   *                      trip is the first to run it
   * @throws DataValidationException if the resulting trip times are invalid
   */
  public TripUpdateResult apply(ModifiedPatternLookup patternLookup) {
    var trip = trip();
    var scheduledPattern = scheduledPattern();
    var policy = formatPolicy();

    // Seed the builder with the times the calls of the message are applied on top of - the aimed
    // times for a format whose message describes the whole journey, no times at all for one where
    // an unreported time is a gap only a delay interpolator may fill.
    var builder = policy.unreportedTime().seedTimes(scheduledTripTimes);
    applyJourneyDescription(builder);

    // Apply the stop time updates, accumulating the resulting pattern changes.
    PatternModification modification = new StopTimeUpdateApplication(
      this,
      builder,
      scheduledPattern
    ).run();

    StopPattern updatedStopPattern = modification.hasPatternChanges()
      ? modification.applyTo(scheduledPattern)
      : scheduledPattern.getStopPattern();

    // The update may cancel the trip without saying so at journey level - whether the pattern the
    // calls leave behind means the trip does not run is the format's answer to give. The times
    // reported for a trip that does not run carry no meaning, so the cancellation starts over from
    // the scheduled times.
    if (policy.implicitCancellation().cancelsTrip(updatedStopPattern)) {
      return cancelTrip();
    }

    // Determine the pattern to use. After reverting, start with the scheduled pattern unless new
    // modifications are needed.
    TripPattern finalPattern = scheduledPattern;
    TripPattern patternToDeleteFrom = null;
    boolean patternChanged = false;

    if (modification.hasPatternChanges()) {
      // Compare against the scheduled pattern to determine if we need a modified pattern
      if (!scheduledPattern.getStopPattern().equals(updatedStopPattern)) {
        finalPattern = patternLookup.findOrCreate(updatedStopPattern, trip, scheduledPattern);
        patternChanged = true;
        patternToDeleteFrom = scheduledPattern;
      }
    }

    // Mark whatever the message states about the trip's real-time state on top of the times already
    // applied - which of them a format states, and whether a changed pattern is reported as MODIFIED
    // or UPDATED, is the format's answer to give.
    policy.realTimeState().mark(builder, patternChanged);

    // Create the RealTimeTripUpdate with revert and deletion signals
    var realTimeTripUpdate = RealTimeTripUpdate.of(finalPattern, builder.build(), serviceDate())
      .withProducer(dataSource())
      .withRevertPreviousRealTimeUpdates(true)
      .withHideTripInScheduledPattern(patternToDeleteFrom)
      .build();
    return new TripUpdateResult(realTimeTripUpdate);
  }

  /**
   * Cancel the trip on its scheduled pattern, keeping the scheduled times: a trip that does not run
   * has no real-time times worth publishing, and the trip is reverted onto its scheduled pattern
   * rather than left on one an earlier message modified.
   */
  private TripUpdateResult cancelTrip() {
    // Seeded from the scheduled times rather than through the format's UnreportedTimePolicy: a
    // cancelled trip has no times to interpolate, and a builder left empty for the interpolators
    // would not survive validation.
    var builder = scheduledTripTimes.createRealTimeFromScheduledTimes();
    applyJourneyDescription(builder);
    builder.withCanceled();
    var cancellation = RealTimeTripUpdate.of(scheduledPattern(), builder.build(), serviceDate())
      .withProducer(dataSource())
      .withRevertPreviousRealTimeUpdates(true)
      .build();
    return new TripUpdateResult(cancellation);
  }

  public TripPattern pattern() {
    return pattern;
  }

  /**
   * The scheduled times of the trip. They are the baseline the real-time times are built from, and
   * they know which {@code stop_sequence} the static feed numbered each call with.
   */
  TripTimes scheduledTripTimes() {
    return scheduledTripTimes;
  }

  @Override
  public String toString() {
    return (
      "TripRevision{" +
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
