package org.opentripplanner.updater.trip;

import java.util.Objects;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.handlers.AddNewTripHandler;
import org.opentripplanner.updater.trip.handlers.CancelTripHandler;
import org.opentripplanner.updater.trip.handlers.DeleteTripHandler;
import org.opentripplanner.updater.trip.handlers.ModifyTripHandler;
import org.opentripplanner.updater.trip.handlers.TripUpdateHandler;
import org.opentripplanner.updater.trip.handlers.TripUpdateResult;
import org.opentripplanner.updater.trip.handlers.UpdateExistingTripHandler;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.ResolvedTripUpdate;
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
 * The applier first resolves the parsed update using {@link TripUpdateResolver} to look up
 * all referenced entities (trip, pattern, service date, etc.), then delegates to the
 * appropriate handler based on update type.
 */
public class DefaultTripUpdateApplier implements TripUpdateApplier {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultTripUpdateApplier.class);

  private final TransitEditorService transitService;
  private final TripUpdateResolver resolver;
  private final TripUpdateHandler updateExistingHandler;
  private final TripUpdateHandler cancelTripHandler;
  private final TripUpdateHandler deleteTripHandler;
  private final TripUpdateHandler addNewTripHandler;
  private final TripUpdateHandler modifyTripHandler;

  public DefaultTripUpdateApplier(TransitEditorService transitService) {
    this(
      transitService,
      new TripUpdateResolver(transitService),
      new UpdateExistingTripHandler(),
      new CancelTripHandler(),
      new DeleteTripHandler(),
      new AddNewTripHandler(),
      new ModifyTripHandler()
    );
  }

  /**
   * Constructor for dependency injection and testing.
   */
  public DefaultTripUpdateApplier(
    TransitEditorService transitService,
    TripUpdateResolver resolver,
    TripUpdateHandler updateExistingHandler,
    TripUpdateHandler cancelTripHandler,
    TripUpdateHandler deleteTripHandler,
    TripUpdateHandler addNewTripHandler,
    TripUpdateHandler modifyTripHandler
  ) {
    this.transitService = Objects.requireNonNull(transitService);
    this.resolver = Objects.requireNonNull(resolver);
    this.updateExistingHandler = Objects.requireNonNull(updateExistingHandler);
    this.cancelTripHandler = Objects.requireNonNull(cancelTripHandler);
    this.deleteTripHandler = Objects.requireNonNull(deleteTripHandler);
    this.addNewTripHandler = Objects.requireNonNull(addNewTripHandler);
    this.modifyTripHandler = Objects.requireNonNull(modifyTripHandler);
  }

  @Override
  public Result<TripUpdateResult, UpdateError> apply(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    try {
      // Resolve the parsed update to get all referenced entities
      var resolveResult = resolver.resolve(parsedUpdate, context);
      if (resolveResult.isFailure()) {
        return Result.failure(resolveResult.failureValue());
      }
      ResolvedTripUpdate resolvedUpdate = resolveResult.successValue();

      // Dispatch to the appropriate handler based on update type
      TripUpdateHandler handler = switch (resolvedUpdate.updateType()) {
        case UPDATE_EXISTING -> updateExistingHandler;
        case CANCEL_TRIP -> cancelTripHandler;
        case DELETE_TRIP -> deleteTripHandler;
        case ADD_NEW_TRIP -> addNewTripHandler;
        case MODIFY_TRIP -> modifyTripHandler;
      };
      return handler.handle(resolvedUpdate, context, transitService);
    } catch (Exception e) {
      LOG.error("Error applying trip update: {}", e.getMessage(), e);
      return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN));
    }
  }
}
