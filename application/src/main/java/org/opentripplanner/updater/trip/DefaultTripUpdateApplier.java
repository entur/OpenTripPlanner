package org.opentripplanner.updater.trip;

import java.util.Objects;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of TripUpdateApplier that applies parsed trip updates to the transit
 * model. This is the unified component shared by both SIRI-ET and GTFS-RT updaters.
 * <p>
 * This class consolidates the logic previously duplicated between:
 * - SIRI: ModifiedTripBuilder, AddedTripBuilder, ExtraCallTripBuilder
 * - GTFS-RT: GtfsRealTimeTripUpdateAdapter, TripTimesUpdater
 */
public class DefaultTripUpdateApplier implements TripUpdateApplier {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultTripUpdateApplier.class);

  private final TransitEditorService transitService;

  public DefaultTripUpdateApplier(TransitEditorService transitService) {
    this.transitService = Objects.requireNonNull(transitService);
  }

  @Override
  public Result<RealTimeTripUpdate, UpdateError> apply(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    try {
      return switch (parsedUpdate.updateType()) {
        case UPDATE_EXISTING -> handleUpdateExisting(parsedUpdate, context);
        case CANCEL_TRIP -> handleCancelTrip(parsedUpdate, context);
        case DELETE_TRIP -> handleDeleteTrip(parsedUpdate, context);
        case ADD_NEW_TRIP -> handleAddNewTrip(parsedUpdate, context);
        case MODIFY_TRIP -> handleModifyTrip(parsedUpdate, context);
        case ADD_EXTRA_CALLS -> handleAddExtraCalls(parsedUpdate, context);
      };
    } catch (Exception e) {
      LOG.error("Error applying trip update: {}", e.getMessage(), e);
      return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN));
    }
  }

  private Result<RealTimeTripUpdate, UpdateError> handleUpdateExisting(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    // TODO: Implement
    return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN));
  }

  private Result<RealTimeTripUpdate, UpdateError> handleCancelTrip(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    return cancelOrDeleteTrip(parsedUpdate, context, true);
  }

  private Result<RealTimeTripUpdate, UpdateError> handleDeleteTrip(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    return cancelOrDeleteTrip(parsedUpdate, context, false);
  }

  /**
   * Common logic for canceling or deleting a trip.
   *
   * @param parsedUpdate the parsed update
   * @param context the context
   * @param isCancel true for cancel, false for delete
   * @return Result with RealTimeTripUpdate or UpdateError
   */
  private Result<RealTimeTripUpdate, UpdateError> cancelOrDeleteTrip(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context,
    boolean isCancel
  ) {
    var tripRef = parsedUpdate.tripReference();
    var serviceDate = parsedUpdate.serviceDate();

    // Resolve trip from ID
    var trip = transitService.getTrip(tripRef.tripId());
    if (trip == null) {
      LOG.debug("Trip {} not found for cancellation/deletion", tripRef.tripId());
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.TRIP_NOT_FOUND,
          null,
          context.feedId()
        )
      );
    }

    // Find the trip pattern
    var pattern = transitService.findPattern(trip, serviceDate);
    if (pattern == null) {
      LOG.debug("Pattern not found for trip {} on date {}", tripRef.tripId(), serviceDate);
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN,
          null,
          context.feedId()
        )
      );
    }

    // Get the snapshot manager and resolve current timetable
    var snapshotManager = context.snapshotManager();
    if (snapshotManager == null) {
      LOG.error("No snapshot manager available for cancellation/deletion");
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.UNKNOWN,
          null,
          context.feedId()
        )
      );
    }

    var timetable = snapshotManager.resolve(pattern, serviceDate);
    var tripTimes = timetable.getTripTimes(tripRef.tripId());
    if (tripTimes == null) {
      LOG.debug("Trip times not found for trip {} on {}", tripRef.tripId(), serviceDate);
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND,
          null,
          context.feedId()
        )
      );
    }

    // Create real-time trip times and mark as canceled/deleted
    var builder = tripTimes.createRealTimeFromScheduledTimes();
    if (isCancel) {
      builder.cancelTrip();
      LOG.debug("Canceling trip {} on {}", tripRef.tripId(), serviceDate);
    } else {
      builder.deleteTrip();
      LOG.debug("Deleting trip {} on {}", tripRef.tripId(), serviceDate);
    }

    var realTimeTripUpdate = new RealTimeTripUpdate(pattern, builder.build(), serviceDate);
    return Result.success(realTimeTripUpdate);
  }

  private Result<RealTimeTripUpdate, UpdateError> handleAddNewTrip(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    // TODO: Implement
    return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN));
  }

  private Result<RealTimeTripUpdate, UpdateError> handleModifyTrip(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    // TODO: Implement
    return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN));
  }

  private Result<RealTimeTripUpdate, UpdateError> handleAddExtraCalls(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    // TODO: Implement
    return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN));
  }
}
