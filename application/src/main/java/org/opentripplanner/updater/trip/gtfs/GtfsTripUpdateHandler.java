package org.opentripplanner.updater.trip.gtfs;

import com.google.transit.realtime.GtfsRealtime;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.trip.UpdateIncrementality;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;

/**
 * Update-scoped task produced by {@link GtfsTripUpdateAdapter#forUpdate}. Applies GTFS-RT trip
 * updates to the mutable timetable snapshot of the current update task.
 */
public interface GtfsTripUpdateHandler {
  /**
   * Apply GTFS-RT trip updates to the timetable snapshot.
   *
   * @param fuzzyTripMatcher              Optional fuzzy trip matcher for matching trips
   * @param forwardsDelayPropagationType  How to propagate delays forward
   * @param backwardsDelayPropagationType How to propagate delays backward
   * @param updateIncrementality          Whether this is a full dataset or differential update
   * @param updates                       The GTFS-RT TripUpdate messages
   * @param feedId                        The feed ID
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
