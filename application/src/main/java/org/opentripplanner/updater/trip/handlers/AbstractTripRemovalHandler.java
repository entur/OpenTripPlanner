package org.opentripplanner.updater.trip.handlers;

import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.updater.trip.model.ResolvedTripRemoval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for handlers that remove trips (cancel or delete).
 * <p>
 * Uses pre-resolved data from {@link ResolvedTripRemoval}:
 * <ul>
 *   <li>If the resolver found a previously real-time added trip (extra journey), it is available
 *       via {@code addedTripPattern}/{@code addedTripTimes} and the {@code added} flag is
 *       preserved in the result.</li>
 *   <li>Otherwise, the handler uses the scheduled trip data and sets
 *       {@code revertPreviousRealTimeUpdates=true} to clear any prior RT modifications.</li>
 * </ul>
 * Note: extra call cancellations (SIRI messages with extra calls AND {@code isCancellation=true})
 * are handled by {@link ModifyTripHandler}, not this class.
 */
public abstract class AbstractTripRemovalHandler implements TripUpdateHandler.ForTripRemoval {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractTripRemovalHandler.class);

  @Override
  public final TripUpdateResult handle(ResolvedTripRemoval resolvedUpdate) {
    var serviceDate = resolvedUpdate.serviceDate();
    var tripId = resolvedUpdate.tripId();

    // First, check for a previously added or RT-modified trip (pre-resolved by TripRemovalResolver)
    var addedTripPattern = resolvedUpdate.addedTripPattern();
    var addedTripTimes = resolvedUpdate.addedTripTimes();

    if (addedTripPattern != null && addedTripTimes != null) {
      // Previously added (extra journey) trip: preserve the "added" state flag.
      var builder = addedTripTimes.createRealTimeFromScheduledTimes();
      applyRemoval(builder);
      builder.withAdded();

      var realTimeTripUpdate = RealTimeTripUpdate.of(addedTripPattern, builder.build(), serviceDate)
        .withProducer(resolvedUpdate.dataSource())
        .build();

      LOG.debug("{} previously added trip {} on {}", getLogAction(), tripId, serviceDate);
      return new TripUpdateResult(realTimeTripUpdate);
    }

    // Not a previously added trip - use scheduled trip from resolved data
    var trip = resolvedUpdate.scheduledTrip();
    var pattern = resolvedUpdate.scheduledPattern();
    var tripTimes = resolvedUpdate.scheduledTripTimes();

    // Create the modified trip times and apply removal
    var builder = tripTimes.createRealTimeFromScheduledTimes();
    applyRemoval(builder);

    var realTimeTripUpdate = RealTimeTripUpdate.of(pattern, builder.build(), serviceDate)
      .withProducer(resolvedUpdate.dataSource())
      .withRevertPreviousRealTimeUpdates(true)
      .build();

    LOG.debug("{} trip {} on {}", getLogAction(), trip.getId(), serviceDate);

    return new TripUpdateResult(realTimeTripUpdate);
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
