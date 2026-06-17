package org.opentripplanner.updater.trip.gtfs;

import static org.opentripplanner.updater.spi.UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND;
import static org.opentripplanner.updater.trip.UpdateIncrementality.FULL_DATASET;

import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.spi.UpdateSuccess;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;
import org.opentripplanner.updater.trip.UpdateIncrementality;
import org.opentripplanner.updater.trip.gtfs.model.TripUpdate;

class CancellationTripHandler {

  private final TransitEditorService transitEditorService;
  private final TimetableSnapshotManager snapshotManager;

  CancellationTripHandler(
    TransitEditorService transitEditorService,
    TimetableSnapshotManager snapshotManager
  ) {
    this.transitEditorService = transitEditorService;
    this.snapshotManager = snapshotManager;
  }

  UpdateSuccess cancel(TripUpdate tripUpdate, UpdateIncrementality incrementality)
    throws UpdateException {
    return handle(tripUpdate, CancelationType.CANCEL, incrementality);
  }

  UpdateSuccess delete(TripUpdate tripUpdate, UpdateIncrementality incrementality)
    throws UpdateException {
    return handle(tripUpdate, CancelationType.DELETE, incrementality);
  }

  private UpdateSuccess handle(
    TripUpdate tripUpdate,
    CancelationType cancelationType,
    UpdateIncrementality incrementality
  ) throws UpdateException {
    // For DIFFERENTIAL updates, try to cancel a previously added trip
    if (incrementality != FULL_DATASET) {
      var addedPattern = snapshotManager.getNewTripPatternForModifiedTrip(
        tripUpdate.tripId(),
        tripUpdate.serviceDate()
      );
      if (addedPattern != null) {
        var timetable = snapshotManager.resolve(addedPattern, tripUpdate.serviceDate());
        if (timetable != null) {
          var tripTimes = timetable.getTripTimes(tripUpdate.tripId());
          if (tripTimes != null && tripTimes.isAdded()) {
            var builder = tripTimes.createRealTimeFromScheduledTimes();
            switch (cancelationType) {
              case CANCEL -> builder.withCanceled();
              case DELETE -> builder.withDeleted();
            }
            return snapshotManager.updateBuffer(
              RealTimeTripUpdate.of(addedPattern, builder.build(), tripUpdate.serviceDate()).build()
            );
          }
        }
      }
    }

    // Cancel the scheduled trip
    var pattern = getPatternForTripId(tripUpdate.tripId());
    if (pattern == null) {
      throw UpdateException.of(tripUpdate.tripId(), NO_TRIP_FOR_CANCELLATION_FOUND);
    }

    var tripTimes = pattern.getScheduledTimetable().getTripTimes(tripUpdate.tripId());
    if (tripTimes == null) {
      throw UpdateException.of(tripUpdate.tripId(), NO_TRIP_FOR_CANCELLATION_FOUND);
    }

    var builder = tripTimes.createRealTimeFromScheduledTimes();
    switch (cancelationType) {
      case CANCEL -> builder.withCanceled();
      case DELETE -> builder.withDeleted();
    }
    return snapshotManager.updateBuffer(
      RealTimeTripUpdate.of(pattern, builder.build(), tripUpdate.serviceDate())
        .withRevertPreviousRealTimeUpdates(true)
        .build()
    );
  }

  private TripPattern getPatternForTripId(FeedScopedId tripId) {
    Trip trip = transitEditorService.getTrip(tripId);
    return transitEditorService.findPattern(trip);
  }

  private enum CancelationType {
    CANCEL,
    DELETE,
  }
}
