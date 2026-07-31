package org.opentripplanner.updater.trip.siri;

import java.util.List;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.trip.UpdateIncrementality;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;

/**
 * Update-scoped task produced by {@link SiriTripUpdateAdapter#forUpdate}. Applies SIRI-ET
 * estimated timetables to the mutable timetable snapshot of the current update task.
 */
public interface SiriTripUpdateHandler {
  /**
   * Apply estimated timetables to the timetable snapshot.
   *
   * @param entityResolver Entity resolver for the feed
   * @param feedId         The feed ID
   * @param incrementality The incrementality of the update
   * @param updates        The SIRI EstimatedTimetable deliveries
   * @return Result of applying the updates
   */
  UpdateResult applyEstimatedTimetable(
    EntityResolver entityResolver,
    String feedId,
    UpdateIncrementality incrementality,
    List<EstimatedTimetableDeliveryStructure> updates
  );
}
