package org.opentripplanner.ext.updater.trip.unified.regression;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RaptorTransitData;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Timetable;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripIdAndServiceDate;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;
import org.opentripplanner.transit.repository.MutableTimetableSnapshot;
import org.opentripplanner.transit.repository.ReadOnlyTimetableSnapshot;

/**
 * Forwarding decorator for a {@link MutableTimetableSnapshot} that adapts a batch-oriented handler
 * to the per-trip driving the shadow comparison needs. The shadow-comparison adapters wrap the
 * buffer of the primary (legacy) handler with this decorator, which
 * <ol>
 *   <li>records the last {@link RealTimeTripUpdate} passed to {@link #update}, so the record the
 *       primary writes for each trip can be compared with the one the unified path produces, and</li>
 *   <li>collapses the batch-level {@link #clear} to the first call of each batch.</li>
 * </ol>
 * The second point is what lets the shadow adapters pass the caller's real
 * {@link org.opentripplanner.updater.trip.UpdateIncrementality} into the primary handler. The
 * primary is invoked once per trip, so on a full-dataset batch it asks to clear the buffer before
 * every trip, which would discard everything applied earlier in the same batch. Ignoring all but
 * the first clear makes the repeated request idempotent, so the primary sees the incrementality
 * production gives it while the buffer is still cleared exactly once per batch.
 */
public class RecordingTimetableSnapshot implements MutableTimetableSnapshot {

  private final MutableTimetableSnapshot delegate;

  /** The feeds already cleared in the current batch. */
  private final Set<String> clearedFeeds = new HashSet<>();

  @Nullable
  private RealTimeTripUpdate lastUpdate;

  public RecordingTimetableSnapshot(MutableTimetableSnapshot delegate) {
    this.delegate = delegate;
  }

  /**
   * Return the last {@link RealTimeTripUpdate} passed to {@link #update} since the last call to
   * {@link #clearLastUpdate()}, or null if there was none.
   */
  @Nullable
  public RealTimeTripUpdate lastUpdate() {
    return lastUpdate;
  }

  public void clearLastUpdate() {
    this.lastUpdate = null;
  }

  /**
   * Start a new batch, re-arming the once-per-batch {@link #clear}. Called by the shadow adapters
   * before they start looping over the trips of a message.
   */
  public void startBatch() {
    clearedFeeds.clear();
  }

  @Override
  public void update(RealTimeTripUpdate realTimeTripUpdate) {
    this.lastUpdate = realTimeTripUpdate;
    delegate.update(realTimeTripUpdate);
  }

  @Override
  public ReadOnlyTimetableSnapshot createReadOnlySnapshot() {
    return delegate.createReadOnlySnapshot();
  }

  /**
   * Clear the feed, but only the first time it is asked for in the current batch. See the class
   * javadoc for why the repeated requests are ignored rather than passed on.
   */
  @Override
  public void clear(String feedId) {
    if (clearedFeeds.add(feedId)) {
      delegate.clear(feedId);
    }
  }

  @Override
  public boolean revertTripToScheduledTripPattern(FeedScopedId tripId, LocalDate serviceDate) {
    return delegate.revertTripToScheduledTripPattern(tripId, serviceDate);
  }

  @Override
  public boolean purgeExpiredData(LocalDate serviceDate) {
    return delegate.purgeExpiredData(serviceDate);
  }

  @Override
  public Timetable resolve(TripPattern pattern, @Nullable LocalDate serviceDate) {
    return delegate.resolve(pattern, serviceDate);
  }

  @Override
  @Nullable
  public TripPattern getNewTripPatternForModifiedTrip(FeedScopedId tripId, LocalDate serviceDate) {
    return delegate.getNewTripPatternForModifiedTrip(tripId, serviceDate);
  }

  @Override
  public List<TripOnServiceDate> listCanceledTrips() {
    return delegate.listCanceledTrips();
  }

  @Override
  public boolean hasNewTripPatternsForModifiedTrips() {
    return delegate.hasNewTripPatternsForModifiedTrips();
  }

  @Override
  @Nullable
  public Route getRealtimeAddedRoute(FeedScopedId id) {
    return delegate.getRealtimeAddedRoute(id);
  }

  @Override
  public Collection<Route> listRealTimeAddedRoutes() {
    return delegate.listRealTimeAddedRoutes();
  }

  @Override
  @Nullable
  public Trip getRealTimeAddedTrip(FeedScopedId id) {
    return delegate.getRealTimeAddedTrip(id);
  }

  @Override
  public Collection<Trip> listRealTimeAddedTrips() {
    return delegate.listRealTimeAddedTrips();
  }

  @Override
  @Nullable
  public TripPattern getRealTimeAddedPatternForTrip(Trip trip) {
    return delegate.getRealTimeAddedPatternForTrip(trip);
  }

  @Override
  public Collection<TripPattern> getRealTimeAddedPatternForRoute(Route route) {
    return delegate.getRealTimeAddedPatternForRoute(route);
  }

  @Override
  @Nullable
  public TripOnServiceDate getRealTimeAddedTripOnServiceDateById(FeedScopedId id) {
    return delegate.getRealTimeAddedTripOnServiceDateById(id);
  }

  @Override
  @Nullable
  public TripOnServiceDate getRealTimeAddedTripOnServiceDateForTripAndDay(
    TripIdAndServiceDate tripIdAndServiceDate
  ) {
    return delegate.getRealTimeAddedTripOnServiceDateForTripAndDay(tripIdAndServiceDate);
  }

  @Override
  public Collection<? extends TripOnServiceDate> listRealTimeAddedTripOnServiceDate() {
    return delegate.listRealTimeAddedTripOnServiceDate();
  }

  @Override
  public Collection<TripOnServiceDate> getRealTimeReplacedByTripOnServiceDate(FeedScopedId id) {
    return delegate.getRealTimeReplacedByTripOnServiceDate(id);
  }

  @Override
  public Collection<TripPattern> getPatternsForStop(StopLocation stop) {
    return delegate.getPatternsForStop(stop);
  }

  @Override
  public RaptorTransitData getRealtimeRaptorTransitData() {
    return delegate.getRealtimeRaptorTransitData();
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }
}
