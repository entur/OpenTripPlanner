package org.opentripplanner.updater.trip.handlers;

import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ResolvedTripRemoval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for handlers that remove trips (cancel or delete).
 * <p>
 * Uses pre-resolved data from {@link ResolvedTripRemoval}: if the resolver found a previously
 * added (real-time) trip, it is available via {@code addedTripPattern}/{@code addedTripTimes}.
 * Otherwise, the handler falls back to the scheduled trip data.
 */
public abstract class AbstractTripRemovalHandler implements TripUpdateHandler.ForTripRemoval {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractTripRemovalHandler.class);

  @Override
  public final Result<TripUpdateResult, UpdateError> handle(ResolvedTripRemoval resolvedUpdate) {
    var serviceDate = resolvedUpdate.serviceDate();
    var tripId = resolvedUpdate.tripId();

    // First, check for a previously added (real-time) trip (pre-resolved by TripRemovalResolver)
    var addedTripPattern = resolvedUpdate.addedTripPattern();
    var addedTripTimes = resolvedUpdate.addedTripTimes();

    if (addedTripPattern != null && addedTripTimes != null) {
      var builder = addedTripTimes.createRealTimeFromScheduledTimes();
      applyRemoval(builder);

      var realTimeTripUpdate = new RealTimeTripUpdate(
        addedTripPattern,
        builder.build(),
        serviceDate,
        null,
        false,
        false,
        resolvedUpdate.dataSource()
      );

      LOG.debug("{} previously added trip {} on {}", getLogAction(), tripId, serviceDate);
      return Result.success(new TripUpdateResult(realTimeTripUpdate));
    }

    // Not a previously added trip - use scheduled trip from resolved data
    var trip = resolvedUpdate.scheduledTrip();
    var pattern = resolvedUpdate.scheduledPattern();
    var tripTimes = resolvedUpdate.scheduledTripTimes();

    // Create the modified trip times and apply removal
    var builder = tripTimes.createRealTimeFromScheduledTimes();
    applyRemoval(builder);

    var realTimeTripUpdate = new RealTimeTripUpdate(
      pattern,
      builder.build(),
      serviceDate,
      null,
      false,
      false,
      resolvedUpdate.dataSource(),
      true,
      null
    );

    LOG.debug("{} trip {} on {}", getLogAction(), trip.getId(), serviceDate);

    return Result.success(new TripUpdateResult(realTimeTripUpdate));
  }

  /**
   * Apply the specific removal operation to the trip times builder.
   * Subclasses implement this to call either cancelTrip() or deleteTrip().
   */
  protected abstract void applyRemoval(RealTimeTripTimesBuilder builder);

  /**
   * Get the action name for logging (e.g., "Cancelled" or "Deleted").
   */
  protected abstract String getLogAction();
}
