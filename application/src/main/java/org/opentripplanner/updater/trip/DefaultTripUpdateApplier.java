package org.opentripplanner.updater.trip;

import java.time.ZoneId;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.framework.DeduplicatorService;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.handlers.AddNewTripHandler;
import org.opentripplanner.updater.trip.handlers.AddNewTripValidator;
import org.opentripplanner.updater.trip.handlers.CancelTripHandler;
import org.opentripplanner.updater.trip.handlers.DeleteTripHandler;
import org.opentripplanner.updater.trip.handlers.ModifyTripHandler;
import org.opentripplanner.updater.trip.handlers.ModifyTripValidator;
import org.opentripplanner.updater.trip.handlers.RouteCreationStrategy;
import org.opentripplanner.updater.trip.handlers.TripUpdateHandler;
import org.opentripplanner.updater.trip.handlers.TripUpdateResult;
import org.opentripplanner.updater.trip.handlers.TripUpdateValidator;
import org.opentripplanner.updater.trip.handlers.UpdateExistingTripHandler;
import org.opentripplanner.updater.trip.handlers.UpdateExistingTripValidator;
import org.opentripplanner.updater.trip.model.ParsedAddNewTrip;
import org.opentripplanner.updater.trip.model.ParsedCancelTrip;
import org.opentripplanner.updater.trip.model.ParsedDeleteTrip;
import org.opentripplanner.updater.trip.model.ParsedModifyTrip;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.ParsedUpdateExisting;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;
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
 * The applier uses pattern matching on the sealed {@link ParsedTripUpdate} hierarchy
 * to dispatch to the correct resolver/validator/handler:
 * <ul>
 *   <li>{@link ParsedUpdateExisting} → {@link ExistingTripResolver}</li>
 *   <li>{@link ParsedModifyTrip} → {@link ExistingTripResolver}</li>
 *   <li>{@link ParsedAddNewTrip} → {@link NewTripResolver}</li>
 *   <li>{@link ParsedCancelTrip} → {@link TripRemovalResolver}</li>
 *   <li>{@link ParsedDeleteTrip} → {@link TripRemovalResolver}</li>
 * </ul>
 */
public class DefaultTripUpdateApplier implements TripUpdateApplier {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultTripUpdateApplier.class);

  private final TransitEditorService transitService;

  // Resolvers for each update type category
  private final ExistingTripResolver existingTripResolver;
  private final NewTripResolver newTripResolver;
  private final TripRemovalResolver tripRemovalResolver;

  // Validators for each update type
  private final TripUpdateValidator.ForExistingTrip updateExistingValidator;
  private final TripUpdateValidator.ForExistingTrip modifyTripValidator;
  private final TripUpdateValidator.ForNewTrip addNewTripValidator;

  // Handlers for each update type
  private final TripUpdateHandler.ForExistingTrip updateExistingHandler;
  private final TripUpdateHandler.ForExistingTrip modifyTripHandler;
  private final TripUpdateHandler.ForNewTrip addNewTripHandler;
  private final TripUpdateHandler.ForTripRemoval cancelTripHandler;
  private final TripUpdateHandler.ForTripRemoval deleteTripHandler;

  /**
   * Primary constructor that takes all dependencies needed to construct resolvers and handlers.
   */
  public DefaultTripUpdateApplier(
    String feedId,
    ZoneId timeZone,
    TransitEditorService transitService,
    DeduplicatorService deduplicator,
    @Nullable TimetableSnapshotManager snapshotManager,
    TripPatternCache tripPatternCache,
    @Nullable FuzzyTripMatcher fuzzyTripMatcher,
    RouteCreationStrategy routeCreationStrategy
  ) {
    this.transitService = Objects.requireNonNull(transitService);

    // Create shared resolvers
    var tripResolver = new TripResolver(transitService);
    var serviceDateResolver = new ServiceDateResolver(tripResolver, transitService);
    var stopResolver = new StopResolver(transitService);

    // Create resolvers with injected deps
    this.existingTripResolver = new ExistingTripResolver(
      transitService,
      tripResolver,
      serviceDateResolver,
      stopResolver,
      fuzzyTripMatcher,
      timeZone
    );
    this.newTripResolver = new NewTripResolver(
      transitService,
      serviceDateResolver,
      stopResolver,
      timeZone
    );
    this.tripRemovalResolver = new TripRemovalResolver(
      transitService,
      tripResolver,
      serviceDateResolver
    );

    // Create validators
    this.updateExistingValidator = new UpdateExistingTripValidator();
    this.modifyTripValidator = new ModifyTripValidator();
    this.addNewTripValidator = new AddNewTripValidator();

    // Create handlers with injected deps
    this.updateExistingHandler = new UpdateExistingTripHandler(snapshotManager, tripPatternCache);
    this.modifyTripHandler = new ModifyTripHandler(
      snapshotManager,
      transitService,
      deduplicator,
      tripPatternCache
    );
    this.addNewTripHandler = new AddNewTripHandler(
      feedId,
      transitService,
      deduplicator,
      tripPatternCache,
      routeCreationStrategy
    );
    this.cancelTripHandler = new CancelTripHandler(snapshotManager);
    this.deleteTripHandler = new DeleteTripHandler(snapshotManager);
  }

  /**
   * Constructor for dependency injection and testing.
   */
  public DefaultTripUpdateApplier(
    TransitEditorService transitService,
    ExistingTripResolver existingTripResolver,
    NewTripResolver newTripResolver,
    TripRemovalResolver tripRemovalResolver,
    TripUpdateValidator.ForExistingTrip updateExistingValidator,
    TripUpdateValidator.ForExistingTrip modifyTripValidator,
    TripUpdateValidator.ForNewTrip addNewTripValidator,
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
    this.updateExistingValidator = Objects.requireNonNull(updateExistingValidator);
    this.modifyTripValidator = Objects.requireNonNull(modifyTripValidator);
    this.addNewTripValidator = Objects.requireNonNull(addNewTripValidator);
    this.updateExistingHandler = Objects.requireNonNull(updateExistingHandler);
    this.modifyTripHandler = Objects.requireNonNull(modifyTripHandler);
    this.addNewTripHandler = Objects.requireNonNull(addNewTripHandler);
    this.cancelTripHandler = Objects.requireNonNull(cancelTripHandler);
    this.deleteTripHandler = Objects.requireNonNull(deleteTripHandler);
  }

  @Override
  public Result<TripUpdateResult, UpdateError> apply(ParsedTripUpdate parsedUpdate) {
    try {
      var producer = parsedUpdate.dataSource();
      return switch (parsedUpdate) {
        case ParsedUpdateExisting u -> {
          var resolveResult = existingTripResolver.resolve(u);
          if (resolveResult.isFailure()) {
            yield withProducer(Result.failure(resolveResult.failureValue()), producer);
          }
          var resolved = resolveResult.successValue();
          var validationResult = updateExistingValidator.validate(resolved);
          if (validationResult.isFailure()) {
            yield withProducer(Result.failure(validationResult.failureValue()), producer);
          }
          yield updateExistingHandler.handle(resolved);
        }
        case ParsedModifyTrip u -> {
          var resolveResult = existingTripResolver.resolve(u);
          if (resolveResult.isFailure()) {
            yield withProducer(Result.failure(resolveResult.failureValue()), producer);
          }
          var resolved = resolveResult.successValue();
          var validationResult = modifyTripValidator.validate(resolved);
          if (validationResult.isFailure()) {
            yield withProducer(Result.failure(validationResult.failureValue()), producer);
          }
          yield modifyTripHandler.handle(resolved);
        }
        case ParsedAddNewTrip u -> {
          var resolveResult = newTripResolver.resolve(u);
          if (resolveResult.isFailure()) {
            yield withProducer(Result.failure(resolveResult.failureValue()), producer);
          }
          var resolved = resolveResult.successValue();
          var validationResult = addNewTripValidator.validate(resolved);
          if (validationResult.isFailure()) {
            yield withProducer(Result.failure(validationResult.failureValue()), producer);
          }
          yield addNewTripHandler.handle(resolved);
        }
        case ParsedCancelTrip u -> {
          var resolveResult = tripRemovalResolver.resolve(u);
          if (resolveResult.isFailure()) {
            yield withProducer(Result.failure(resolveResult.failureValue()), producer);
          }
          yield cancelTripHandler.handle(resolveResult.successValue());
        }
        case ParsedDeleteTrip u -> {
          var resolveResult = tripRemovalResolver.resolve(u);
          if (resolveResult.isFailure()) {
            yield withProducer(Result.failure(resolveResult.failureValue()), producer);
          }
          yield deleteTripHandler.handle(resolveResult.successValue());
        }
      };
    } catch (Exception e) {
      LOG.error("Error applying trip update: {}", e.getMessage(), e);
      return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN));
    }
  }

  /**
   * Enrich a failure result with producer information from the parsed update.
   * On the success path, the producer is already set in the RealTimeTripUpdate by the handlers.
   */
  private static Result<TripUpdateResult, UpdateError> withProducer(
    Result<TripUpdateResult, UpdateError> result,
    @Nullable String producer
  ) {
    if (result.isSuccess() || producer == null) {
      return result;
    }
    var e = result.failureValue();
    return Result.failure(new UpdateError(e.tripId(), e.errorType(), e.stopIndex(), producer));
  }
}
