package org.opentripplanner.updater.trip;

import java.util.Objects;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.TripAddition;
import org.opentripplanner.updater.trip.model.TripCancellation;
import org.opentripplanner.updater.trip.model.TripDeletion;
import org.opentripplanner.updater.trip.model.TripDuplication;
import org.opentripplanner.updater.trip.model.TripModification;
import org.opentripplanner.updater.trip.model.TripRevision;

/**
 * Default implementation of TripUpdateApplier that applies parsed trip updates to the transit
 * model. This is the unified component shared by both SIRI-ET and GTFS-RT updaters.
 * <p>
 * Build a fully wired instance with {@link TripUpdateApplierFactory#create}.
 * <p>
 * The applier is a pure dispatcher: it pattern-matches on the sealed {@link ParsedTripUpdate}
 * hierarchy and delegates each update type to the domain operation that applies it:
 * <ul>
 *   <li>{@link TripRevision} → {@link TripReviser}</li>
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
public class DefaultTripUpdateApplier implements TripUpdateApplier {

  private final TripReviser tripReviser;
  private final TripModifier tripModifier;
  private final TripAdder tripAdder;
  private final TripCanceller tripCanceller;
  private final TripDeleter tripDeleter;
  private final TripDuplicator tripDuplicator;

  /**
   * Wires the pre-built domain operations. Package-private; use
   * {@link TripUpdateApplierFactory#create} to obtain a fully wired instance.
   */
  DefaultTripUpdateApplier(
    TripReviser tripReviser,
    TripModifier tripModifier,
    TripAdder tripAdder,
    TripCanceller tripCanceller,
    TripDeleter tripDeleter,
    TripDuplicator tripDuplicator
  ) {
    this.tripReviser = Objects.requireNonNull(tripReviser);
    this.tripModifier = Objects.requireNonNull(tripModifier);
    this.tripAdder = Objects.requireNonNull(tripAdder);
    this.tripCanceller = Objects.requireNonNull(tripCanceller);
    this.tripDeleter = Objects.requireNonNull(tripDeleter);
    this.tripDuplicator = Objects.requireNonNull(tripDuplicator);
  }

  @Override
  public TripUpdateResult apply(ParsedTripUpdate parsedUpdate) {
    return switch (parsedUpdate) {
      case TripRevision u -> tripReviser.revise(u);
      case TripModification u -> tripModifier.modify(u);
      case TripAddition u -> tripAdder.add(u);
      case TripCancellation u -> tripCanceller.cancel(u);
      case TripDeletion u -> tripDeleter.delete(u);
      case TripDuplication u -> tripDuplicator.duplicate(u);
    };
  }
}
