package org.opentripplanner.updater.trip;

import java.time.ZoneId;
import java.util.Objects;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.ScheduledTripUpdate;
import org.opentripplanner.updater.trip.model.TripAddition;
import org.opentripplanner.updater.trip.model.TripCancellation;
import org.opentripplanner.updater.trip.model.TripDeletion;
import org.opentripplanner.updater.trip.model.TripDuplication;
import org.opentripplanner.updater.trip.model.TripModification;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;

/**
 * Dispatches parsed trip updates to the domain operation that applies them to the transit model.
 * This is the unified component shared by both SIRI-ET and GTFS-RT updaters.
 * <p>
 * Build a fully wired instance with {@link #create}.
 * <p>
 * The dispatcher is pure routing: it pattern-matches on the sealed {@link ParsedTripUpdate}
 * hierarchy and delegates each update type to the domain operation that applies it:
 * <ul>
 *   <li>{@link ScheduledTripUpdate} → {@link ScheduledTripUpdater}</li>
 *   <li>{@link TripModification} → {@link TripModifier}</li>
 *   <li>{@link TripAddition} → {@link TripAdder}</li>
 *   <li>{@link TripCancellation} → {@link TripCanceller}</li>
 *   <li>{@link TripDeletion} → {@link TripDeleter}</li>
 *   <li>{@link TripDuplication} → {@link TripDuplicator}</li>
 * </ul>
 * Each domain operation anchors the parsed update to the transit model (via its resolver),
 * validates it, and produces the {@link TripUpdateResult} to be written to the timetable
 * snapshot buffer by the calling adapter.
 */
public class TripUpdateDispatcher {

  private final ScheduledTripUpdater scheduledTripUpdater;
  private final TripModifier tripModifier;
  private final TripAdder tripAdder;
  private final TripCanceller tripCanceller;
  private final TripDeleter tripDeleter;
  private final TripDuplicator tripDuplicator;

  /**
   * Wires the pre-built domain operations. Package-private; use {@link #create} to obtain a
   * fully wired instance.
   */
  TripUpdateDispatcher(
    ScheduledTripUpdater scheduledTripUpdater,
    TripModifier tripModifier,
    TripAdder tripAdder,
    TripCanceller tripCanceller,
    TripDeleter tripDeleter,
    TripDuplicator tripDuplicator
  ) {
    this.scheduledTripUpdater = Objects.requireNonNull(scheduledTripUpdater);
    this.tripModifier = Objects.requireNonNull(tripModifier);
    this.tripAdder = Objects.requireNonNull(tripAdder);
    this.tripCanceller = Objects.requireNonNull(tripCanceller);
    this.tripDeleter = Objects.requireNonNull(tripDeleter);
    this.tripDuplicator = Objects.requireNonNull(tripDuplicator);
  }

  /**
   * Composition root: wires the shared resolvers into the per-type domain operations. This is
   * plain manual DI (the {@code updater.trip} package uses no Dagger).
   */
  public static TripUpdateDispatcher create(
    String feedId,
    ZoneId timeZone,
    TransitEditorService transitService,
    DeduplicatorService deduplicator,
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
      serviceDateResolver
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

    return new TripUpdateDispatcher(
      new ScheduledTripUpdater(existingTripResolver, tripPatternCache),
      new TripModifier(existingTripResolver, transitService, deduplicator, tripPatternCache),
      new TripAdder(newTripResolver, tripCreator, new AddedTripUpdater()),
      new TripCanceller(tripRemovalResolver),
      new TripDeleter(tripRemovalResolver),
      new TripDuplicator(duplicateTripResolver, deduplicator)
    );
  }

  /**
   * Apply a parsed trip update by dispatching it to the matching domain operation.
   *
   * @param parsedUpdate The format-independent parsed update
   * @return The TripUpdateResult (with RealTimeTripUpdate and warnings)
   * @throws UpdateException if the update cannot be applied
   */
  public TripUpdateResult apply(ParsedTripUpdate parsedUpdate) throws UpdateException {
    return switch (parsedUpdate) {
      case ScheduledTripUpdate u -> scheduledTripUpdater.update(u);
      case TripModification u -> tripModifier.modify(u);
      case TripAddition u -> tripAdder.add(u);
      case TripCancellation u -> tripCanceller.cancel(u);
      case TripDeletion u -> tripDeleter.delete(u);
      case TripDuplication u -> tripDuplicator.duplicate(u);
    };
  }
}
