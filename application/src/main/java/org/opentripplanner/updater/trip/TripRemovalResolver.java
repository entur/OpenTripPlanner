package org.opentripplanner.updater.trip;

import java.time.LocalDate;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeState;
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
 * This resolver looks up scheduled trips first, then checks for previously added (real-time)
 * trips via the snapshot manager.
 */
public class TripRemovalResolver {

  private static final Logger LOG = LoggerFactory.getLogger(TripRemovalResolver.class);

  private final TransitEditorService transitService;
  private final TripResolver tripResolver;
  private final ServiceDateResolver serviceDateResolver;

  @Nullable
  private final TimetableSnapshotManager snapshotManager;

  public TripRemovalResolver(
    TransitEditorService transitService,
    TripResolver tripResolver,
    ServiceDateResolver serviceDateResolver,
    @Nullable TimetableSnapshotManager snapshotManager
  ) {
    this.transitService = Objects.requireNonNull(transitService, "transitService must not be null");
    this.tripResolver = Objects.requireNonNull(tripResolver, "tripResolver must not be null");
    this.serviceDateResolver = Objects.requireNonNull(
      serviceDateResolver,
      "serviceDateResolver must not be null"
    );
    this.snapshotManager = snapshotManager;
  }

  /**
   * Resolve a ParsedTripUpdate for trip cancellation or deletion.
   *
   * @param parsedUpdate The parsed update to resolve
   * @return Result containing the resolved data, or error if trip cannot be found at all
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
      // Trip not found in scheduled data - check for previously added trips
      return resolveAddedTripOrNotFound(serviceDate, tripId, dataSource);
    }
    Trip trip = tripResult.successValue();

    // Find pattern for the trip
    TripPattern pattern = transitService.findPattern(trip);
    if (pattern == null) {
      return resolveAddedTripOrNotFound(serviceDate, trip.getId(), dataSource);
    }

    // Get trip times
    TripTimes tripTimes = pattern.getScheduledTimetable().getTripTimes(trip);
    if (tripTimes == null) {
      return resolveAddedTripOrNotFound(serviceDate, trip.getId(), dataSource);
    }

    return Result.success(
      ResolvedTripRemoval.forScheduledTrip(serviceDate, trip, pattern, tripTimes, dataSource)
    );
  }

  /**
   * Check for a previously added (real-time) trip in the snapshot manager.
   * Returns a success with added trip data if found, or a failure otherwise.
   */
  private Result<ResolvedTripRemoval, UpdateError> resolveAddedTripOrNotFound(
    LocalDate serviceDate,
    FeedScopedId tripId,
    @Nullable String dataSource
  ) {
    if (snapshotManager != null && tripId != null) {
      var pattern = snapshotManager.getNewTripPatternForModifiedTrip(tripId, serviceDate);
      if (pattern != null) {
        var timetable = snapshotManager.resolve(pattern, serviceDate);
        var tripTimes = timetable.getTripTimes(tripId);
        if (tripTimes != null && tripTimes.getRealTimeState() == RealTimeState.ADDED) {
          return Result.success(
            ResolvedTripRemoval.forPreviouslyAddedTrip(
              serviceDate,
              tripId,
              pattern,
              tripTimes,
              dataSource
            )
          );
        }
      }
    }
    return Result.failure(
      new UpdateError(tripId, UpdateError.UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND)
    );
  }
}
