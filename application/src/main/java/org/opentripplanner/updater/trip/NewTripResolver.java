package org.opentripplanner.updater.trip;

import java.time.LocalDate;
import java.util.Objects;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.ResolvedNewTrip;
import org.opentripplanner.updater.trip.model.ResolvedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.TripCreationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a {@link ParsedTripUpdate} into a {@link ResolvedNewTrip} for adding new trips
 * or updating previously added trips.
 * <p>
 * Used for ADD_NEW_TRIP update type.
 * <p>
 * Resolution handles two cases:
 * <ul>
 *   <li>New trip creation - returns ResolvedNewTrip with no existing trip</li>
 *   <li>Update to existing added trip - returns ResolvedNewTrip with existing trip data</li>
 * </ul>
 */
public class NewTripResolver {

  private static final Logger LOG = LoggerFactory.getLogger(NewTripResolver.class);

  private final TransitEditorService transitService;

  public NewTripResolver(TransitEditorService transitService) {
    this.transitService = Objects.requireNonNull(transitService, "transitService must not be null");
  }

  /**
   * Resolve a ParsedTripUpdate for adding a new trip.
   *
   * @param parsedUpdate The parsed update to resolve
   * @param context The applier context containing resolvers and caches
   * @return Result containing the resolved data, or an error if resolution fails
   */
  public Result<ResolvedNewTrip, UpdateError> resolve(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    // Resolve service date
    var serviceDateResult = context.serviceDateResolver().resolveServiceDate(parsedUpdate);
    if (serviceDateResult.isFailure()) {
      return Result.failure(serviceDateResult.failureValue());
    }
    LocalDate serviceDate = serviceDateResult.successValue();

    // Validate trip creation info is present
    TripCreationInfo tripCreationInfo = parsedUpdate.tripCreationInfo();
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

      // Resolve stop time updates now that service date is known
      var resolvedStopTimeUpdates = ResolvedStopTimeUpdate.resolveAll(
        parsedUpdate.stopTimeUpdates(),
        serviceDate,
        context.timeZone(),
        context.stopResolver()
      );

      return Result.success(
        ResolvedNewTrip.forExistingAddedTrip(
          parsedUpdate,
          serviceDate,
          resolvedStopTimeUpdates,
          existingRealTimeTrip,
          existingPattern,
          scheduledTripTimes
        )
      );
    }

    // Resolve stop time updates now that service date is known
    var resolvedStopTimeUpdates = ResolvedStopTimeUpdate.resolveAll(
      parsedUpdate.stopTimeUpdates(),
      serviceDate,
      context.timeZone(),
      context.stopResolver()
    );

    // New trip - no existing trip to resolve
    return Result.success(
      ResolvedNewTrip.forNewTrip(parsedUpdate, serviceDate, resolvedStopTimeUpdates)
    );
  }
}
