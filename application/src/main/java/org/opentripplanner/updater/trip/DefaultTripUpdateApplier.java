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
 * The applier uses dedicated resolvers for each update type category:
 * <ul>
 *   <li>{@link ExistingTripResolver} for UPDATE_EXISTING and MODIFY_TRIP</li>
 *   <li>{@link NewTripResolver} for ADD_NEW_TRIP</li>
 *   <li>{@link TripRemovalResolver} for CANCEL_TRIP and DELETE_TRIP</li>
 * </ul>
 */
public class DefaultTripUpdateApplier implements TripUpdateApplier {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultTripUpdateApplier.class);

  private final TransitEditorService transitService;

  // Resolvers for each update type category
  private final ExistingTripResolver existingTripResolver;
  private final NewTripResolver newTripResolver;
  private final TripRemovalResolver tripRemovalResolver;

  // Handlers for each update type
  private final TripUpdateHandler.ForExistingTrip updateExistingHandler;
  private final TripUpdateHandler.ForExistingTrip modifyTripHandler;
  private final TripUpdateHandler.ForNewTrip addNewTripHandler;
  private final TripUpdateHandler.ForTripRemoval cancelTripHandler;
  private final TripUpdateHandler.ForTripRemoval deleteTripHandler;

  public DefaultTripUpdateApplier(TransitEditorService transitService) {
    this(
      transitService,
      new ExistingTripResolver(transitService),
      new NewTripResolver(transitService),
      new TripRemovalResolver(transitService),
      new UpdateExistingTripHandler(),
      new ModifyTripHandler(),
      new AddNewTripHandler(),
      new CancelTripHandler(),
      new DeleteTripHandler()
    );
  }

  /**
   * Constructor for dependency injection and testing.
   */
  public DefaultTripUpdateApplier(
    TransitEditorService transitService,
    ExistingTripResolver existingTripResolver,
    NewTripResolver newTripResolver,
    TripRemovalResolver tripRemovalResolver,
    TripUpdateHandler.ForExistingTrip updateExistingHandler,
    TripUpdateHandler.ForExistingTrip modifyTripHandler,
    TripUpdateHandler.ForNewTrip addNewTripHandler,
    TripUpdateHandler.ForTripRemoval cancelTripHandler,
    TripUpdateHandler.ForTripRemoval deleteTripHandler
  ) {
    this.transitService = Objects.requireNonNull(transitService);
    this.existingTripResolver = Objects.requireNonNull(existingTripResolver);
    this.newTripResolver = Objects.requireNonNull(newTripResolver);
    this.tripRemovalResolver = Objects.requireNonNull(tripRemovalResolver);
    this.updateExistingHandler = Objects.requireNonNull(updateExistingHandler);
    this.modifyTripHandler = Objects.requireNonNull(modifyTripHandler);
    this.addNewTripHandler = Objects.requireNonNull(addNewTripHandler);
    this.cancelTripHandler = Objects.requireNonNull(cancelTripHandler);
    this.deleteTripHandler = Objects.requireNonNull(deleteTripHandler);
  }

  @Override
  public Result<TripUpdateResult, UpdateError> apply(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    try {
      return switch (parsedUpdate.updateType()) {
        case UPDATE_EXISTING -> {
          var resolveResult = existingTripResolver.resolve(parsedUpdate, context);
          if (resolveResult.isFailure()) {
            yield Result.failure(resolveResult.failureValue());
          }
          yield updateExistingHandler.handle(resolveResult.successValue(), context, transitService);
        }
        case MODIFY_TRIP -> {
          var resolveResult = existingTripResolver.resolve(parsedUpdate, context);
          if (resolveResult.isFailure()) {
            yield Result.failure(resolveResult.failureValue());
          }
          yield modifyTripHandler.handle(resolveResult.successValue(), context, transitService);
        }
        case ADD_NEW_TRIP -> {
          var resolveResult = newTripResolver.resolve(parsedUpdate, context);
          if (resolveResult.isFailure()) {
            yield Result.failure(resolveResult.failureValue());
          }
          yield addNewTripHandler.handle(resolveResult.successValue(), context, transitService);
        }
        case CANCEL_TRIP -> {
          var resolveResult = tripRemovalResolver.resolve(parsedUpdate, context);
          if (resolveResult.isFailure()) {
            yield Result.failure(resolveResult.failureValue());
          }
          yield cancelTripHandler.handle(resolveResult.successValue(), context, transitService);
        }
        case DELETE_TRIP -> {
          var resolveResult = tripRemovalResolver.resolve(parsedUpdate, context);
          if (resolveResult.isFailure()) {
            yield Result.failure(resolveResult.failureValue());
          }
          yield deleteTripHandler.handle(resolveResult.successValue(), context, transitService);
        }
      };
    } catch (Exception e) {
      LOG.error("Error applying trip update: {}", e.getMessage(), e);
      return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN));
    }
  }
}
