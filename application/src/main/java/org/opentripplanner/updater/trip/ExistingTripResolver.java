package org.opentripplanner.updater.trip;

import java.time.LocalDate;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.ResolvedExistingTrip;
import org.opentripplanner.updater.trip.model.ResolvedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.TripReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a {@link ParsedTripUpdate} into a {@link ResolvedExistingTrip} for updates
 * to existing scheduled trips.
 * <p>
 * Used for UPDATE_EXISTING and MODIFY_TRIP update types.
 * <p>
 * Resolution includes:
 * <ul>
 *   <li>Service date (from explicit date, TripOnServiceDate, or aimed departure)</li>
 *   <li>Trip (from trip ID or TripOnServiceDate)</li>
 *   <li>Pattern (the pattern containing the trip)</li>
 *   <li>Scheduled pattern (original if pattern is modified)</li>
 *   <li>Trip times (from scheduled timetable)</li>
 *   <li>TripOnServiceDate (optional, for dated vehicle journey lookups)</li>
 * </ul>
 */
public class ExistingTripResolver {

  private static final Logger LOG = LoggerFactory.getLogger(ExistingTripResolver.class);

  private final TransitEditorService transitService;

  public ExistingTripResolver(TransitEditorService transitService) {
    this.transitService = Objects.requireNonNull(transitService, "transitService must not be null");
  }

  /**
   * Resolve a ParsedTripUpdate for an existing trip.
   *
   * @param parsedUpdate The parsed update to resolve
   * @param context The applier context containing resolvers and caches
   * @return Result containing the resolved data, or an error if resolution fails
   */
  public Result<ResolvedExistingTrip, UpdateError> resolve(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    // Resolve service date
    var serviceDateResult = context.serviceDateResolver().resolveServiceDate(parsedUpdate);
    if (serviceDateResult.isFailure()) {
      return Result.failure(serviceDateResult.failureValue());
    }
    LocalDate serviceDate = serviceDateResult.successValue();

    var tripReference = parsedUpdate.tripReference();

    // Resolve trip and pattern
    var tripAndPatternResult = resolveTripWithPattern(parsedUpdate, serviceDate, context);
    if (tripAndPatternResult.isFailure()) {
      LOG.debug("Could not resolve trip for update: {}", tripReference);
      return Result.failure(tripAndPatternResult.failureValue());
    }

    var tripAndPattern = tripAndPatternResult.successValue();
    Trip trip = tripAndPattern.trip();
    TripPattern pattern = tripAndPattern.tripPattern();

    // Validate service date is valid for this trip
    var validationResult = validateServiceDate(trip, serviceDate);
    if (validationResult.isFailure()) {
      return Result.failure(validationResult.failureValue());
    }

    // Get the scheduled pattern (original if this is a modified pattern)
    TripPattern scheduledPattern = pattern.isModified()
      ? pattern.getOriginalTripPattern()
      : pattern;

    // Get trip times from scheduled timetable
    TripTimes tripTimes = scheduledPattern.getScheduledTimetable().getTripTimes(trip);
    if (tripTimes == null) {
      LOG.warn(
        "No trip times found for trip {} in pattern {}",
        trip.getId(),
        scheduledPattern.getId()
      );
      return Result.failure(
        new UpdateError(trip.getId(), UpdateError.UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN)
      );
    }

    // Resolve TripOnServiceDate if available
    TripOnServiceDate tripOnServiceDate = resolveTripOnServiceDate(tripReference, context);

    // Resolve stop time updates now that service date is known
    var resolvedStopTimeUpdates = ResolvedStopTimeUpdate.resolveAll(
      parsedUpdate.stopTimeUpdates(),
      serviceDate,
      context.timeZone()
    );

    return Result.success(
      new ResolvedExistingTrip(
        parsedUpdate,
        serviceDate,
        trip,
        pattern,
        scheduledPattern,
        tripTimes,
        tripOnServiceDate,
        resolvedStopTimeUpdates
      )
    );
  }

  /**
   * Resolve a Trip and its TripPattern from a ParsedTripUpdate.
   * Supports both exact matching and fuzzy matching (if configured).
   */
  private Result<TripAndPattern, UpdateError> resolveTripWithPattern(
    ParsedTripUpdate parsedUpdate,
    LocalDate serviceDate,
    TripUpdateApplierContext context
  ) {
    var tripResolver = context.tripResolver();
    var fuzzyTripMatcher = context.fuzzyTripMatcher();
    TripReference reference = parsedUpdate.tripReference();

    // Try exact match first
    var exactResult = tripResolver.resolveTrip(reference);
    if (exactResult.isSuccess()) {
      Trip trip = exactResult.successValue();
      TripPattern pattern = transitService.findPattern(trip, serviceDate);
      if (pattern == null) {
        pattern = transitService.findPattern(trip);
      }
      if (pattern != null) {
        return Result.success(new TripAndPattern(trip, pattern));
      }
      LOG.warn("Trip {} found but no pattern available", trip.getId());
      return Result.failure(
        new UpdateError(reference.tripId(), UpdateError.UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN)
      );
    }

    // Exact match failed - try fuzzy matching if allowed
    if (shouldTryFuzzyMatching(reference, fuzzyTripMatcher)) {
      LOG.debug("Exact match failed for {}, trying fuzzy matching", reference);
      return fuzzyTripMatcher.match(reference, parsedUpdate, serviceDate);
    }

    // Return the original exact match error
    return Result.failure(exactResult.failureValue());
  }

  private boolean shouldTryFuzzyMatching(
    TripReference reference,
    @Nullable FuzzyTripMatcher fuzzyTripMatcher
  ) {
    if (fuzzyTripMatcher == null) {
      return false;
    }
    return reference.fuzzyMatchingHint() == TripReference.FuzzyMatchingHint.FUZZY_MATCH_ALLOWED;
  }

  /**
   * Validate that the service date is valid for the trip's service.
   */
  private Result<Void, UpdateError> validateServiceDate(Trip trip, LocalDate serviceDate) {
    var serviceId = trip.getServiceId();
    var serviceDates = transitService.getCalendarService().getServiceDatesForServiceId(serviceId);
    if (!serviceDates.contains(serviceDate)) {
      LOG.debug(
        "Trip {} has service date {} for which trip's service is not valid, skipping.",
        trip.getId(),
        serviceDate
      );
      return Result.failure(
        new UpdateError(trip.getId(), UpdateError.UpdateErrorType.NO_SERVICE_ON_DATE)
      );
    }
    return Result.success(null);
  }

  /**
   * Try to resolve TripOnServiceDate from a trip reference.
   */
  @Nullable
  private TripOnServiceDate resolveTripOnServiceDate(
    TripReference reference,
    TripUpdateApplierContext context
  ) {
    if (reference.hasTripOnServiceDateId()) {
      return context.tripResolver().resolveTripOnServiceDateOrNull(reference);
    }
    return null;
  }
}
