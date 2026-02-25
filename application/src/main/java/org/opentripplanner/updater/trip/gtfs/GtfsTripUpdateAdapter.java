package org.opentripplanner.updater.trip.gtfs;

import com.google.transit.realtime.GtfsRealtime;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.trip.UpdateIncrementality;

/**
 * Interface for applying GTFS-RT TripUpdate messages to the transit model.
 * This allows switching between the old and new implementations.
 */
public interface GtfsTripUpdateAdapter {
  /**
   * Apply GTFS-RT trip updates to the timetable snapshot.
   *
   * @param fuzzyTripMatcher Optional fuzzy trip matcher for matching trips
   * @param forwardsDelayPropagationType How to propagate delays forward
   * @param backwardsDelayPropagationType How to propagate delays backward
   * @param updateIncrementality Whether this is a full dataset or differential update
   * @param updates The GTFS-RT TripUpdate messages
   * @param feedId The feed ID
   * @return Result of applying the updates
   */
  UpdateResult applyTripUpdates(
    @Nullable GtfsRealtimeFuzzyTripMatcher fuzzyTripMatcher,
    ForwardsDelayPropagationType forwardsDelayPropagationType,
    BackwardsDelayPropagationType backwardsDelayPropagationType,
    UpdateIncrementality updateIncrementality,
    List<GtfsRealtime.TripUpdate> updates,
    String feedId
  );
}
