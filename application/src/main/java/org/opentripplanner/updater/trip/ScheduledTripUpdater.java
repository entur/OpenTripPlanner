package org.opentripplanner.updater.trip;

import java.util.Objects;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.updater.spi.DataValidationExceptionMapper;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ResolvedScheduledTripUpdate;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Updates an existing scheduled trip with real-time data: delays, changed times and minor
 * pattern adjustments such as replaced stops or pick/drop changes.
 * Maps to GTFS-RT SCHEDULED and SIRI-ET regular updates.
 * <p>
 * The update arrives already resolved to a trip in the transit model and validated by the
 * {@link ExistingTripResolver}. The update itself is applied by
 * {@link ResolvedScheduledTripUpdate#apply} - this class supplies the pattern lookup it needs and
 * translates invalid real-time data into an update error.
 */
public class ScheduledTripUpdater {

  private static final Logger LOG = LoggerFactory.getLogger(ScheduledTripUpdater.class);

  private final TripPatternCache tripPatternCache;

  public ScheduledTripUpdater(TripPatternCache tripPatternCache) {
    this.tripPatternCache = Objects.requireNonNull(tripPatternCache);
  }

  public TripUpdateResult update(ResolvedScheduledTripUpdate resolvedUpdate)
    throws UpdateException {
    var tripId = resolvedUpdate.trip().getId();
    var serviceDate = resolvedUpdate.serviceDate();

    LOG.debug(
      "Updating trip {} on pattern {} for date {}",
      tripId,
      resolvedUpdate.pattern().getId(),
      serviceDate
    );
    try {
      var result = resolvedUpdate.apply(tripPatternCache::getOrCreateTripPattern);
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
