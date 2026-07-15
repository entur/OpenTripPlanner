package org.opentripplanner.updater.trip;

import java.time.ZoneId;
import javax.annotation.Nullable;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;

/**
 * Composition root for {@link DefaultTripUpdateApplier}: wires the shared resolvers into the
 * per-type domain operations ({@link TripReviser}, {@link TripModifier}, {@link TripAdder},
 * {@link TripCanceller}, {@link TripDeleter}, {@link TripDuplicator}). This is plain manual DI
 * (the {@code updater.trip} package uses no Dagger).
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
    var duplicateTripResolver = new DuplicateTripResolver(transitService);

    // Per-type domain operations
    var tripCreator = new TripCreator(
      feedId,
      transitService,
      deduplicator,
      tripPatternCache,
      routeCreationStrategy
    );

    return new DefaultTripUpdateApplier(
      new TripReviser(existingTripResolver, tripPatternCache),
      new TripModifier(existingTripResolver, transitService, deduplicator, tripPatternCache),
      new TripAdder(newTripResolver, tripCreator, new AddedTripReviser()),
      new TripCanceller(tripRemovalResolver),
      new TripDeleter(tripRemovalResolver),
      new TripDuplicator(duplicateTripResolver, deduplicator)
    );
  }
}
