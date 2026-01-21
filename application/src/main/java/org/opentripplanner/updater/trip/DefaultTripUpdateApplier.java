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
    // TODO: Implement
    return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN));
  }

  private Result<RealTimeTripUpdate, UpdateError> handleDeleteTrip(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    // TODO: Implement
    return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN));
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
