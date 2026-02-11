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
import org.opentripplanner.updater.trip.model.ResolvedTripUpdate;
import org.opentripplanner.updater.trip.model.TripReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a {@link ParsedTripUpdate} into a {@link ResolvedTripUpdate} by looking up
 * all referenced entities from the transit model.
 * <p>
 * This centralizes the resolution logic that was previously duplicated across handlers:
 * <ul>
 *   <li>Service date resolution (from explicit date, TripOnServiceDate, or aimed departure)</li>
 *   <li>Trip resolution (from trip ID or TripOnServiceDate)</li>
 *   <li>Pattern resolution (finding the pattern for the trip)</li>
 *   <li>Scheduled pattern resolution (getting the original if pattern is modified)</li>
 *   <li>Trip times resolution (from scheduled timetable)</li>
 * </ul>
 * <p>
 * Different update types have different resolution requirements:
 * <ul>
 *   <li><b>UPDATE_EXISTING, MODIFY_TRIP</b>: Requires trip, pattern, and trip times</li>
 *   <li><b>CANCEL_TRIP, DELETE_TRIP</b>: Requires trip and pattern (or previously added trip)</li>
 *   <li><b>ADD_NEW_TRIP</b>: May have existing trip (update) or not (new trip)</li>
 * </ul>
 */
public class TripUpdateResolver {

  private static final Logger LOG = LoggerFactory.getLogger(TripUpdateResolver.class);

  private final TransitEditorService transitService;

  public TripUpdateResolver(TransitEditorService transitService) {
    this.transitService = Objects.requireNonNull(transitService, "transitService must not be null");
  }

  /**
   * Resolve a ParsedTripUpdate into a ResolvedTripUpdate.
   * <p>
   * The resolution strategy depends on the update type:
   * <ul>
   *   <li>UPDATE_EXISTING: Resolve trip, pattern, and trip times (required)</li>
   *   <li>MODIFY_TRIP: Same as UPDATE_EXISTING</li>
   *   <li>CANCEL_TRIP/DELETE_TRIP: Resolve trip and pattern (may be previously added trip)</li>
   *   <li>ADD_NEW_TRIP: Check if trip already exists (update) or create new</li>
   * </ul>
   *
   * @param parsedUpdate The parsed update to resolve
   * @param context The applier context containing resolvers and caches
   * @return Result containing the resolved update, or an error if resolution fails
   */
  public Result<ResolvedTripUpdate, UpdateError> resolve(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    // First, resolve the service date (required for all update types)
    var serviceDateResult = context.serviceDateResolver().resolveServiceDate(parsedUpdate);
    if (serviceDateResult.isFailure()) {
      return Result.failure(serviceDateResult.failureValue());
    }
    LocalDate serviceDate = serviceDateResult.successValue();

    // Dispatch to type-specific resolution
    return switch (parsedUpdate.updateType()) {
      case UPDATE_EXISTING -> resolveForUpdateExisting(parsedUpdate, serviceDate, context);
      case MODIFY_TRIP -> resolveForModifyTrip(parsedUpdate, serviceDate, context);
      case CANCEL_TRIP, DELETE_TRIP -> resolveForTripRemoval(parsedUpdate, serviceDate, context);
      case ADD_NEW_TRIP -> resolveForAddNewTrip(parsedUpdate, serviceDate, context);
    };
  }

  /**
   * Resolve for UPDATE_EXISTING: requires trip, pattern, and trip times.
   */
  private Result<ResolvedTripUpdate, UpdateError> resolveForUpdateExisting(
    ParsedTripUpdate parsedUpdate,
    LocalDate serviceDate,
    TripUpdateApplierContext context
  ) {
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

    return Result.success(
      ResolvedTripUpdate.forExistingTrip(
        parsedUpdate,
        serviceDate,
        trip,
        pattern,
        scheduledPattern,
        tripTimes,
        tripOnServiceDate
      )
    );
  }

  /**
   * Resolve for MODIFY_TRIP: similar to UPDATE_EXISTING.
   */
  private Result<ResolvedTripUpdate, UpdateError> resolveForModifyTrip(
    ParsedTripUpdate parsedUpdate,
    LocalDate serviceDate,
    TripUpdateApplierContext context
  ) {
    // MODIFY_TRIP has the same resolution requirements as UPDATE_EXISTING
    return resolveForUpdateExisting(parsedUpdate, serviceDate, context);
  }

  /**
   * Resolve for CANCEL_TRIP or DELETE_TRIP.
   * <p>
   * This handles two cases:
   * <ol>
   *   <li>Cancelling a previously added (real-time) trip</li>
   *   <li>Cancelling a scheduled trip</li>
   * </ol>
   */
  /**
   * Resolve for CANCEL_TRIP/DELETE_TRIP: try to resolve scheduled trip.
   * <p>
   * This only resolves from the static transit model. If the trip is not found
   * in the scheduled data, returns success with null trip/pattern - the handler
   * will check for previously added trips using the snapshot manager.
   */
  private Result<ResolvedTripUpdate, UpdateError> resolveForTripRemoval(
    ParsedTripUpdate parsedUpdate,
    LocalDate serviceDate,
    TripUpdateApplierContext context
  ) {
    var tripReference = parsedUpdate.tripReference();

    // Try to resolve as scheduled trip from static transit model
    var tripResult = context.tripResolver().resolveTrip(tripReference);
    if (tripResult.isFailure()) {
      // Trip not found in scheduled data - return success with null values
      // Handler will check for previously added trips
      return Result.success(ResolvedTripUpdate.forNewTrip(parsedUpdate, serviceDate));
    }
    Trip trip = tripResult.successValue();

    // Find pattern for the trip
    TripPattern pattern = transitService.findPattern(trip);
    if (pattern == null) {
      // No pattern - return success with null values, handler will check for added trips
      return Result.success(ResolvedTripUpdate.forNewTrip(parsedUpdate, serviceDate));
    }

    // Get trip times
    TripTimes tripTimes = pattern.getScheduledTimetable().getTripTimes(trip);
    if (tripTimes == null) {
      // No trip times - return success with null values, handler will check for added trips
      return Result.success(ResolvedTripUpdate.forNewTrip(parsedUpdate, serviceDate));
    }

    return Result.success(
      ResolvedTripUpdate.forExistingTrip(
        parsedUpdate,
        serviceDate,
        trip,
        pattern,
        pattern,
        tripTimes,
        null
      )
    );
  }

  /**
   * Resolve for ADD_NEW_TRIP.
   * <p>
   * This handles two cases:
   * <ol>
   *   <li>Creating a new trip (trip doesn't exist)</li>
   *   <li>Updating an existing added trip (trip already exists in real-time)</li>
   * </ol>
   */
  private Result<ResolvedTripUpdate, UpdateError> resolveForAddNewTrip(
    ParsedTripUpdate parsedUpdate,
    LocalDate serviceDate,
    TripUpdateApplierContext context
  ) {
    var tripCreationInfo = parsedUpdate.tripCreationInfo();
    if (tripCreationInfo == null) {
      LOG.debug("ADD_NEW_TRIP: No trip creation info provided");
      return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN));
    }

    var tripId = tripCreationInfo.tripId();

    // Check if trip already exists in scheduled data (error case)
    if (transitService.getScheduledTrip(tripId) != null) {
      LOG.debug("ADD_NEW_TRIP: Trip {} already exists in scheduled data", tripId);
      return Result.failure(
        new UpdateError(tripId, UpdateError.UpdateErrorType.TRIP_ALREADY_EXISTS)
      );
    }

    // Check if trip was already added in real-time (update rather than create)
    Trip existingRealTimeTrip = transitService.getTrip(tripId);
    if (existingRealTimeTrip != null) {
      LOG.debug(
        "ADD_NEW_TRIP: Trip {} already exists as real-time added trip, will update",
        tripId
      );

      // Find the existing pattern
      TripPattern existingPattern = transitService.findPattern(existingRealTimeTrip, serviceDate);
      if (existingPattern == null) {
        existingPattern = transitService.findPattern(existingRealTimeTrip);
      }
      if (existingPattern == null) {
        LOG.warn("UPDATE_ADDED_TRIP: Could not find pattern for existing trip {}", tripId);
        return Result.failure(
          new UpdateError(tripId, UpdateError.UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN)
        );
      }

      // Get scheduled trip times (for added trips, this is the original aimed times)
      TripTimes scheduledTripTimes = existingPattern
        .getScheduledTimetable()
        .getTripTimes(existingRealTimeTrip);
      if (scheduledTripTimes == null) {
        LOG.warn("UPDATE_ADDED_TRIP: Could not find scheduled trip times for trip {}", tripId);
        return Result.failure(
          new UpdateError(tripId, UpdateError.UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN)
        );
      }

      return Result.success(
        ResolvedTripUpdate.forExistingAddedTrip(
          parsedUpdate,
          serviceDate,
          existingRealTimeTrip,
          existingPattern,
          scheduledTripTimes
        )
      );
    }

    // New trip - no existing trip to resolve
    return Result.success(ResolvedTripUpdate.forNewTrip(parsedUpdate, serviceDate));
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
