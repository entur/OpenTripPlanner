package org.opentripplanner.updater.trip.service;

import java.util.Objects;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.updater.trip.model.change.TripDuplication;
import org.opentripplanner.updater.trip.model.change.TripUpdateResult;

/**
 * Duplicates an existing scheduled trip at a new start time. Creates a copy of the
 * original trip with all stop times shifted to the new start time, added to the original
 * pattern on the requested service date.
 * <p>
 * Maps to GTFS-RT DUPLICATED. SIRI-ET has no equivalent concept.
 * <p>
 * The duplication arrives fully specified by the {@link org.opentripplanner.updater.trip.factory.TripDuplicationFactory TripDuplicationFactory}, which looks up
 * the original trip, its pattern and its scheduled times. It applies itself through
 * {@link TripDuplication#apply} - this class only supplies the deduplicator it needs.
 */
public class TripDuplicator {

  private final DeduplicatorService deduplicator;

  public TripDuplicator(DeduplicatorService deduplicator) {
    this.deduplicator = Objects.requireNonNull(deduplicator);
  }

  public TripUpdateResult duplicate(TripDuplication duplication) {
    return duplication.apply(deduplicator);
  }
}
