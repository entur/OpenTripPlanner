package org.opentripplanner.updater.trip.siri;

import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.trip.UpdateIncrementality;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;

/**
 * Interface for applying SIRI EstimatedTimetable updates to the transit model.
 * This allows switching between the old and new implementations.
 */
public interface SiriTripUpdateAdapter {
  /**
   * Apply estimated timetables to the timetable snapshot.
   *
   * @param fuzzyTripMatcher Optional fuzzy trip matcher for matching trips
   * @param entityResolver Entity resolver for the feed
   * @param feedId The feed ID
   * @param incrementality The incrementality of the update
   * @param updates The SIRI EstimatedTimetable deliveries
   * @return Result of applying the updates
   */
  UpdateResult applyEstimatedTimetable(
    @Nullable SiriFuzzyTripMatcher fuzzyTripMatcher,
    EntityResolver entityResolver,
    String feedId,
    UpdateIncrementality incrementality,
    List<EstimatedTimetableDeliveryStructure> updates
  );
}
