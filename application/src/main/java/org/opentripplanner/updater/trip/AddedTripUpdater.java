package org.opentripplanner.updater.trip;

import java.time.LocalDate;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.updater.spi.DataValidationExceptionMapper;
import org.opentripplanner.updater.trip.model.ResolvedAddedTripUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Updates a previously added real-time trip: the same trip is sent again as
 * ADD_NEW_TRIP after it has already been integrated in the transit model (subsequent updates
 * to an extra journey). {@link TripAdder} routes these updates here.
 * <p>
 * The existing trip and pattern are reused verbatim; only the trip times are rebuilt from the
 * baseline times and the incoming call data.
 */
public class AddedTripUpdater {

  private static final Logger LOG = LoggerFactory.getLogger(AddedTripUpdater.class);

  public TripUpdateResult update(ResolvedAddedTripUpdate resolvedUpdate) {
    TripPattern pattern = resolvedUpdate.pattern();
    var tripTimes = resolvedUpdate.tripTimes();
    LocalDate serviceDate = resolvedUpdate.serviceDate();
    FeedScopedId tripId = resolvedUpdate.trip().getId();

    LOG.debug("Updating existing added trip {} on {}", tripId, serviceDate);

    // Filter stop time updates
    var filteredUpdates = StopTimeUpdates.filterUnknownStops(resolvedUpdate.stopTimeUpdates());

    // Create real-time trip times from the baseline times
    var builder = tripTimes.createRealTimeFromScheduledTimes();
    // A journey-level cancellation of an already-added trip is a clean cancellation: keep the
    // scheduled times and do not re-apply the real-time call data, so the previously applied
    // real-time flags are dropped (matching the legacy ModifiedTripBuilder.cancelTrip behaviour).
    if (!resolvedUpdate.isCancellation()) {
      StopTimeUpdates.applyRealTimeUpdates(
        resolvedUpdate.tripCreationInfo(),
        builder,
        filteredUpdates.updates()
      );
    }
    // Extra journeys always keep the "added" flag, even when all stops are cancelled,
    // because they were never part of the static schedule.
    builder.withAdded();
    if (resolvedUpdate.isCancellation() || resolvedUpdate.isAllStopsCancelled()) {
      builder.withCanceled();
    }

    // Build and return result
    // tripCreation=false since this is an update to an existing added trip
    // routeCreation=false since the route already exists
    try {
      var realTimeTripUpdate = RealTimeTripUpdate.of(pattern, builder.build(), serviceDate)
        .withProducer(resolvedUpdate.dataSource())
        .withRevertPreviousRealTimeUpdates(true)
        .build();

      LOG.debug("Updated existing added trip {} on {}", tripId, serviceDate);
      return new TripUpdateResult(realTimeTripUpdate, filteredUpdates.warnings());
    } catch (DataValidationException e) {
      LOG.info("Invalid real-time data for updated added trip {}: {}", tripId, e.getMessage());
      throw DataValidationExceptionMapper.map(e);
    }
  }
}
