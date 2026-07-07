package org.opentripplanner.updater.trip;

import java.time.ZoneId;
import javax.annotation.Nullable;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.trip.handlers.AddNewTripHandler;
import org.opentripplanner.updater.trip.handlers.AddNewTripValidator;
import org.opentripplanner.updater.trip.handlers.CancelTripHandler;
import org.opentripplanner.updater.trip.handlers.DeleteTripHandler;
import org.opentripplanner.updater.trip.handlers.ModifyTripHandler;
import org.opentripplanner.updater.trip.handlers.ModifyTripValidator;
import org.opentripplanner.updater.trip.handlers.RouteCreationStrategy;
import org.opentripplanner.updater.trip.handlers.UpdateAddedTripHandler;
import org.opentripplanner.updater.trip.handlers.UpdateExistingTripHandler;
import org.opentripplanner.updater.trip.handlers.UpdateExistingTripValidator;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;

/**
 * Composition root for {@link DefaultTripUpdateApplier}: wires the shared resolvers, the per-type
 * validators and the per-type handlers. This is plain manual DI (the {@code updater.trip} package
 * uses no Dagger).
 */
public final class TripUpdateApplierFactory {

  private TripUpdateApplierFactory() {}

  public static DefaultTripUpdateApplier create(
    String feedId,
    ZoneId timeZone,
    TransitEditorService transitService,
    DeduplicatorService deduplicator,
    @Nullable TimetableSnapshotManager snapshotManager,
    TripPatternCache tripPatternCache,
    FuzzyTripMatcher fuzzyTripMatcher,
    RouteCreationStrategy routeCreationStrategy
  ) {
    // Shared resolvers
    var tripResolver = new TripResolver(transitService);
    var serviceDateResolver = new ServiceDateResolver(tripResolver, transitService);
    var stopResolver = new StopResolver(transitService);

    var existingTripResolver = new ExistingTripResolver(
      transitService,
      tripResolver,
      serviceDateResolver,
      stopResolver,
      fuzzyTripMatcher,
      timeZone
    );
    var newTripResolver = new NewTripResolver(
      transitService,
      serviceDateResolver,
      stopResolver,
      timeZone
    );
    var tripRemovalResolver = new TripRemovalResolver(
      transitService,
      tripResolver,
      serviceDateResolver,
      snapshotManager
    );

    // Validators
    var updateExistingValidator = new UpdateExistingTripValidator();
    var modifyTripValidator = new ModifyTripValidator();
    var addNewTripValidator = new AddNewTripValidator();

    // Handlers - pure transformers, no snapshot manager dependency
    var updateExistingHandler = new UpdateExistingTripHandler(tripPatternCache);
    var modifyTripHandler = new ModifyTripHandler(transitService, deduplicator, tripPatternCache);
    var addNewTripHandler = new AddNewTripHandler(
      feedId,
      transitService,
      deduplicator,
      tripPatternCache,
      routeCreationStrategy
    );
    var updateAddedTripHandler = new UpdateAddedTripHandler();
    var cancelTripHandler = new CancelTripHandler();
    var deleteTripHandler = new DeleteTripHandler();

    return new DefaultTripUpdateApplier(
      transitService,
      existingTripResolver,
      newTripResolver,
      tripRemovalResolver,
      updateExistingValidator,
      modifyTripValidator,
      addNewTripValidator,
      updateExistingHandler,
      modifyTripHandler,
      addNewTripHandler,
      updateAddedTripHandler,
      cancelTripHandler,
      deleteTripHandler
    );
  }
}
