package org.opentripplanner.updater.trip;

import java.time.LocalDate;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ResolvedTripRemoval;
import org.opentripplanner.updater.trip.model.TripRemoval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a {@link TripRemoval} into a {@link ResolvedTripRemoval} for cancelling
 * or deleting trips.
 * <p>
 * Used for CANCEL_TRIP ({@link org.opentripplanner.updater.trip.model.TripCancellation})
 * and DELETE_TRIP ({@link org.opentripplanner.updater.trip.model.TripDeletion}).
 * <p>
 * This resolver looks up scheduled trips first, then checks for previously added (real-time)
 * trips via the snapshot manager.
 */
public class TripRemovalResolver {

  private static final Logger LOG = LoggerFactory.getLogger(TripRemovalResolver.class);

  private final TransitEditorService transitService;
  private final TripResolver tripResolver;
  private final ServiceDateResolver serviceDateResolver;

  @Nullable
  private final TimetableSnapshotManager snapshotManager;

  public TripRemovalResolver(
    TransitEditorService transitService,
    TripResolver tripResolver,
    ServiceDateResolver serviceDateResolver,
    @Nullable TimetableSnapshotManager snapshotManager
  ) {
    this.transitService = Objects.requireNonNull(transitService, "transitService must not be null");
    this.tripResolver = Objects.requireNonNull(tripResolver, "tripResolver must not be null");
    this.serviceDateResolver = Objects.requireNonNull(
      serviceDateResolver,
      "serviceDateResolver must not be null"
    );
    this.snapshotManager = snapshotManager;
  }

  /**
   * Resolve a ParsedTripUpdate for trip cancellation or deletion.
   *
   * @param parsedUpdate The parsed update to resolve
   * @return the resolved data
   * @throws UpdateException if trip cannot be found at all
   */
  public ResolvedTripRemoval resolve(TripRemoval parsedUpdate) {
    // Resolve service date
    LocalDate serviceDate = serviceDateResolver.resolveServiceDate(parsedUpdate);

    var tripReference = parsedUpdate.tripReference();
    FeedScopedId tripId = tripReference.tripId();
    String dataSource = parsedUpdate.dataSource();

    // Try to resolve as scheduled trip from static transit model
    Trip trip;
    try {
      trip = tripResolver.resolveTrip(tripReference);
    } catch (UpdateException e) {
      // Trip not found in scheduled data - check for previously added trips
      return resolveAddedTripOrNotFound(serviceDate, tripId, dataSource);
    }

    // Find pattern for the trip
    TripPattern pattern = transitService.findPattern(trip);
    if (pattern == null) {
      return resolveAddedTripOrNotFound(serviceDate, trip.getId(), dataSource);
    }

    // If the resolved pattern is itself a real-time added pattern (i.e., this trip was added via
    // real-time, not in the static schedule), look up the RT timetable times and treat the trip
    // as a previously-added trip so that TripRemover preserves the "added" flag.
    if (pattern.isRealTimeTripPattern() && !pattern.isStopPatternModifiedInRealTime()) {
      if (snapshotManager != null) {
        var rtTimetable = snapshotManager.resolve(pattern, serviceDate);
        var rtTripTimes = rtTimetable.getTripTimes(trip.getId());
        if (rtTripTimes != null && rtTripTimes.isAdded()) {
          return ResolvedTripRemoval.forPreviouslyAddedTrip(
            serviceDate,
            trip.getId(),
            pattern,
            rtTripTimes,
            dataSource
          );
        }
      }
    }

    // Get trip times
    TripTimes tripTimes = pattern.getScheduledTimetable().getTripTimes(trip);
    if (tripTimes == null) {
      return resolveAddedTripOrNotFound(serviceDate, trip.getId(), dataSource);
    }

    // Cancellation of a scheduled trip always reverts any previous real-time modifications
    // (quay changes, time updates) and marks the trip as cancelled on the scheduled pattern.
    // TripRemover sets revertPreviousRealTimeUpdates=true so that any existing
    // RT-modified pattern entry for this trip is cleared from the snapshot.
    //
    // Note: extra call cancellations (SIRI Cancellation=true with extra call stops) are NOT
    // routed here — they go through TripModifier instead (see SiriTripUpdateParser).
    return ResolvedTripRemoval.forScheduledTrip(serviceDate, trip, pattern, tripTimes, dataSource);
  }

  /**
   * Check for a previously added (real-time) trip in the snapshot manager.
   * Returns the resolved data if found, or throws UpdateException otherwise.
   */
  private ResolvedTripRemoval resolveAddedTripOrNotFound(
    LocalDate serviceDate,
    FeedScopedId tripId,
    @Nullable String dataSource
  ) {
    if (snapshotManager != null && tripId != null) {
      var pattern = snapshotManager.getNewTripPatternForModifiedTrip(tripId, serviceDate);
      if (pattern != null) {
        var timetable = snapshotManager.resolve(pattern, serviceDate);
        var tripTimes = timetable.getTripTimes(tripId);
        if (tripTimes != null && tripTimes.isAdded()) {
          return ResolvedTripRemoval.forPreviouslyAddedTrip(
            serviceDate,
            tripId,
            pattern,
            tripTimes,
            dataSource
          );
        }
      }
    }
    throw UpdateException.of(tripId, UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND);
  }
}
