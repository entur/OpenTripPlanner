package org.opentripplanner.updater.trip;

import java.util.Objects;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.trip.handlers.TripUpdateHandler;
import org.opentripplanner.updater.trip.handlers.TripUpdateResult;
import org.opentripplanner.updater.trip.handlers.TripUpdateValidator;
import org.opentripplanner.updater.trip.model.ParsedAddNewTrip;
import org.opentripplanner.updater.trip.model.ParsedCancelTrip;
import org.opentripplanner.updater.trip.model.ParsedDeleteTrip;
import org.opentripplanner.updater.trip.model.ParsedModifyTrip;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.ParsedUpdateExisting;

/**
 * Default implementation of TripUpdateApplier that applies parsed trip updates to the transit
 * model. This is the unified component shared by both SIRI-ET and GTFS-RT updaters.
 * <p>
 * Build a fully wired instance with {@link TripUpdateApplierFactory#create}.
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
   * Wires the pre-built resolvers, validators and handlers. Package-private; use
   * {@link TripUpdateApplierFactory#create} to obtain a fully wired instance.
   */
  DefaultTripUpdateApplier(
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
  public TripUpdateResult apply(ParsedTripUpdate parsedUpdate) {
    return switch (parsedUpdate) {
      case ParsedUpdateExisting u -> {
        var resolved = existingTripResolver.resolve(u);
        updateExistingValidator.validate(resolved);
        yield updateExistingHandler.handle(resolved);
      }
      case ParsedModifyTrip u -> {
        var resolved = existingTripResolver.resolve(u);
        modifyTripValidator.validate(resolved);
        yield modifyTripHandler.handle(resolved);
      }
      case ParsedAddNewTrip u -> {
        var resolved = newTripResolver.resolve(u);
        addNewTripValidator.validate(resolved);
        yield addNewTripHandler.handle(resolved);
      }
      case ParsedCancelTrip u -> cancelTripHandler.handle(tripRemovalResolver.resolve(u));
      case ParsedDeleteTrip u -> deleteTripHandler.handle(tripRemovalResolver.resolve(u));
    };
  }
}
