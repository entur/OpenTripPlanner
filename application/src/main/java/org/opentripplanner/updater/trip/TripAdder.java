package org.opentripplanner.updater.trip;

import java.util.Objects;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ResolvedAddedTripUpdate;
import org.opentripplanner.updater.trip.model.ResolvedTripCreation;
import org.opentripplanner.updater.trip.model.TripAddition;

/**
 * Adds a trip that is not part of the static schedule (a GTFS-RT NEW/ADDED trip or a SIRI-ET
 * extra journey).
 * <p>
 * The {@link NewTripResolver} decides whether the message is the first occurrence of the trip
 * or a subsequent update to a trip added earlier: the first is created by the
 * {@link TripCreator}, the latter is updated by the {@link AddedTripUpdater}.
 */
public class TripAdder {

  private final NewTripResolver resolver;
  private final TripCreator tripCreator;
  private final AddedTripUpdater addedTripUpdater;

  public TripAdder(
    NewTripResolver resolver,
    TripCreator tripCreator,
    AddedTripUpdater addedTripUpdater
  ) {
    this.resolver = Objects.requireNonNull(resolver);
    this.tripCreator = Objects.requireNonNull(tripCreator);
    this.addedTripUpdater = Objects.requireNonNull(addedTripUpdater);
  }

  public TripUpdateResult add(TripAddition parsedUpdate) throws UpdateException {
    return switch (resolver.resolve(parsedUpdate)) {
      case ResolvedTripCreation creation -> tripCreator.create(creation);
      case ResolvedAddedTripUpdate update -> addedTripUpdater.update(update);
    };
  }
}
