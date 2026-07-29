package org.opentripplanner.updater.trip;

import java.util.Objects;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.updater.spi.DataValidationExceptionMapper;
import org.opentripplanner.updater.trip.model.ResolvedTripCreation;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a brand-new trip that is not part of the static schedule.
 * Maps to GTFS-RT NEW/ADDED and SIRI-ET extra journeys.
 * <p>
 * The update itself is applied by {@link ResolvedTripCreation#apply} - this class supplies the
 * collaborators it needs and translates invalid real-time data into an update error.
 * Subsequent updates to a trip added earlier are resolved to a
 * {@link org.opentripplanner.updater.trip.model.ResolvedAddedTripUpdate} and updated by
 * {@link AddedTripUpdater}; the {@link NewTripResolver} decides which of the two applies.
 */
public class TripCreator {

  private static final Logger LOG = LoggerFactory.getLogger(TripCreator.class);

  private final DeduplicatorService deduplicator;
  private final TripPatternCache tripPatternCache;

  public TripCreator(DeduplicatorService deduplicator, TripPatternCache tripPatternCache) {
    this.deduplicator = Objects.requireNonNull(deduplicator);
    this.tripPatternCache = Objects.requireNonNull(tripPatternCache);
  }

  public TripUpdateResult create(ResolvedTripCreation resolvedUpdate) {
    var tripId = resolvedUpdate.tripId();
    var serviceDate = resolvedUpdate.serviceDate();

    LOG.debug("Adding trip {} on {}", tripId, serviceDate);
    try {
      var result = resolvedUpdate.apply(deduplicator, tripPatternCache::generatePatternId);
      LOG.debug("Added trip {} on {}", tripId, serviceDate);
      return result;
    } catch (DataValidationException e) {
      LOG.info("Invalid real-time data for added trip {}: {}", tripId, e.getMessage());
      throw DataValidationExceptionMapper.map(e);
    }
  }
}
