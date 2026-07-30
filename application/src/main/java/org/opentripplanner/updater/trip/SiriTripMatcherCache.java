package org.opentripplanner.updater.trip;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.transit.service.TransitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduled-data cache for {@link SiriTripMatcher}. The cache only contains scheduled data, so it
 * is application-scoped and shared by the update-scoped matchers: it is built once, lazily on the
 * first fuzzy match, and reused across all update tasks.
 */
public class SiriTripMatcherCache {

  private static final Logger LOG = LoggerFactory.getLogger(SiriTripMatcherCache.class);

  private final TimetableRepository timetableRepository;

  // Cache: (stopId:arrivalTime) -> Set<Trip>
  private final Map<String, Set<Trip>> lastStopArrivalCache = new HashMap<>();
  // Cache: internalPlanningCode -> Set<Trip> (for RAIL trips)
  private final Map<String, Set<Trip>> internalPlanningCodeCache = new HashMap<>();
  private volatile boolean initialized = false;

  public SiriTripMatcherCache(TimetableRepository timetableRepository) {
    this.timetableRepository = Objects.requireNonNull(timetableRepository);
  }

  /**
   * Return the scheduled trips arriving at the given stop at the given arrival time, or an empty
   * set if there are none.
   */
  Set<Trip> tripsByLastStopArrival(StopLocation stop, int arrivalTimeSeconds) {
    ensureInitialized();
    return lastStopArrivalCache.getOrDefault(createCacheKey(stop, arrivalTimeSeconds), Set.of());
  }

  /**
   * Return the scheduled RAIL trips with the given NeTEx internal planning code, or an empty set
   * if there are none.
   */
  Set<Trip> tripsByInternalPlanningCode(String internalPlanningCode) {
    ensureInitialized();
    return internalPlanningCodeCache.getOrDefault(internalPlanningCode, Set.of());
  }

  private void ensureInitialized() {
    if (initialized) {
      return;
    }
    synchronized (this) {
      if (initialized) {
        return;
      }
      initCache(new DefaultTransitService(timetableRepository, null));
      initialized = true;
    }
  }

  private void initCache(TransitService transitService) {
    for (Trip trip : transitService.listTrips()) {
      TripPattern tripPattern = transitService.findPattern(trip);
      if (tripPattern == null) {
        continue;
      }

      String lastStopId = tripPattern.lastStop().getId().getId();
      TripTimes tripTimes = tripPattern.getScheduledTimetable().getTripTimes(trip);
      if (tripTimes != null) {
        int arrivalTime = tripTimes.getArrivalTime(tripTimes.getNumStops() - 1);
        String key = createCacheKey(lastStopId, arrivalTime);
        lastStopArrivalCache.computeIfAbsent(key, k -> new HashSet<>()).add(trip);
      }

      if (tripPattern.getRoute().getMode().equals(TransitMode.RAIL)) {
        String planningCode = trip.getNetexInternalPlanningCode();
        if (planningCode != null) {
          internalPlanningCodeCache.computeIfAbsent(planningCode, k -> new HashSet<>()).add(trip);
        }
      }
    }
    LOG.info(
      "Built last-stop-arrival cache with {} entries, planning code cache with {} entries",
      lastStopArrivalCache.size(),
      internalPlanningCodeCache.size()
    );
  }

  private static String createCacheKey(String stopId, int arrivalTimeSeconds) {
    return stopId + ":" + arrivalTimeSeconds;
  }

  private static String createCacheKey(StopLocation stop, int arrivalTimeSeconds) {
    return createCacheKey(stop.getId().getId(), arrivalTimeSeconds);
  }
}
