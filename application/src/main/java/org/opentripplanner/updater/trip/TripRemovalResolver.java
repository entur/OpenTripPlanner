package org.opentripplanner.updater.trip;

import java.time.LocalDate;
import java.util.Objects;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.ResolvedTripRemoval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a {@link ParsedTripUpdate} into a {@link ResolvedTripRemoval} for cancelling
 * or deleting trips.
 * <p>
 * Used for CANCEL_TRIP and DELETE_TRIP update types.
 * <p>
 * This resolver only looks up scheduled trips. If the trip is not found in the scheduled
 * data, it returns a success with null trip data - the handler will then check for
 * previously added trips using the snapshot manager.
 */
public class TripRemovalResolver {

  private static final Logger LOG = LoggerFactory.getLogger(TripRemovalResolver.class);

  private final TransitEditorService transitService;

  public TripRemovalResolver(TransitEditorService transitService) {
    this.transitService = Objects.requireNonNull(transitService, "transitService must not be null");
  }

  /**
   * Resolve a ParsedTripUpdate for trip cancellation or deletion.
   *
   * @param parsedUpdate The parsed update to resolve
   * @param context The applier context containing resolvers and caches
   * @return Result containing the resolved data (always succeeds - handler checks for added trips)
   */
  public Result<ResolvedTripRemoval, UpdateError> resolve(
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
    FeedScopedId tripId = tripReference.tripId();

    // Try to resolve as scheduled trip from static transit model
    var tripResult = context.tripResolver().resolveTrip(tripReference);
    if (tripResult.isFailure()) {
      // Trip not found in scheduled data - return success with null values
      // Handler will check for previously added trips
      return Result.success(
        ResolvedTripRemoval.notFoundInSchedule(parsedUpdate, serviceDate, tripId)
      );
    }
    Trip trip = tripResult.successValue();

    // Find pattern for the trip
    TripPattern pattern = transitService.findPattern(trip);
    if (pattern == null) {
      // No pattern - return success with null values, handler will check for added trips
      return Result.success(
        ResolvedTripRemoval.notFoundInSchedule(parsedUpdate, serviceDate, trip.getId())
      );
    }

    // Get trip times
    TripTimes tripTimes = pattern.getScheduledTimetable().getTripTimes(trip);
    if (tripTimes == null) {
      // No trip times - return success with null values, handler will check for added trips
      return Result.success(
        ResolvedTripRemoval.notFoundInSchedule(parsedUpdate, serviceDate, trip.getId())
      );
    }

    return Result.success(
      ResolvedTripRemoval.forScheduledTrip(parsedUpdate, serviceDate, trip, pattern, tripTimes)
    );
  }
}
