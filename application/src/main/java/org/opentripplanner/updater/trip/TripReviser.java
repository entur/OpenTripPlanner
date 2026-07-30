package org.opentripplanner.updater.trip;

import java.util.Objects;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.updater.spi.DataValidationExceptionMapper;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.TripRevision;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Revises an existing scheduled trip with real-time data: delays, changed times and minor
 * pattern adjustments such as replaced stops or pick/drop changes.
 * Maps to GTFS-RT SCHEDULED and SIRI-ET regular updates.
 * <p>
 * The revision arrives fully specified and validated by the {@link ExistingTripChangeFactory}
 * and applies itself through {@link TripRevision#apply} - this class supplies the pattern lookup
 * it needs and translates invalid real-time data into an update error.
 */
public class TripReviser {

  private static final Logger LOG = LoggerFactory.getLogger(TripReviser.class);

  private final TripPatternCache tripPatternCache;

  public TripReviser(TripPatternCache tripPatternCache) {
    this.tripPatternCache = Objects.requireNonNull(tripPatternCache);
  }

  public TripUpdateResult revise(TripRevision revision) throws UpdateException {
    var tripId = revision.trip().getId();
    var serviceDate = revision.serviceDate();

    LOG.debug(
      "Updating trip {} on pattern {} for date {}",
      tripId,
      revision.pattern().getId(),
      serviceDate
    );
    try {
      var result = revision.apply(tripPatternCache::getOrCreateTripPattern);
      LOG.debug(
        "Updated trip {} on {} (pattern {})",
        tripId,
        serviceDate,
        result.pattern().getId()
      );
      return result;
    } catch (DataValidationException e) {
      LOG.info(
        "Invalid real-time data for trip {} - TripTimes failed to validate. {}",
        tripId,
        e.getMessage()
      );
      throw DataValidationExceptionMapper.map(e);
    }
  }
}
