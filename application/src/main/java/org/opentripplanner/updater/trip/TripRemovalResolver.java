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
import org.opentripplanner.updater.trip.model.ParsedTripRemoval;
import org.opentripplanner.updater.trip.model.ResolvedTripRemoval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a {@link ParsedTripRemoval} into a {@link ResolvedTripRemoval} for cancelling
 * or deleting trips.
 * <p>
 * Used for CANCEL_TRIP ({@link org.opentripplanner.updater.trip.model.ParsedCancelTrip})
 * and DELETE_TRIP ({@link org.opentripplanner.updater.trip.model.ParsedDeleteTrip}).
 * <p>
 * This resolver only looks up scheduled trips. If the trip is not found in the scheduled
 * data, it returns a success with null trip data - the handler will then check for
 * previously added trips using the snapshot manager.
 */
public class TripRemovalResolver {

  private static final Logger LOG = LoggerFactory.getLogger(TripRemovalResolver.class);

  private final TransitEditorService transitService;
  private final TripResolver tripResolver;
  private final ServiceDateResolver serviceDateResolver;

  public TripRemovalResolver(
    TransitEditorService transitService,
    TripResolver tripResolver,
    ServiceDateResolver serviceDateResolver
  ) {
    this.transitService = Objects.requireNonNull(transitService, "transitService must not be null");
    this.tripResolver = Objects.requireNonNull(tripResolver, "tripResolver must not be null");
    this.serviceDateResolver = Objects.requireNonNull(
      serviceDateResolver,
      "serviceDateResolver must not be null"
    );
  }

  /**
   * Resolve a ParsedTripUpdate for trip cancellation or deletion.
   *
   * @param parsedUpdate The parsed update to resolve
   * @return Result containing the resolved data (always succeeds - handler checks for added trips)
   */
  public Result<ResolvedTripRemoval, UpdateError> resolve(ParsedTripRemoval parsedUpdate) {
    // Resolve service date
    var serviceDateResult = serviceDateResolver.resolveServiceDate(parsedUpdate);
    if (serviceDateResult.isFailure()) {
      return Result.failure(serviceDateResult.failureValue());
    }
    LocalDate serviceDate = serviceDateResult.successValue();

    var tripReference = parsedUpdate.tripReference();
    FeedScopedId tripId = tripReference.tripId();
    String dataSource = parsedUpdate.dataSource();

    // Try to resolve as scheduled trip from static transit model
    var tripResult = tripResolver.resolveTrip(tripReference);
    if (tripResult.isFailure()) {
      // Trip not found in scheduled data - return success with null values
      // Handler will check for previously added trips
      return Result.success(
        ResolvedTripRemoval.notFoundInSchedule(serviceDate, tripId, dataSource)
      );
    }
    Trip trip = tripResult.successValue();

    // Find pattern for the trip
    TripPattern pattern = transitService.findPattern(trip);
    if (pattern == null) {
      // No pattern - return success with null values, handler will check for added trips
      return Result.success(
        ResolvedTripRemoval.notFoundInSchedule(serviceDate, trip.getId(), dataSource)
      );
    }

    // Get trip times
    TripTimes tripTimes = pattern.getScheduledTimetable().getTripTimes(trip);
    if (tripTimes == null) {
      // No trip times - return success with null values, handler will check for added trips
      return Result.success(
        ResolvedTripRemoval.notFoundInSchedule(serviceDate, trip.getId(), dataSource)
      );
    }

    return Result.success(
      ResolvedTripRemoval.forScheduledTrip(serviceDate, trip, pattern, tripTimes, dataSource)
    );
  }
}
