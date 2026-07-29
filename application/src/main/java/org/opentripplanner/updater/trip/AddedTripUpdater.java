package org.opentripplanner.updater.trip;

import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.updater.spi.DataValidationExceptionMapper;
import org.opentripplanner.updater.trip.model.ResolvedAddedTripUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Updates a previously added real-time trip: the same trip is sent again as
 * ADD_NEW_TRIP after it has already been integrated in the transit model (subsequent updates
 * to an extra journey). The {@link NewTripResolver} recognises them as such.
 * <p>
 * The update itself is applied by {@link ResolvedAddedTripUpdate#apply()} - it needs nothing but
 * its own resolved state. This class only translates invalid real-time data into an update error.
 */
public class AddedTripUpdater {

  private static final Logger LOG = LoggerFactory.getLogger(AddedTripUpdater.class);

  public TripUpdateResult update(ResolvedAddedTripUpdate resolvedUpdate) {
    var tripId = resolvedUpdate.tripId();
    var serviceDate = resolvedUpdate.serviceDate();

    LOG.debug("Updating existing added trip {} on {}", tripId, serviceDate);
    try {
      var result = resolvedUpdate.apply();
      LOG.debug("Updated existing added trip {} on {}", tripId, serviceDate);
      return result;
    } catch (DataValidationException e) {
      LOG.info("Invalid real-time data for updated added trip {}: {}", tripId, e.getMessage());
      throw DataValidationExceptionMapper.map(e);
    }
  }
}
