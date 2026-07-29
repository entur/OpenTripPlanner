package org.opentripplanner.updater.trip;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ExistingTripUpdate;
import org.opentripplanner.updater.trip.model.ResolvedExistingTrip;
import org.opentripplanner.updater.trip.model.ResolvedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ScheduledTripUpdate;
import org.opentripplanner.updater.trip.model.TripModification;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.policy.StopReplacementPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a {@link ExistingTripUpdate} into a {@link ResolvedExistingTrip} for updates
 * to existing scheduled trips.
 * <p>
 * Used for UPDATE_EXISTING ({@link ScheduledTripUpdate}) and MODIFY_TRIP
 * ({@link TripModification}).
 * <p>
 * Resolution includes:
 * <ul>
 *   <li>Service date (from explicit date, TripOnServiceDate, or aimed departure)</li>
 *   <li>Trip (from trip ID or TripOnServiceDate)</li>
 *   <li>Pattern (the pattern containing the trip)</li>
 *   <li>Scheduled pattern (original if pattern is modified)</li>
 *   <li>Trip times (from scheduled timetable)</li>
 * </ul>
 * The two update types share all of that, but not their preconditions: each is validated against
 * the invariants of its own use case before it is returned, so a {@link ResolvedExistingTrip} is
 * always valid for the operation it was resolved for. The entry point the caller picks - not a
 * runtime check - decides which invariants apply.
 */
public class ExistingTripResolver {

  private static final Logger LOG = LoggerFactory.getLogger(ExistingTripResolver.class);

  private final TransitEditorService transitService;
  private final TripResolver tripResolver;
  private final ServiceDateResolver serviceDateResolver;
  private final StopResolver stopResolver;

  private final FuzzyTripMatcher fuzzyTripMatcher;

  private final ZoneId timeZone;

  public ExistingTripResolver(
    TransitEditorService transitService,
    TripResolver tripResolver,
    ServiceDateResolver serviceDateResolver,
    StopResolver stopResolver,
    FuzzyTripMatcher fuzzyTripMatcher,
    ZoneId timeZone
  ) {
    this.transitService = Objects.requireNonNull(transitService, "transitService must not be null");
    this.tripResolver = Objects.requireNonNull(tripResolver, "tripResolver must not be null");
    this.serviceDateResolver = Objects.requireNonNull(
      serviceDateResolver,
      "serviceDateResolver must not be null"
    );
    this.stopResolver = Objects.requireNonNull(stopResolver, "stopResolver must not be null");
    this.fuzzyTripMatcher = Objects.requireNonNull(
      fuzzyTripMatcher,
      "fuzzyTripMatcher must not be null"
    );
    this.timeZone = Objects.requireNonNull(timeZone, "timeZone must not be null");
  }

  /**
   * Resolve an update to the real-time times of an existing scheduled trip, for the
   * {@link ScheduledTripUpdater}.
   *
   * @throws UpdateException if resolution fails or the update violates the preconditions of an
   *                         update to an existing trip
   */
  public ResolvedExistingTrip resolve(ScheduledTripUpdate parsedUpdate) {
    var resolvedUpdate = doResolve(parsedUpdate);
    validateScheduledTripUpdate(resolvedUpdate);
    return resolvedUpdate;
  }

  /**
   * Resolve a modification of the stop pattern of an existing trip, for the {@link TripModifier}.
   *
   * @throws UpdateException if resolution fails or the update violates the preconditions of a
   *                         trip modification
   */
  public ResolvedExistingTrip resolve(TripModification parsedUpdate) {
    var resolvedUpdate = doResolve(parsedUpdate);
    validateTripModification(resolvedUpdate);
    return resolvedUpdate;
  }

  /**
   * The preconditions of an update to the times of an existing trip: a format that matches calls by
   * position (FULL_UPDATE) must send every call of the trip, and must not number them. Matching by
   * stop sequence or id (PARTIAL_UPDATE) puts no constraint on the calls.
   */
  private void validateScheduledTripUpdate(ResolvedExistingTrip resolvedUpdate) {
    // The exact-stop-count precondition only applies to position-based (FULL_UPDATE) matching.
    if (!resolvedUpdate.formatPolicy().stopMatching().requiresExactStopCount()) {
      return;
    }

    var tripId = resolvedUpdate.trip().getId();
    var scheduledPattern = resolvedUpdate.scheduledPattern();
    var stopTimeUpdates = resolvedUpdate.stopTimeUpdates();

    if (resolvedUpdate.hasStopSequences()) {
      throw UpdateException.of(tripId, UpdateErrorType.INVALID_STOP_SEQUENCE);
    }

    // The count is compared against the scheduled pattern, not the current real-time pattern,
    // because a revert update may send fewer stops than a previously modified pattern (e.g. after
    // removing an extra call).
    if (stopTimeUpdates.size() < scheduledPattern.numberOfStops()) {
      throw UpdateException.of(tripId, UpdateErrorType.TOO_FEW_STOPS);
    }
    if (stopTimeUpdates.size() > scheduledPattern.numberOfStops()) {
      throw UpdateException.of(tripId, UpdateErrorType.TOO_MANY_STOPS);
    }
  }

  /**
   * The preconditions of a modification of the stop pattern of an existing trip: at least two
   * calls, and - when the message carries SIRI extra calls - a non-extra call sequence that still
   * matches the original pattern.
   */
  private void validateTripModification(ResolvedExistingTrip resolvedUpdate) {
    var trip = resolvedUpdate.trip();
    var stopTimeUpdates = resolvedUpdate.stopTimeUpdates();

    if (stopTimeUpdates.size() < 2) {
      LOG.debug("MODIFY_TRIP: trip {} has fewer than 2 stops, skipping.", trip.getId());
      throw UpdateException.of(trip.getId(), UpdateErrorType.TOO_FEW_STOPS);
    }

    if (resolvedUpdate.hasSiriExtraCalls()) {
      validateSiriExtraCalls(
        stopTimeUpdates,
        resolvedUpdate.scheduledPattern(),
        trip,
        resolvedUpdate.formatPolicy().stopReplacement()
      );
    }
  }

  /**
   * The non-extra calls of a SIRI message with extra calls must still describe the original
   * pattern: same number of calls, each one matching the original stop according to the format's
   * {@link StopReplacementPolicy}.
   */
  private void validateSiriExtraCalls(
    List<ResolvedStopTimeUpdate> stopTimeUpdates,
    TripPattern originalPattern,
    Trip trip,
    StopReplacementPolicy stopReplacement
  ) {
    long nonExtraCount = stopTimeUpdates
      .stream()
      .filter(u -> !u.isExtraCall())
      .count();
    if (nonExtraCount != originalPattern.numberOfStops()) {
      LOG.debug(
        "SIRI extra call validation failed: {} non-extra stops but original pattern has {} stops",
        nonExtraCount,
        originalPattern.numberOfStops()
      );
      throw UpdateException.of(trip.getId(), UpdateErrorType.INVALID_STOP_SEQUENCE);
    }

    int originalIndex = 0;
    for (int i = 0; i < stopTimeUpdates.size(); i++) {
      var stopUpdate = stopTimeUpdates.get(i);
      if (stopUpdate.isExtraCall()) {
        continue;
      }

      StopLocation updateStop = stopUpdate.stop();
      if (updateStop == null) {
        throw UpdateException.of(trip.getId(), UpdateErrorType.UNKNOWN_STOP, i);
      }

      StopLocation originalStop = originalPattern.getStop(originalIndex);

      var validationResult = stopReplacement.check(originalStop, updateStop);
      if (validationResult != StopReplacementPolicy.Result.VALID) {
        LOG.debug(
          "SIRI extra call validation failed: stop {} at index {} doesn't match original stop {} ({})",
          updateStop.getId(),
          i,
          originalStop.getId(),
          validationResult
        );
        throw UpdateException.of(trip.getId(), UpdateErrorType.STOP_MISMATCH, i);
      }

      originalIndex++;
    }
  }

  /**
   * Resolve everything the two update types resolve the same way, before each is validated against
   * the invariants of its own use case.
   *
   * @throws UpdateException if resolution fails
   */
  private ResolvedExistingTrip doResolve(ExistingTripUpdate parsedUpdate) {
    // Resolve service date
    LocalDate serviceDate = serviceDateResolver.resolveServiceDate(parsedUpdate);

    var tripReference = parsedUpdate.tripReference();

    // Resolve trip and pattern
    TripAndPattern tripAndPattern;
    try {
      tripAndPattern = resolveTripWithPattern(parsedUpdate, serviceDate);
    } catch (UpdateException e) {
      LOG.debug("Could not resolve trip for update: {}", tripReference);
      throw e;
    }

    Trip trip = tripAndPattern.trip();
    TripPattern pattern = tripAndPattern.tripPattern();

    // Validate service date is valid for this trip
    validateServiceDate(trip, serviceDate);

    // Get the scheduled pattern. When the pattern is modified (e.g. a cancelled stop created a
    // new RT pattern), look up the trip's own scheduled pattern from the index rather than
    // trusting getOriginalTripPattern(). The TripPatternCache can share RT patterns across
    // trips from different routes when the modified StopPattern happens to match, causing
    // getOriginalTripPattern() to return the wrong pattern.
    TripPattern scheduledPattern = pattern.isModified()
      ? transitService.findPattern(trip)
      : pattern;

    // Get trip times from scheduled timetable
    TripTimes tripTimes = scheduledPattern.getScheduledTimetable().getTripTimes(trip);
    if (tripTimes == null) {
      LOG.warn(
        "No trip times found for trip {} in pattern {}",
        trip.getId(),
        scheduledPattern.getId()
      );
      throw UpdateException.of(trip.getId(), UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN);
    }

    // Resolve stop time updates now that service date is known
    var resolvedStopTimeUpdates = ResolvedStopTimeUpdate.resolveAll(
      parsedUpdate.stopTimeUpdates(),
      serviceDate,
      timeZone,
      stopResolver
    );

    return new ResolvedExistingTrip(
      parsedUpdate,
      serviceDate,
      trip,
      pattern,
      scheduledPattern,
      tripTimes,
      // The scheduled times of the trip already run on the calendar of the trip's service id, so
      // this is the same code as a lookup by service id - without the nullable Integer that
      // TripCalendars returns for an unregistered service.
      tripTimes.getServiceCode(),
      resolvedStopTimeUpdates
    );
  }

  /**
   * Resolve a Trip and its TripPattern from a ParsedTripUpdate.
   * Supports both exact matching and fuzzy matching (if configured).
   */
  private TripAndPattern resolveTripWithPattern(
    ExistingTripUpdate parsedUpdate,
    LocalDate serviceDate
  ) {
    TripReference reference = parsedUpdate.tripReference();

    // Try exact match first
    try {
      Trip trip = tripResolver.resolveTrip(reference);
      TripPattern pattern = transitService.findPattern(trip, serviceDate);
      if (pattern == null) {
        pattern = transitService.findPattern(trip);
      }
      if (pattern != null) {
        return new TripAndPattern(trip, pattern);
      }
      LOG.warn("Trip {} found but no pattern available", trip.getId());
      throw UpdateException.of(reference.tripId(), UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN);
    } catch (UpdateException exactMatchException) {
      // Exact match failed - try fuzzy matching if enabled
      if (fuzzyTripMatcher.isEnabled()) {
        LOG.debug("Exact match failed for {}, trying fuzzy matching", reference);
        return fuzzyTripMatcher.match(reference, parsedUpdate, serviceDate);
      }

      // Return the original exact match error
      throw exactMatchException;
    }
  }

  /**
   * Validate that the service date is valid for the trip's service.
   */
  private void validateServiceDate(Trip trip, LocalDate serviceDate) {
    var serviceId = trip.getServiceId();
    var serviceDates = transitService.getTripCalendars().listServiceDates(serviceId);
    if (!serviceDates.contains(serviceDate)) {
      LOG.debug(
        "Trip {} has service date {} for which trip's service is not valid, skipping.",
        trip.getId(),
        serviceDate
      );
      throw UpdateException.of(trip.getId(), UpdateErrorType.NO_SERVICE_ON_DATE);
    }
  }
}
