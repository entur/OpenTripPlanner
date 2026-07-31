package org.opentripplanner.updater.trip.model.change;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimesFactory;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.command.ModifyTrip;
import org.opentripplanner.updater.trip.policy.StopReplacementPolicy;

/**
 * The modification of the stop pattern of an existing trip: the trip is rerouted on
 * the requested service date.
 * <p>
 * The modification applies itself through {@link #apply}: the new pattern and its real-time
 * times are built from the resolved state.
 * {@link org.opentripplanner.updater.trip.service.TripModifier} drives it.
 */
public final class TripModification extends ExistingTripChange {

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

  public TripModification(
    ModifyTrip command,
    LocalDate serviceDate,
    Trip trip,
    TripPattern scheduledPattern,
    int serviceCode,
    List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates
  ) {
    super(command, serviceDate, trip, scheduledPattern, resolvedStopTimeUpdates);
    this.cancellation = command.isCancellation();
    this.extraJourney = command.isExtraJourney();
    this.serviceCode = serviceCode;
    validate();
  }

  /**
   * The preconditions of a modification of the stop pattern of an existing trip: at least two
   * calls, and - when the message carries SIRI extra calls - a non-extra call sequence that still
   * matches the original pattern.
   *
   * @throws UpdateException if the message cannot reroute the trip
   */
  private void validate() {
    if (stopTimeUpdates().size() < 2) {
      throw UpdateException.of(trip().getId(), UpdateErrorType.TOO_FEW_STOPS);
    }

    if (hasSiriExtraCalls()) {
      validateSiriExtraCalls();
    }
  }

  /**
   * The non-extra calls of a SIRI message with extra calls must still describe the original
   * pattern: same number of calls, each one matching the original stop according to the format's
   * {@link StopReplacementPolicy}.
   */
  private void validateSiriExtraCalls() {
    var tripId = trip().getId();
    var originalPattern = scheduledPattern();
    var stopTimeUpdates = stopTimeUpdates();

    long nonExtraCount = stopTimeUpdates
      .stream()
      .filter(u -> !u.isExtraCall())
      .count();
    if (nonExtraCount != originalPattern.numberOfStops()) {
      throw UpdateException.of(
        tripId,
        UpdateErrorType.INVALID_STOP_SEQUENCE,
        "%d non-extra calls but the original pattern has %d stops".formatted(
          nonExtraCount,
          originalPattern.numberOfStops()
        )
      );
    }

    var stopReplacement = formatPolicy().stopReplacement();
    int originalIndex = 0;
    for (int i = 0; i < stopTimeUpdates.size(); i++) {
      var stopUpdate = stopTimeUpdates.get(i);
      if (stopUpdate.isExtraCall()) {
        continue;
      }

      StopLocation updateStop = stopUpdate.stop();
      if (updateStop == null) {
        throw UpdateException.of(tripId, UpdateErrorType.UNKNOWN_STOP, i);
      }

      StopLocation originalStop = originalPattern.getStop(originalIndex);

      var validationResult = stopReplacement.check(originalStop, updateStop);
      if (validationResult != StopReplacementPolicy.Result.VALID) {
        throw UpdateException.of(
          tripId,
          UpdateErrorType.STOP_MISMATCH,
          i,
          "call at stop %s does not match the original stop %s (%s)".formatted(
            updateStop.getId(),
            originalStop.getId(),
            validationResult
          )
        );
      }

      originalIndex++;
    }
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

  private boolean hasSiriExtraCalls() {
    return stopTimeUpdates().stream().anyMatch(ResolvedStopTimeUpdate::isExtraCall);
  }

  @Override
  public String toString() {
    return (
      "TripModification{" +
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
