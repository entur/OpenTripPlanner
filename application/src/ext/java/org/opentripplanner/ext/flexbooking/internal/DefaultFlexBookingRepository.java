package org.opentripplanner.ext.flexbooking.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.opentripplanner.ext.flexbooking.FlexBookingRepository;
import org.opentripplanner.transit.model.timetable.TripIdAndServiceDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultFlexBookingRepository implements FlexBookingRepository {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultFlexBookingRepository.class);

  /**
   * Minimum time between expiry sweeps. Tours expire on a multi-day timescale, so scanning the
   * repository on every poll is wasteful; sweeping at most this often keeps the work negligible
   * regardless of how short — or how many — the polling feeds are.
   */
  private static final Duration SWEEP_INTERVAL = Duration.ofHours(1);

  private final Map<TripIdAndServiceDate, CarpoolTrip> tours = new ConcurrentHashMap<>();

  /** The earliest instant at which the next expiry sweep is allowed to run. */
  private final AtomicReference<Instant> nextSweep = new AtomicReference<>(Instant.MIN);

  @Override
  public Optional<CarpoolTrip> findTour(TripIdAndServiceDate key) {
    return Optional.ofNullable(tours.get(key));
  }

  @Override
  public Collection<CarpoolTrip> listTours() {
    return tours.values();
  }

  @Override
  public void upsertTour(TripIdAndServiceDate key, CarpoolTrip tour) {
    CarpoolTrip existing = tours.put(key, tour);
    if (existing != null) {
      LOG.debug("Updated flex booking tour {} with {} stops", key, tour.stops().size());
    } else {
      LOG.debug("Added new flex booking tour {} with {} stops", key, tour.stops().size());
    }
  }

  @Override
  public void removeTour(TripIdAndServiceDate key) {
    CarpoolTrip removed = tours.remove(key);
    if (removed != null) {
      LOG.debug("Removed flex booking tour {}", key);
    } else {
      LOG.debug("Tried to remove unknown flex booking tour {}", key);
    }
  }

  @Override
  public int removeExpiredTours(Instant now, Duration expiry) {
    Instant allowedAt = nextSweep.get();
    if (now.isBefore(allowedAt) || !nextSweep.compareAndSet(allowedAt, now.plus(SWEEP_INTERVAL))) {
      return 0;
    }

    Instant expiryThreshold = now.minus(expiry);
    int removed = 0;
    for (Map.Entry<TripIdAndServiceDate, CarpoolTrip> entry : tours.entrySet()) {
      if (
        entry.getValue().latestEndTime().toInstant().isBefore(expiryThreshold) &&
        tours.remove(entry.getKey(), entry.getValue())
      ) {
        removed++;
      }
    }
    if (removed > 0) {
      LOG.debug("Removed {} flex booking tours that ended before {}", removed, expiryThreshold);
    }
    return removed;
  }
}
