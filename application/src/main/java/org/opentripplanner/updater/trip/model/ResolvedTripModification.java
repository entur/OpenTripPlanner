package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimesFactory;
import org.opentripplanner.updater.trip.NewStopPatternFactory;
import org.opentripplanner.updater.trip.StopTimeUpdates;
import org.opentripplanner.updater.trip.TripUpdateResult;

/**
 * Resolved data for a modification of the stop pattern of an existing trip: the trip is rerouted on
 * the requested service date.
 * <p>
 * The update owns its own application through {@link #apply}: the new pattern and its real-time
 * times are built from the resolved state.
 * {@link org.opentripplanner.updater.trip.TripModifier} drives it.
 */
public final class ResolvedTripModification extends ResolvedExistingTrip {

  /**
   * Whether the message cancels the journey as a whole (SIRI {@code isCancellation=true}).
   * {@link #apply} marks the trip as cancelled on the modified pattern (e.g. extra call with
   * cancellation).
   */
  private final boolean cancellation;

  /**
   * Whether the update is for a SIRI extra journey (ExtraJourney=true) that also carries extra
   * calls. {@link #apply} also marks the modified trip as added, mirroring the legacy
   * {@code ExtraCallTripBuilder}.
   */
  private final boolean extraJourney;

  /**
   * The service code of the calendar the trip runs on, resolved from the trip's service id. Needed
   * because the modified pattern rebuilds the trip times from scratch, and the new times must run
   * on the same calendar as the trip they replace.
   */
  private final int serviceCode;

  public ResolvedTripModification(
    TripModification parsedUpdate,
    LocalDate serviceDate,
    Trip trip,
    TripPattern scheduledPattern,
    int serviceCode,
    List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates
  ) {
    super(parsedUpdate, serviceDate, trip, scheduledPattern, resolvedStopTimeUpdates);
    this.cancellation = parsedUpdate.isCancellation();
    this.extraJourney = parsedUpdate.isExtraJourney();
    this.serviceCode = serviceCode;
  }

  /**
   * Reroute the trip on the requested service date: build the new pattern and its trip times from
   * the resolved state and the incoming call data, and return them as an update replacing the
   * trip's scheduled pattern.
   *
   * @param deduplicator       deduplicates the scheduled trip times built as the baseline for the
   *                           real-time times
   * @param patternIdGenerator generates the id of the new pattern from the trip - injects
   *                           {@code TripPatternCache#generatePatternId}
   * @throws DataValidationException if the resulting trip times are invalid
   */
  public TripUpdateResult apply(
    DeduplicatorService deduplicator,
    Function<Trip, FeedScopedId> patternIdGenerator
  ) {
    var trip = trip();
    var scheduledPattern = scheduledPattern();

    // Build the new stop pattern from stop time updates
    var stopTimesAndPattern = NewStopPatternFactory.buildNewStopPattern(
      trip,
      stopTimeUpdates(),
      formatPolicy().firstLastStopTime()
    );

    // Create scheduled trip times for the new pattern (used as baseline for real-time)
    var scheduledTimes = TripTimesFactory.tripTimes(
      trip,
      stopTimesAndPattern.stopTimes(),
      deduplicator
    ).withServiceCode(serviceCode);

    scheduledTimes.validateNonIncreasingTimes();

    // Create the new pattern - don't add scheduled times, only real-time times will be added
    TripPattern newPattern = TripPattern.of(patternIdGenerator.apply(trip))
      .withRoute(trip.getRoute())
      .withMode(trip.getMode())
      .withNetexSubmode(trip.getNetexSubMode())
      .withStopPattern(stopTimesAndPattern.stopPattern())
      .withRealTimeStopPatternModified()
      .withOriginalTripPattern(scheduledPattern)
      .build();

    // Create real-time trip times builder from scheduled
    var builder = scheduledTimes.createRealTimeFromScheduledTimes();
    applyJourneyDescription(builder);

    // Apply real-time updates
    StopTimeUpdates.applyRealTimeUpdates(builder, stopTimeUpdates());

    // Set state to MODIFIED (trip pattern was modified)
    builder.withModifiedTripPattern();

    // If this is a SIRI extra journey (ExtraJourney=true) that also carries extra calls, also mark
    // the trip as added: an extra journey is never part of the static schedule. Mirrors the legacy
    // ExtraCallTripBuilder, which sets added and modifiedTripPattern (and canceled) simultaneously.
    if (extraJourney) {
      builder.withAdded();
    }

    // If the SIRI message carries a trip-level cancellation flag (e.g. extra call + cancellation),
    // mark the trip as cancelled on the modified pattern.
    if (cancellation) {
      builder.withCanceled();
    }

    // Build and return the result with revert and deletion signals
    var realTimeTripUpdate = RealTimeTripUpdate.of(newPattern, builder.build(), serviceDate())
      .withProducer(dataSource())
      .withRevertPreviousRealTimeUpdates(true)
      .withHideTripInScheduledPattern(scheduledPattern)
      .build();
    return new TripUpdateResult(realTimeTripUpdate);
  }

  public boolean hasSiriExtraCalls() {
    return stopTimeUpdates().stream().anyMatch(ResolvedStopTimeUpdate::isExtraCall);
  }

  @Override
  public String toString() {
    return (
      "ResolvedTripModification{" +
      "serviceDate=" +
      serviceDate() +
      ", trip=" +
      trip().getId() +
      ", scheduledPattern=" +
      scheduledPattern().getId() +
      '}'
    );
  }
}
