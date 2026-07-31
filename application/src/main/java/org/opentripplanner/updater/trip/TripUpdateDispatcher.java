package org.opentripplanner.updater.trip;

import java.time.ZoneId;
import java.util.Objects;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.factory.ExistingTripChangeFactory;
import org.opentripplanner.updater.trip.factory.RouteCreationStrategy;
import org.opentripplanner.updater.trip.factory.TripAdditionFactory;
import org.opentripplanner.updater.trip.factory.TripDuplicationFactory;
import org.opentripplanner.updater.trip.factory.TripRemovalFactory;
import org.opentripplanner.updater.trip.model.change.AddedTripRevision;
import org.opentripplanner.updater.trip.model.change.TripAddition;
import org.opentripplanner.updater.trip.model.change.TripCreation;
import org.opentripplanner.updater.trip.model.change.TripUpdateResult;
import org.opentripplanner.updater.trip.model.command.AddTrip;
import org.opentripplanner.updater.trip.model.command.CancelTrip;
import org.opentripplanner.updater.trip.model.command.DeleteTrip;
import org.opentripplanner.updater.trip.model.command.DuplicateTrip;
import org.opentripplanner.updater.trip.model.command.ModifyTrip;
import org.opentripplanner.updater.trip.model.command.ReviseTrip;
import org.opentripplanner.updater.trip.model.command.TripUpdateCommand;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;
import org.opentripplanner.updater.trip.resolver.FuzzyTripMatcher;
import org.opentripplanner.updater.trip.resolver.ServiceDateResolver;
import org.opentripplanner.updater.trip.resolver.StopResolver;
import org.opentripplanner.updater.trip.resolver.TripResolver;
import org.opentripplanner.updater.trip.service.AddedTripReviser;
import org.opentripplanner.updater.trip.service.TripCanceller;
import org.opentripplanner.updater.trip.service.TripCreator;
import org.opentripplanner.updater.trip.service.TripDeleter;
import org.opentripplanner.updater.trip.service.TripDuplicator;
import org.opentripplanner.updater.trip.service.TripModifier;
import org.opentripplanner.updater.trip.service.TripReviser;

/**
 * Executes trip update commands: resolves each command into the change it asks for, and has the
 * domain service for its type carry the change out. This is the unified component shared by both
 * SIRI-ET and GTFS-RT updaters.
 * <p>
 * Build a fully wired instance with {@link #create}.
 * <p>
 * Executing a command happens in two steps. First the dispatcher pattern-matches on the sealed
 * {@link TripUpdateCommand} hierarchy and hands the command to the factory for its type, which
 * resolves it against the transit model into a validated change:
 * <ul>
 *   <li>{@link ReviseTrip}, {@link ModifyTrip} → {@link ExistingTripChangeFactory}</li>
 *   <li>{@link AddTrip} → {@link TripAdditionFactory}</li>
 *   <li>{@link CancelTrip}, {@link DeleteTrip} → {@link TripRemovalFactory}</li>
 *   <li>{@link DuplicateTrip} → {@link TripDuplicationFactory}</li>
 * </ul>
 * The change applies itself; the domain service for its type supplies the shared resources it
 * needs and drives it:
 * <ul>
 *   <li>{@link ReviseTrip} → {@link TripReviser}</li>
 *   <li>{@link ModifyTrip} → {@link TripModifier}</li>
 *   <li>{@link AddTrip} → {@link TripCreator} or {@link AddedTripReviser}</li>
 *   <li>{@link CancelTrip} → {@link TripCanceller}</li>
 *   <li>{@link DeleteTrip} → {@link TripDeleter}</li>
 *   <li>{@link DuplicateTrip} → {@link TripDuplicator}</li>
 * </ul>
 * Each domain service produces the {@link TripUpdateResult} to be written to the timetable
 * snapshot buffer by the calling adapter.
 */
public class TripUpdateDispatcher {

  private final ExistingTripChangeFactory existingTripChangeFactory;
  private final TripAdditionFactory tripAdditionFactory;
  private final TripRemovalFactory tripRemovalFactory;
  private final TripDuplicationFactory tripDuplicationFactory;

  private final TripReviser tripReviser;
  private final TripModifier tripModifier;
  private final TripCreator tripCreator;
  private final TripDuplicator tripDuplicator;

  // These services need nothing but the change they are given.
  private final AddedTripReviser addedTripReviser = new AddedTripReviser();
  private final TripCanceller tripCanceller = new TripCanceller();
  private final TripDeleter tripDeleter = new TripDeleter();

  /**
   * Wires the change factories and the pre-built domain services. Package-private; use
   * {@link #create} to obtain a fully wired instance.
   */
  TripUpdateDispatcher(
    ExistingTripChangeFactory existingTripChangeFactory,
    TripAdditionFactory tripAdditionFactory,
    TripRemovalFactory tripRemovalFactory,
    TripDuplicationFactory tripDuplicationFactory,
    TripReviser tripReviser,
    TripModifier tripModifier,
    TripCreator tripCreator,
    TripDuplicator tripDuplicator
  ) {
    this.existingTripChangeFactory = Objects.requireNonNull(existingTripChangeFactory);
    this.tripAdditionFactory = Objects.requireNonNull(tripAdditionFactory);
    this.tripRemovalFactory = Objects.requireNonNull(tripRemovalFactory);
    this.tripDuplicationFactory = Objects.requireNonNull(tripDuplicationFactory);
    this.tripReviser = Objects.requireNonNull(tripReviser);
    this.tripModifier = Objects.requireNonNull(tripModifier);
    this.tripCreator = Objects.requireNonNull(tripCreator);
    this.tripDuplicator = Objects.requireNonNull(tripDuplicator);
  }

  /**
   * Composition root: wires the change factories and the per-type domain services. This is
   * plain manual DI (the {@code updater.trip} package uses no Dagger).
   */
  public static TripUpdateDispatcher create(
    ZoneId timeZone,
    TransitEditorService transitService,
    DeduplicatorService deduplicator,
    TripPatternCache tripPatternCache,
    FuzzyTripMatcher fuzzyTripMatcher,
    RouteCreationStrategy routeCreationStrategy
  ) {
    // Reference resolvers shared by the change factories
    var tripResolver = new TripResolver(transitService);
    var serviceDateResolver = new ServiceDateResolver(tripResolver, transitService);
    var stopResolver = new StopResolver(transitService);

    // Per-type change factories
    var existingTripChangeFactory = new ExistingTripChangeFactory(
      transitService,
      tripResolver,
      serviceDateResolver,
      stopResolver,
      fuzzyTripMatcher,
      timeZone
    );
    var tripAdditionFactory = new TripAdditionFactory(
      transitService,
      serviceDateResolver,
      stopResolver,
      routeCreationStrategy,
      timeZone
    );
    var tripRemovalFactory = new TripRemovalFactory(
      transitService,
      tripResolver,
      serviceDateResolver
    );
    var tripDuplicationFactory = new TripDuplicationFactory(transitService);

    return new TripUpdateDispatcher(
      existingTripChangeFactory,
      tripAdditionFactory,
      tripRemovalFactory,
      tripDuplicationFactory,
      new TripReviser(tripPatternCache),
      new TripModifier(deduplicator, tripPatternCache),
      new TripCreator(deduplicator, tripPatternCache),
      new TripDuplicator(deduplicator)
    );
  }

  /**
   * Execute a trip update command by resolving it into the change it asks for and having the
   * domain service for its type carry the change out.
   *
   * @param command the format-independent command produced by the parser
   * @return the TripUpdateResult (with RealTimeTripUpdate and warnings)
   * @throws UpdateException if the command cannot be resolved or the change cannot be applied
   */
  public TripUpdateResult execute(TripUpdateCommand command) throws UpdateException {
    return switch (command) {
      case ReviseTrip u -> tripReviser.revise(existingTripChangeFactory.create(u));
      case ModifyTrip u -> tripModifier.modify(existingTripChangeFactory.create(u));
      case AddTrip u -> addTrip(tripAdditionFactory.create(u));
      case CancelTrip u -> tripCanceller.cancel(tripRemovalFactory.create(u));
      case DeleteTrip u -> tripDeleter.delete(tripRemovalFactory.create(u));
      case DuplicateTrip u -> tripDuplicator.duplicate(tripDuplicationFactory.create(u));
    };
  }

  /**
   * A message adding a trip that is not part of the static schedule either brings the trip into the
   * transit model for the first time, or revises a trip added by an earlier message. Which one it
   * is is state-dependent, so the {@link TripAdditionFactory} decides it and the addition carries
   * the answer.
   */
  private TripUpdateResult addTrip(TripAddition addition) {
    return switch (addition) {
      case TripCreation creation -> tripCreator.create(creation);
      case AddedTripRevision revision -> addedTripReviser.revise(revision);
    };
  }
}
