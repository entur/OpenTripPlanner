package org.opentripplanner.updater.trip;

import java.util.Objects;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.handlers.AddExtraCallsHandler;
import org.opentripplanner.updater.trip.handlers.AddNewTripHandler;
import org.opentripplanner.updater.trip.handlers.CancelTripHandler;
import org.opentripplanner.updater.trip.handlers.DeleteTripHandler;
import org.opentripplanner.updater.trip.handlers.ModifyTripHandler;
import org.opentripplanner.updater.trip.handlers.TripUpdateHandler;
import org.opentripplanner.updater.trip.handlers.UpdateExistingTripHandler;
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
 * <p>
 * Each update type is delegated to a specific handler class for better separation of concerns.
 */
public class DefaultTripUpdateApplier implements TripUpdateApplier {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultTripUpdateApplier.class);

  private final TransitEditorService transitService;
  private final TripUpdateHandler updateExistingHandler;
  private final TripUpdateHandler cancelTripHandler;
  private final TripUpdateHandler deleteTripHandler;
  private final TripUpdateHandler addNewTripHandler;
  private final TripUpdateHandler modifyTripHandler;
  private final TripUpdateHandler addExtraCallsHandler;

  public DefaultTripUpdateApplier(TransitEditorService transitService) {
    this(
      transitService,
      new UpdateExistingTripHandler(),
      new CancelTripHandler(),
      new DeleteTripHandler(),
      new AddNewTripHandler(),
      new ModifyTripHandler(),
      new AddExtraCallsHandler()
    );
  }

  /**
   * Constructor for dependency injection and testing.
   */
  public DefaultTripUpdateApplier(
    TransitEditorService transitService,
    TripUpdateHandler updateExistingHandler,
    TripUpdateHandler cancelTripHandler,
    TripUpdateHandler deleteTripHandler,
    TripUpdateHandler addNewTripHandler,
    TripUpdateHandler modifyTripHandler,
    TripUpdateHandler addExtraCallsHandler
  ) {
    this.transitService = Objects.requireNonNull(transitService);
    this.updateExistingHandler = Objects.requireNonNull(updateExistingHandler);
    this.cancelTripHandler = Objects.requireNonNull(cancelTripHandler);
    this.deleteTripHandler = Objects.requireNonNull(deleteTripHandler);
    this.addNewTripHandler = Objects.requireNonNull(addNewTripHandler);
    this.modifyTripHandler = Objects.requireNonNull(modifyTripHandler);
    this.addExtraCallsHandler = Objects.requireNonNull(addExtraCallsHandler);
  }

  @Override
  public Result<RealTimeTripUpdate, UpdateError> apply(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    try {
      TripUpdateHandler handler =
        switch (parsedUpdate.updateType()) {
          case UPDATE_EXISTING -> updateExistingHandler;
          case CANCEL_TRIP -> cancelTripHandler;
          case DELETE_TRIP -> deleteTripHandler;
          case ADD_NEW_TRIP -> addNewTripHandler;
          case MODIFY_TRIP -> modifyTripHandler;
          case ADD_EXTRA_CALLS -> addExtraCallsHandler;
        };
      return handler.handle(parsedUpdate, context, transitService);
    } catch (Exception e) {
      LOG.error("Error applying trip update: {}", e.getMessage(), e);
      return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN));
    }
  }
}
