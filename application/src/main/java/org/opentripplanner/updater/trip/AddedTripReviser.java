package org.opentripplanner.updater.trip;

import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.updater.spi.DataValidationExceptionMapper;
import org.opentripplanner.updater.trip.model.AddedTripRevision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Revises a previously added real-time trip: the same trip is sent again as
 * ADD_NEW_TRIP after it has already been integrated in the transit model (subsequent updates
 * to an extra journey). The {@link TripAdditionFactory} recognises them as such.
 * <p>
 * The revision applies itself through {@link AddedTripRevision#apply()} - it needs nothing but
 * its own resolved state. This class only translates invalid real-time data into an update error.
 */
public class AddedTripReviser {

  private static final Logger LOG = LoggerFactory.getLogger(AddedTripReviser.class);

  public TripUpdateResult revise(AddedTripRevision revision) {
    var tripId = revision.tripId();
    var serviceDate = revision.serviceDate();

    LOG.debug("Revising added trip {} on {}", tripId, serviceDate);
    try {
      var result = revision.apply();
      LOG.debug("Revised added trip {} on {}", tripId, serviceDate);
      return result;
    } catch (DataValidationException e) {
      LOG.info("Invalid real-time data for added trip {}: {}", tripId, e.getMessage());
      throw DataValidationExceptionMapper.map(e);
    }
  }
}
