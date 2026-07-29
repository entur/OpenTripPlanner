package org.opentripplanner.updater.trip;

import java.time.ZoneId;
import java.util.Objects;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.ResolvedAddedTripUpdate;
import org.opentripplanner.updater.trip.model.ResolvedNewTrip;
import org.opentripplanner.updater.trip.model.ResolvedTripCreation;
import org.opentripplanner.updater.trip.model.ScheduledTripUpdate;
import org.opentripplanner.updater.trip.model.TripAddition;
import org.opentripplanner.updater.trip.model.TripCancellation;
import org.opentripplanner.updater.trip.model.TripDeletion;
import org.opentripplanner.updater.trip.model.TripDuplication;
import org.opentripplanner.updater.trip.model.TripModification;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;

/**
 * Anchors parsed trip updates to the transit model and dispatches them to the domain operation that
 * applies them. This is the unified component shared by both SIRI-ET and GTFS-RT updaters.
 * <p>
 * Build a fully wired instance with {@link #create}.
 * <p>
 * Applying an update happens in two steps. First the dispatcher pattern-matches on the sealed
 * {@link ParsedTripUpdate} hierarchy and hands the update to the resolver that anchors it to the
 * transit model:
 * <ul>
 *   <li>{@link ScheduledTripUpdate}, {@link TripModification} → {@link ExistingTripResolver}</li>
 *   <li>{@link TripAddition} → {@link NewTripResolver}</li>
 *   <li>{@link TripCancellation}, {@link TripDeletion} → {@link TripRemovalResolver}</li>
 *   <li>{@link TripDuplication} → {@link DuplicateTripResolver}</li>
 * </ul>
 * The resolved update is then applied by the matching domain operation:
 * <ul>
 *   <li>{@link ScheduledTripUpdate} → {@link ScheduledTripUpdater}</li>
 *   <li>{@link TripModification} → {@link TripModifier}</li>
 *   <li>{@link TripAddition} → {@link TripCreator} or {@link AddedTripUpdater}</li>
 *   <li>{@link TripCancellation} → {@link TripCanceller}</li>
 *   <li>{@link TripDeletion} → {@link TripDeleter}</li>
 *   <li>{@link TripDuplication} → {@link TripDuplicator}</li>
 * </ul>
 * Each domain operation validates the resolved update and produces the {@link TripUpdateResult} to
 * be written to the timetable snapshot buffer by the calling adapter.
 */
public class TripUpdateDispatcher {

  private final ExistingTripResolver existingTripResolver;
  private final NewTripResolver newTripResolver;
  private final TripRemovalResolver tripRemovalResolver;
  private final DuplicateTripResolver duplicateTripResolver;

  private final ScheduledTripUpdater scheduledTripUpdater;
  private final TripModifier tripModifier;
  private final TripCreator tripCreator;
  private final TripDuplicator tripDuplicator;

  // These operations need nothing but the resolved update they are given.
  private final AddedTripUpdater addedTripUpdater = new AddedTripUpdater();
  private final TripCanceller tripCanceller = new TripCanceller();
  private final TripDeleter tripDeleter = new TripDeleter();

  /**
   * Wires the resolvers and the pre-built domain operations. Package-private; use {@link #create}
   * to obtain a fully wired instance.
   */
  TripUpdateDispatcher(
    ExistingTripResolver existingTripResolver,
    NewTripResolver newTripResolver,
    TripRemovalResolver tripRemovalResolver,
    DuplicateTripResolver duplicateTripResolver,
    ScheduledTripUpdater scheduledTripUpdater,
    TripModifier tripModifier,
    TripCreator tripCreator,
    TripDuplicator tripDuplicator
  ) {
    this.existingTripResolver = Objects.requireNonNull(existingTripResolver);
    this.newTripResolver = Objects.requireNonNull(newTripResolver);
    this.tripRemovalResolver = Objects.requireNonNull(tripRemovalResolver);
    this.duplicateTripResolver = Objects.requireNonNull(duplicateTripResolver);
    this.scheduledTripUpdater = Objects.requireNonNull(scheduledTripUpdater);
    this.tripModifier = Objects.requireNonNull(tripModifier);
    this.tripCreator = Objects.requireNonNull(tripCreator);
    this.tripDuplicator = Objects.requireNonNull(tripDuplicator);
  }

  /**
   * Composition root: wires the shared resolvers and the per-type domain operations. This is
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
    // Resolvers shared by the per-type resolvers
    var tripResolver = new TripResolver(transitService);
    var serviceDateResolver = new ServiceDateResolver(tripResolver, transitService);
    var stopResolver = new StopResolver(transitService);

    // Per-type resolvers
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
      existingTripResolver,
      newTripResolver,
      tripRemovalResolver,
      duplicateTripResolver,
      new ScheduledTripUpdater(tripPatternCache),
      new TripModifier(deduplicator, tripPatternCache),
      tripCreator,
      new TripDuplicator(deduplicator)
    );
  }

  /**
   * Apply a parsed trip update by resolving it against the transit model and dispatching the
   * resolved update to the matching domain operation.
   *
   * @param parsedUpdate The format-independent parsed update
   * @return The TripUpdateResult (with RealTimeTripUpdate and warnings)
   * @throws UpdateException if the update cannot be resolved or applied
   */
  public TripUpdateResult apply(ParsedTripUpdate parsedUpdate) throws UpdateException {
    return switch (parsedUpdate) {
      case ScheduledTripUpdate u -> scheduledTripUpdater.update(existingTripResolver.resolve(u));
      case TripModification u -> tripModifier.modify(existingTripResolver.resolve(u));
      case TripAddition u -> addTrip(newTripResolver.resolve(u));
      case TripCancellation u -> tripCanceller.cancel(tripRemovalResolver.resolve(u));
      case TripDeletion u -> tripDeleter.delete(tripRemovalResolver.resolve(u));
      case TripDuplication u -> tripDuplicator.duplicate(duplicateTripResolver.resolve(u));
    };
  }

  /**
   * A message adding a trip that is not part of the static schedule either brings the trip into the
   * transit model for the first time, or updates a trip added by an earlier message. Which one it is
   * is state-dependent, so the {@link NewTripResolver} decides it and the resolved update carries
   * the answer.
   */
  private TripUpdateResult addTrip(ResolvedNewTrip resolvedUpdate) {
    return switch (resolvedUpdate) {
      case ResolvedTripCreation creation -> tripCreator.create(creation);
      case ResolvedAddedTripUpdate update -> addedTripUpdater.update(update);
    };
  }
}
