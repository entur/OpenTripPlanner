package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.TripUpdateResult;

/**
 * The revision of the real-time times of an existing scheduled trip: delays, changed times and
 * minor pattern adjustments such as replaced stops or pick/drop changes.
 * <p>
 * The revision applies itself through {@link #apply}: the trip keeps its scheduled times as
 * the baseline and the calls of the message are applied on top of them.
 * {@link org.opentripplanner.updater.trip.TripReviser} drives it.
 */
public final class TripRevision extends ExistingTripChange {

  /** The pattern the trip currently runs on, which may be a real-time modified pattern. */
  private final TripPattern pattern;

  /** The scheduled times of the trip, the baseline the real-time times are built from. */
  private final TripTimes scheduledTripTimes;

  /**
   * Whether the calls of the message are numbered. A format that matches calls by position must not
   * number them.
   */
  private final boolean hasStopSequences;

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
    this.hasStopSequences = command.hasStopSequences();
    validate();
  }

  /**
   * The preconditions of an update to the times of an existing trip: a format that matches calls by
   * position (FULL_UPDATE) must send every call of the trip, and must not number them. Matching by
   * stop sequence or id (PARTIAL_UPDATE) puts no constraint on the calls.
   *
   * @throws UpdateException if the message cannot update the trip
   */
  private void validate() {
    // The exact-stop-count precondition only applies to position-based (FULL_UPDATE) matching.
    if (!formatPolicy().stopMatching().requiresExactStopCount()) {
      return;
    }

    var tripId = trip().getId();

    if (hasStopSequences) {
      throw UpdateException.of(tripId, UpdateErrorType.INVALID_STOP_SEQUENCE);
    }

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

    // Seed the builder. With delay propagation enabled, start with empty times so interpolators
    // can fill them in; otherwise pre-fill with scheduled times (SIRI-style).
    var builder = policy.delayPropagation().initialBuilder(scheduledTripTimes);
    applyJourneyDescription(builder);

    // If all stops are cancelled, treat as implicit trip-level cancellation (avoid MODIFIED state)
    if (isCancelledAtEveryStop()) {
      builder.withCanceled();
      var cancellation = RealTimeTripUpdate.of(scheduledPattern, builder.build(), serviceDate())
        .withProducer(dataSource())
        .withRevertPreviousRealTimeUpdates(true)
        .build();
      return new TripUpdateResult(cancellation);
    }

    // Apply the stop time updates, accumulating the resulting pattern changes.
    PatternModification modification = new StopTimeUpdateApplication(
      this,
      builder,
      scheduledPattern
    ).run();

    // Determine the pattern to use. After reverting, start with the scheduled pattern unless new
    // modifications are needed.
    TripPattern finalPattern = scheduledPattern;
    TripPattern patternToDeleteFrom = null;
    boolean patternChanged = false;

    if (modification.hasPatternChanges()) {
      StopPattern newStopPattern = modification.applyTo(scheduledPattern);

      // Compare against the scheduled pattern to determine if we need a modified pattern
      if (!scheduledPattern.getStopPattern().equals(newStopPattern)) {
        finalPattern = patternLookup.findOrCreate(newStopPattern, trip, scheduledPattern);
        patternChanged = true;
        patternToDeleteFrom = scheduledPattern;
      }
    }

    // Set real-time state if there were real-time changes (time updates, cancellations or pattern
    // changes). NO_DATA-only updates are excluded so a trip whose only change is NO_DATA stops stays
    // scheduled. The format's RealTimeStatePolicy decides whether a pattern change is exposed as
    // MODIFIED (SIRI-ET) or UPDATED (GTFS-RT).
    if (modification.hasRealTimeChanges()) {
      policy.realTimeState().mark(builder, patternChanged);
    }

    // Create the RealTimeTripUpdate with revert and deletion signals
    var realTimeTripUpdate = RealTimeTripUpdate.of(finalPattern, builder.build(), serviceDate())
      .withProducer(dataSource())
      .withRevertPreviousRealTimeUpdates(true)
      .withHideTripInScheduledPattern(patternToDeleteFrom)
      .build();
    return new TripUpdateResult(realTimeTripUpdate);
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

  /**
   * Whether every stop of the trip is cancelled/skipped, which cancels the trip implicitly. The
   * stop updates must cover the full pattern: a partial update only cancels the stops it mentions.
   */
  private boolean isCancelledAtEveryStop() {
    return (
      ResolvedStopTimeUpdate.allSkipped(stopTimeUpdates()) &&
      stopTimeUpdates().size() == pattern.numberOfStops()
    );
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
