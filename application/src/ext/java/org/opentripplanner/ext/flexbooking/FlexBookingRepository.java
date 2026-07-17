package org.opentripplanner.ext.flexbooking;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.opentripplanner.transit.model.timetable.TripIdAndServiceDate;

/**
 * Holds the real-time booked tour of the single active vehicle serving a flexible trip, keyed by
 * flex trip id and service date. Written by the flex booking updater, read directly by routing
 * threads.
 *
 * <h2>Tour shape contract</h2>
 * A tour is stored as a {@link CarpoolTrip} whose ordered stops mean:
 * <ul>
 *   <li>{@code stops().getFirst()} — the vehicle's tour start / current position anchor. Its
 *       deviation budget is always {@link Duration#ZERO}: the anchor is never displaced, and the
 *       insertion machinery only inserts at positions &gt;= 1. Refreshed as the vehicle moves.</li>
 *   <li>Middle stops — the already-booked passenger pickups and dropoffs in visit order, each with
 *       coordinate, expected times, onboard count and the <em>remaining</em> per-stop deviation
 *       budget.</li>
 *   <li>{@code stops().getLast()} — the last booked dropoff (or a final depot call). Appending a
 *       new passenger after it is expressed by the routing side, which synthesizes an end anchor
 *       before running insertion; the repository stores only what the feed reported.</li>
 * </ul>
 * A vehicle with fewer than two booked calls has no commitments to protect and is therefore not
 * stored at all ({@link #removeTour(TripIdAndServiceDate)}); routing falls back to the static
 * NeTEx behavior when {@link #findTour(TripIdAndServiceDate)} is empty.
 *
 * <p>Implementations must be safe for concurrent single-writer/multi-reader use without external
 * synchronization.
 */
public interface FlexBookingRepository {
  /**
   * The active vehicle's booked tour for the given flex trip and service date, or empty when no
   * real-time tour is known — the caller must then fall back to the static flex behavior.
   * The trip id is the flex trip's underlying {@code Trip} id (the same id used by
   * {@code FlexIndex.getTripById}).
   */
  Optional<CarpoolTrip> findTour(TripIdAndServiceDate key);

  /**
   * All currently stored tours, for debugging and introspection.
   */
  Collection<CarpoolTrip> listTours();

  /**
   * Stores the tour for the given key, replacing any existing one — a flexible trip has exactly
   * one active vehicle per service date.
   */
  void upsertTour(TripIdAndServiceDate key, CarpoolTrip tour);

  /**
   * Removes the tour for the given key, if present. Called when the journey is cancelled or when
   * the vehicle no longer has enough booked calls to constitute a tour.
   */
  void removeTour(TripIdAndServiceDate key);

  /**
   * Removes tours whose latest end time lies more than {@code expiry} before {@code now}.
   * Implementations may throttle the sweep; the updater calls this on every poll.
   *
   * @return the number of tours removed
   */
  int removeExpiredTours(Instant now, Duration expiry);
}
