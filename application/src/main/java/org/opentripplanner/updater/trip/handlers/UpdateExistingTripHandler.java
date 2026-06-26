package org.opentripplanner.updater.trip.handlers;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.updater.spi.DataValidationExceptionMapper;
import org.opentripplanner.updater.trip.model.PatternModification;
import org.opentripplanner.updater.trip.model.ResolvedExistingTrip;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles updates to existing trips (delay updates, time changes).
 * Maps to GTFS-RT SCHEDULED and SIRI-ET regular updates.
 * <p>
 * This handler receives a {@link ResolvedExistingTrip} with trip, pattern, and service date
 * already resolved. It orchestrates the apply: seed the builder, run the
 * {@link StopTimeUpdateApplication} command, and turn the resulting {@link PatternModification}
 * into the final pattern and real-time state.
 */
public class UpdateExistingTripHandler implements TripUpdateHandler.ForExistingTrip {

  private static final Logger LOG = LoggerFactory.getLogger(UpdateExistingTripHandler.class);

  private final TripPatternCache tripPatternCache;

  public UpdateExistingTripHandler(TripPatternCache tripPatternCache) {
    this.tripPatternCache = Objects.requireNonNull(tripPatternCache);
  }

  @Override
  public TripUpdateResult handle(ResolvedExistingTrip resolvedUpdate) {
    // All resolution already done by ExistingTripResolver
    Trip trip = resolvedUpdate.trip();
    TripPattern scheduledPattern = resolvedUpdate.scheduledPattern();
    TripTimes tripTimes = resolvedUpdate.scheduledTripTimes();
    LocalDate serviceDate = resolvedUpdate.serviceDate();
    var policy = resolvedUpdate.formatPolicy();

    LOG.debug(
      "Updating trip {} on pattern {} for date {}",
      trip.getId(),
      resolvedUpdate.pattern().getId(),
      serviceDate
    );

    // Seed the builder. With delay propagation enabled, start with empty times so interpolators
    // can fill them in; otherwise pre-fill with scheduled times (SIRI-style).
    var builder = policy.delayPropagation().initialBuilder(tripTimes);

    // If all stops are cancelled, treat as implicit trip-level cancellation (avoid MODIFIED state)
    if (resolvedUpdate.isAllStopsCancelled()) {
      builder.withCanceled();
      var realTimeTripUpdate = RealTimeTripUpdate.of(scheduledPattern, builder.build(), serviceDate)
        .withProducer(resolvedUpdate.dataSource())
        .withRevertPreviousRealTimeUpdates(true)
        .build();
      LOG.debug(
        "All stops cancelled - trip {} treated as cancelled on {}",
        trip.getId(),
        serviceDate
      );
      return new TripUpdateResult(realTimeTripUpdate);
    }

    // Apply the stop time updates, accumulating the resulting pattern changes.
    PatternModification modResult = new StopTimeUpdateApplication(
      resolvedUpdate,
      builder,
      scheduledPattern
    ).run();

    // Determine the pattern to use
    // After reverting, start with the scheduled pattern unless new modifications are needed
    TripPattern finalPattern = scheduledPattern;
    TripPattern patternToDeleteFrom = null;
    boolean patternChanged = false;

    // If stop pattern was modified, create or get cached modified pattern
    if (modResult.hasPatternChanges()) {
      StopPattern newStopPattern = buildModifiedStopPattern(
        scheduledPattern,
        modResult.stopReplacements(),
        modResult.pickupChanges(),
        modResult.dropoffChanges()
      );

      // Check if pattern actually changed (builder deduplicates)
      // Compare against the scheduled pattern to determine if we need a modified pattern
      if (!scheduledPattern.getStopPattern().equals(newStopPattern)) {
        finalPattern = tripPatternCache.getOrCreateTripPattern(newStopPattern, trip);
        patternChanged = true;
        patternToDeleteFrom = scheduledPattern;
      }
    }

    // Set real-time state if any updates were applied. The format's RealTimeStatePolicy decides
    // whether a pattern change is exposed as MODIFIED (SIRI-ET) or UPDATED (GTFS-RT).
    if (modResult.hasAnyUpdates()) {
      policy.realTimeState().mark(builder, patternChanged);
    }

    // Create the RealTimeTripUpdate with revert and deletion signals
    try {
      var realTimeTripUpdate = RealTimeTripUpdate.of(finalPattern, builder.build(), serviceDate)
        .withProducer(resolvedUpdate.dataSource())
        .withRevertPreviousRealTimeUpdates(true)
        .withHideTripInScheduledPattern(patternToDeleteFrom)
        .build();
      LOG.debug(
        "Updated trip {} on {} (patternChanged: {})",
        trip.getId(),
        serviceDate,
        patternChanged
      );
      return new TripUpdateResult(realTimeTripUpdate);
    } catch (DataValidationException e) {
      LOG.info(
        "Invalid real-time data for trip {} - TripTimes failed to validate. {}",
        trip.getId(),
        e.getMessage()
      );
      throw DataValidationExceptionMapper.map(e);
    }
  }

  /**
   * Build a modified stop pattern based on detected changes.
   * Uses StopPattern.StopPatternBuilder to apply stop replacements and pickup/dropoff changes.
   * The builder automatically deduplicates - if no actual changes, returns the original pattern.
   *
   * @param originalPattern The original scheduled pattern
   * @param stopReplacements Map of stop index to replacement stop
   * @param pickupChanges Map of stop index to new pickup type
   * @param dropoffChanges Map of stop index to new dropoff type
   * @return The modified stop pattern (may be same as original if builder deduplicates)
   */
  private StopPattern buildModifiedStopPattern(
    TripPattern originalPattern,
    Map<Integer, StopLocation> stopReplacements,
    Map<Integer, PickDrop> pickupChanges,
    Map<Integer, PickDrop> dropoffChanges
  ) {
    var builder = originalPattern.copyPlannedStopPattern();

    // Apply stop replacements
    if (!stopReplacements.isEmpty()) {
      builder.replaceStops(stopReplacements);
    }

    // Apply pickup changes
    if (!pickupChanges.isEmpty()) {
      builder.updatePickups(pickupChanges);
    }

    // Apply dropoff changes
    if (!dropoffChanges.isEmpty()) {
      builder.updateDropoffs(dropoffChanges);
    }

    return builder.build();
  }
}
