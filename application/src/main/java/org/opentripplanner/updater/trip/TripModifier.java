package org.opentripplanner.updater.trip;

import java.util.Objects;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.updater.spi.DataValidationExceptionMapper;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.TripModification;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modifies a trip by replacing its stop pattern (rerouting it on the requested service date).
 * <p>
 * This covers two use cases:
 * <ul>
 *   <li><b>GTFS-RT REPLACEMENT</b>: Complete stop pattern replacement with full freedom</li>
 *   <li><b>SIRI-ET EXTRA_CALL</b>: Insert extra stops, non-extra stops must match original</li>
 * </ul>
 * <p>
 * The modification arrives fully specified and validated by the
 * {@link ExistingTripChangeFactory} and applies itself through
 * {@link TripModification#apply} - this class supplies the collaborators it needs and
 * translates invalid real-time data into an update error.
 */
public class TripModifier {

  private static final Logger LOG = LoggerFactory.getLogger(TripModifier.class);

  private final DeduplicatorService deduplicator;
  private final TripPatternCache tripPatternCache;

  public TripModifier(DeduplicatorService deduplicator, TripPatternCache tripPatternCache) {
    this.deduplicator = Objects.requireNonNull(deduplicator);
    this.tripPatternCache = Objects.requireNonNull(tripPatternCache);
  }

  public TripUpdateResult modify(TripModification modification) throws UpdateException {
    var tripId = modification.trip().getId();
    var serviceDate = modification.serviceDate();

    LOG.debug("Modifying trip {} on {}", tripId, serviceDate);
    try {
      var result = modification.apply(deduplicator, tripPatternCache::generatePatternId);
      LOG.debug("Modified trip {} on {}", tripId, serviceDate);
      return result;
    } catch (DataValidationException e) {
      LOG.info("Invalid real-time data for modified trip {}: {}", tripId, e.getMessage());
      throw DataValidationExceptionMapper.map(e);
    }
  }
}
