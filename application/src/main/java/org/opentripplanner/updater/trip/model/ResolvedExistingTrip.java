package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.model.timetable.TripTimesFactory;
import org.opentripplanner.updater.trip.NewStopPatternFactory;
import org.opentripplanner.updater.trip.StopTimeUpdates;
import org.opentripplanner.updater.trip.TripUpdateResult;
import org.opentripplanner.updater.trip.policy.FormatPolicy;

/**
 * Resolved data for updates to existing scheduled trips.
 * <p>
 * Used by {@link org.opentripplanner.updater.trip.ScheduledTripUpdater}
 * and {@link org.opentripplanner.updater.trip.TripModifier}. A modification (rerouting the trip
 * on the requested service date) owns its own application through {@link #applyModification}:
 * the new pattern and its real-time times are built from the resolved state.
 */
public final class ResolvedExistingTrip {

  private final FormatPolicy formatPolicy;

  @Nullable
  private final String dataSource;

  private final VehicleDescription vehicleDescription;

  @Nullable
  private final I18NString tripHeadsign;

  private final boolean hasStopSequences;

  /**
   * Whether the message cancels the journey as a whole (SIRI {@code isCancellation=true}).
   * {@link #applyModification} marks the trip as cancelled on the modified pattern
   * (e.g. extra call with cancellation).
   */
  private final boolean cancellation;

  /**
   * Whether the update is for a SIRI extra journey (ExtraJourney=true) that also carries extra
   * calls. {@link #applyModification} also marks the modified trip as added, mirroring the legacy
   * {@code ExtraCallTripBuilder}.
   */
  private final boolean extraJourney;

  private final LocalDate serviceDate;
  private final Trip trip;
  private final TripPattern pattern;
  private final TripPattern scheduledPattern;
  private final TripTimes scheduledTripTimes;

  /**
   * The service code of the calendar the trip runs on, resolved from the trip's service id. Needed
   * when a modified pattern rebuilds the trip times from scratch, since the new times must run on
   * the same calendar as the trip they replace.
   */
  private final int serviceCode;
  private final List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates;

  public ResolvedExistingTrip(
    ExistingTripUpdate parsedUpdate,
    LocalDate serviceDate,
    Trip trip,
    TripPattern pattern,
    TripPattern scheduledPattern,
    TripTimes scheduledTripTimes,
    int serviceCode,
    List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates
  ) {
    this.formatPolicy = parsedUpdate.formatPolicy();
    this.dataSource = parsedUpdate.dataSource();
    this.vehicleDescription = parsedUpdate.vehicleDescription();
    this.tripHeadsign = parsedUpdate.tripHeadsign();
    this.hasStopSequences = parsedUpdate.hasStopSequences();
    this.cancellation = parsedUpdate instanceof TripModification pmt ? pmt.isCancellation() : false;
    this.extraJourney = parsedUpdate instanceof TripModification pmt2
      ? pmt2.isExtraJourney()
      : false;
    this.serviceDate = Objects.requireNonNull(serviceDate, "serviceDate must not be null");
    this.trip = Objects.requireNonNull(trip, "trip must not be null");
    this.pattern = Objects.requireNonNull(pattern, "pattern must not be null");
    this.scheduledPattern = Objects.requireNonNull(
      scheduledPattern,
      "scheduledPattern must not be null"
    );
    this.scheduledTripTimes = Objects.requireNonNull(
      scheduledTripTimes,
      "scheduledTripTimes must not be null"
    );
    this.serviceCode = serviceCode;
    this.resolvedStopTimeUpdates = Objects.requireNonNull(
      resolvedStopTimeUpdates,
      "resolvedStopTimeUpdates must not be null"
    );
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
  public TripUpdateResult applyModification(
    DeduplicatorService deduplicator,
    Function<Trip, FeedScopedId> patternIdGenerator
  ) {
    // Build the new stop pattern from stop time updates
    var stopTimesAndPattern = NewStopPatternFactory.buildNewStopPattern(
      trip,
      resolvedStopTimeUpdates,
      formatPolicy.firstLastStopTime()
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
    StopTimeUpdates.applyRealTimeUpdates(builder, resolvedStopTimeUpdates);

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
    var realTimeTripUpdate = RealTimeTripUpdate.of(newPattern, builder.build(), serviceDate)
      .withProducer(dataSource)
      .withRevertPreviousRealTimeUpdates(true)
      .withHideTripInScheduledPattern(scheduledPattern)
      .build();
    return new TripUpdateResult(realTimeTripUpdate);
  }

  // ========== Resolved data accessors ==========

  public LocalDate serviceDate() {
    return serviceDate;
  }

  public Trip trip() {
    return trip;
  }

  public TripPattern pattern() {
    return pattern;
  }

  /**
   * The original scheduled pattern.
   * If the current pattern is a real-time modified pattern, this returns the original.
   * Otherwise, returns the same as pattern().
   */
  public TripPattern scheduledPattern() {
    return scheduledPattern;
  }

  public TripTimes scheduledTripTimes() {
    return scheduledTripTimes;
  }

  /** The behavioural {@link FormatPolicy} for this update, chosen once at the parser boundary. */
  public FormatPolicy formatPolicy() {
    return formatPolicy;
  }

  /**
   * Apply what the message says about the journey as it runs today - the headsign it displays and
   * the vehicle serving it - to the trip times being built.
   */
  public void applyJourneyDescription(RealTimeTripTimesBuilder builder) {
    if (tripHeadsign != null) {
      builder.withTripHeadsign(tripHeadsign);
    }
    vehicleDescription.applyTo(builder);
  }

  public List<ResolvedStopTimeUpdate> stopTimeUpdates() {
    return resolvedStopTimeUpdates;
  }

  /**
   * Whether every stop of the trip is cancelled/skipped, which cancels the trip implicitly. The
   * stop updates must cover the full pattern: a partial update only cancels the stops it mentions.
   */
  public boolean isCancelledAtEveryStop() {
    return (
      ResolvedStopTimeUpdate.allSkipped(resolvedStopTimeUpdates) &&
      resolvedStopTimeUpdates.size() == pattern.numberOfStops()
    );
  }

  public boolean hasSiriExtraCalls() {
    return resolvedStopTimeUpdates.stream().anyMatch(ResolvedStopTimeUpdate::isExtraCall);
  }

  @Nullable
  public String dataSource() {
    return dataSource;
  }

  public boolean hasStopSequences() {
    return hasStopSequences;
  }

  @Override
  public String toString() {
    return (
      "ResolvedExistingTrip{" +
      "serviceDate=" +
      serviceDate +
      ", trip=" +
      trip.getId() +
      ", pattern=" +
      pattern.getId() +
      ", scheduledPattern=" +
      scheduledPattern.getId() +
      '}'
    );
  }
}
