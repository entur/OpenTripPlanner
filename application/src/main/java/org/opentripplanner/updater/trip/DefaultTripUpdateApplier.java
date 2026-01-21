package org.opentripplanner.updater.trip;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.model.StopTime;
import org.opentripplanner.transit.model.framework.Deduplicator;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeState;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimesFactory;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of TripUpdateApplier that applies parsed trip updates to the transit
 * model. This is the unified component shared by both SIRI-ET and GTFS-RT updaters.
 * <p>
 * This class consolidates the logic previously duplicated between:
 * - SIRI: ModifiedTripBuilder, AddedTripBuilder, ExtraCallTripBuilder
 * - GTFS-RT: GtfsRealTimeTripUpdateAdapter, TripTimesUpdater
 */
public class DefaultTripUpdateApplier implements TripUpdateApplier {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultTripUpdateApplier.class);

  private final TransitEditorService transitService;

  public DefaultTripUpdateApplier(TransitEditorService transitService) {
    this.transitService = Objects.requireNonNull(transitService);
  }

  @Override
  public Result<RealTimeTripUpdate, UpdateError> apply(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    try {
      return switch (parsedUpdate.updateType()) {
        case UPDATE_EXISTING -> handleUpdateExisting(parsedUpdate, context);
        case CANCEL_TRIP -> handleCancelTrip(parsedUpdate, context);
        case DELETE_TRIP -> handleDeleteTrip(parsedUpdate, context);
        case ADD_NEW_TRIP -> handleAddNewTrip(parsedUpdate, context);
        case MODIFY_TRIP -> handleModifyTrip(parsedUpdate, context);
        case ADD_EXTRA_CALLS -> handleAddExtraCalls(parsedUpdate, context);
      };
    } catch (Exception e) {
      LOG.error("Error applying trip update: {}", e.getMessage(), e);
      return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN));
    }
  }

  private Result<RealTimeTripUpdate, UpdateError> handleUpdateExisting(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    var tripRef = parsedUpdate.tripReference();
    var serviceDate = parsedUpdate.serviceDate();

    if (tripRef.tripId() == null) {
      return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.NO_TRIP_ID));
    }

    // Resolve trip from ID
    var trip = transitService.getTrip(tripRef.tripId());
    if (trip == null) {
      LOG.debug("Trip {} not found for update", tripRef.tripId());
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.TRIP_NOT_FOUND,
          null,
          context.feedId()
        )
      );
    }

    // Find the trip pattern
    var pattern = transitService.findPattern(trip, serviceDate);
    if (pattern == null) {
      LOG.debug("Pattern not found for trip {} on date {}", tripRef.tripId(), serviceDate);
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN,
          null,
          context.feedId()
        )
      );
    }

    // Get the snapshot manager and resolve current timetable
    var snapshotManager = context.snapshotManager();
    if (snapshotManager == null) {
      LOG.error("No snapshot manager available for update");
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.UNKNOWN,
          null,
          context.feedId()
        )
      );
    }

    var timetable = snapshotManager.resolve(pattern, serviceDate);
    var tripTimes = timetable.getTripTimes(tripRef.tripId());
    if (tripTimes == null) {
      LOG.debug("Trip times not found for trip {} on {}", tripRef.tripId(), serviceDate);
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.TRIP_NOT_FOUND,
          null,
          context.feedId()
        )
      );
    }

    // Create builder from scheduled times
    var builder = tripTimes.createRealTimeFromScheduledTimes();

    // Apply stop time updates
    var stopUpdates = parsedUpdate.stopTimeUpdates();
    if (stopUpdates.isEmpty()) {
      LOG.debug("No stop time updates for trip {}", tripRef.tripId());
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.NO_UPDATES,
          null,
          context.feedId()
        )
      );
    }

    // Apply each stop time update
    boolean anyUpdatesApplied = false;
    for (int i = 0; i < stopUpdates.size() && i < builder.numberOfStops(); i++) {
      var stopUpdate = stopUpdates.get(i);

      // Apply time updates
      if (stopUpdate.arrivalUpdate() != null) {
        int scheduledArrival = builder.getScheduledArrivalTime(i);
        int updatedArrival = stopUpdate.arrivalUpdate().resolveTime(scheduledArrival);
        builder.withArrivalTime(i, updatedArrival);
        anyUpdatesApplied = true;
      }

      if (stopUpdate.departureUpdate() != null) {
        int scheduledDeparture = builder.getScheduledDepartureTime(i);
        int updatedDeparture = stopUpdate.departureUpdate().resolveTime(scheduledDeparture);
        builder.withDepartureTime(i, updatedDeparture);
        anyUpdatesApplied = true;
      }

      // Apply stop status
      switch (stopUpdate.status()) {
        case CANCELLED, SKIPPED -> {
          builder.withCanceled(i);
          anyUpdatesApplied = true;
        }
        case NO_DATA -> {
          builder.withNoData(i);
          anyUpdatesApplied = true;
        }
        case SCHEDULED, ADDED -> {
          // Normal update, already handled by time updates
        }
      }

      // Apply additional properties
      if (stopUpdate.stopHeadsign() != null) {
        builder.withStopHeadsign(i, stopUpdate.stopHeadsign());
      }
      if (stopUpdate.occupancy() != null) {
        builder.withOccupancyStatus(i, stopUpdate.occupancy());
      }
      if (stopUpdate.predictionInaccurate()) {
        builder.withInaccuratePredictions(i);
      }
      if (stopUpdate.recorded()) {
        builder.withRecorded(i);
      }
    }

    if (!anyUpdatesApplied) {
      LOG.debug("No actual updates applied for trip {}", tripRef.tripId());
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.NO_UPDATES,
          null,
          context.feedId()
        )
      );
    }

    // Mark as modified or updated based on whether trip has real-time state
    builder.withRealTimeState(org.opentripplanner.transit.model.timetable.RealTimeState.MODIFIED);

    var realTimeTripUpdate = new RealTimeTripUpdate(pattern, builder.build(), serviceDate);
    LOG.debug("Updated trip {} on {}", tripRef.tripId(), serviceDate);
    return Result.success(realTimeTripUpdate);
  }

  private Result<RealTimeTripUpdate, UpdateError> handleCancelTrip(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    return cancelOrDeleteTrip(parsedUpdate, context, true);
  }

  private Result<RealTimeTripUpdate, UpdateError> handleDeleteTrip(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    return cancelOrDeleteTrip(parsedUpdate, context, false);
  }

  /**
   * Common logic for canceling or deleting a trip.
   *
   * @param parsedUpdate the parsed update
   * @param context the context
   * @param isCancel true for cancel, false for delete
   * @return Result with RealTimeTripUpdate or UpdateError
   */
  private Result<RealTimeTripUpdate, UpdateError> cancelOrDeleteTrip(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context,
    boolean isCancel
  ) {
    var tripRef = parsedUpdate.tripReference();
    var serviceDate = parsedUpdate.serviceDate();

    // Resolve trip from ID
    var trip = transitService.getTrip(tripRef.tripId());
    if (trip == null) {
      LOG.debug("Trip {} not found for cancellation/deletion", tripRef.tripId());
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.TRIP_NOT_FOUND,
          null,
          context.feedId()
        )
      );
    }

    // Find the trip pattern
    var pattern = transitService.findPattern(trip, serviceDate);
    if (pattern == null) {
      LOG.debug("Pattern not found for trip {} on date {}", tripRef.tripId(), serviceDate);
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN,
          null,
          context.feedId()
        )
      );
    }

    // Get the snapshot manager and resolve current timetable
    var snapshotManager = context.snapshotManager();
    if (snapshotManager == null) {
      LOG.error("No snapshot manager available for cancellation/deletion");
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.UNKNOWN,
          null,
          context.feedId()
        )
      );
    }

    var timetable = snapshotManager.resolve(pattern, serviceDate);
    var tripTimes = timetable.getTripTimes(tripRef.tripId());
    if (tripTimes == null) {
      LOG.debug("Trip times not found for trip {} on {}", tripRef.tripId(), serviceDate);
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND,
          null,
          context.feedId()
        )
      );
    }

    // Create real-time trip times and mark as canceled/deleted
    var builder = tripTimes.createRealTimeFromScheduledTimes();
    if (isCancel) {
      builder.cancelTrip();
      LOG.debug("Canceling trip {} on {}", tripRef.tripId(), serviceDate);
    } else {
      builder.deleteTrip();
      LOG.debug("Deleting trip {} on {}", tripRef.tripId(), serviceDate);
    }

    var realTimeTripUpdate = new RealTimeTripUpdate(pattern, builder.build(), serviceDate);
    return Result.success(realTimeTripUpdate);
  }

  private Result<RealTimeTripUpdate, UpdateError> handleAddNewTrip(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    var tripRef = parsedUpdate.tripReference();
    var serviceDate = parsedUpdate.serviceDate();
    var stopTimeUpdates = parsedUpdate.stopTimeUpdates();

    // Validate we have stop time updates
    if (stopTimeUpdates.isEmpty()) {
      LOG.debug("No stop time updates for new trip {}", tripRef.tripId());
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.NO_UPDATES,
          null,
          context.feedId()
        )
      );
    }

    // Get the route
    var route = transitService.getRoute(tripRef.routeId());
    if (route == null) {
      LOG.debug("Route {} not found for new trip {}", tripRef.routeId(), tripRef.tripId());
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.UNKNOWN,
          null,
          context.feedId()
        )
      );
    }

    // Get or create service ID for the service date
    var serviceId = transitService.getOrCreateServiceIdForDate(serviceDate);

    // Create the trip
    var trip = Trip.of(tripRef.tripId()).withRoute(route).withServiceId(serviceId).build();

    // Create stop times from updates
    List<StopTime> stopTimes = new ArrayList<>();
    for (int i = 0; i < stopTimeUpdates.size(); i++) {
      var stopUpdate = stopTimeUpdates.get(i);
      var stop = transitService.getRegularStop(stopUpdate.stopReference().stopId());
      if (stop == null) {
        LOG.debug(
          "Stop {} not found for new trip {}",
          stopUpdate.stopReference(),
          tripRef.tripId()
        );
        return Result.failure(
          new UpdateError(
            tripRef.tripId(),
            UpdateError.UpdateErrorType.UNKNOWN,
            null,
            context.feedId()
          )
        );
      }

      var stopTime = new StopTime();
      stopTime.setStop(stop);
      stopTime.setStopSequence(i);

      // Resolve arrival and departure times
      int arrivalTime = stopUpdate.hasArrivalUpdate()
        ? stopUpdate.arrivalUpdate().resolveTime(0)
        : -1;
      int departureTime = stopUpdate.hasDepartureUpdate()
        ? stopUpdate.departureUpdate().resolveTime(0)
        : -1;

      stopTime.setArrivalTime(arrivalTime);
      stopTime.setDepartureTime(departureTime);

      stopTimes.add(stopTime);
    }

    // Create stop pattern
    var stopPattern = new StopPattern(stopTimes);

    // Create scheduled trip times
    var scheduledTripTimes = TripTimesFactory.tripTimes(trip, stopTimes, new Deduplicator());

    // Create trip pattern
    var patternId = tripRef.tripId().getId() + "-pattern";
    var pattern = TripPattern.of(
      new org.opentripplanner.core.model.id.FeedScopedId(context.feedId(), patternId)
    )
      .withRoute(route)
      .withMode(route.getMode())
      .withStopPattern(stopPattern)
      .withScheduledTimeTableBuilder(builder -> builder.addTripTimes(scheduledTripTimes))
      .build();

    // Create real-time trip times from scheduled
    var rtBuilder = scheduledTripTimes.createRealTimeFromScheduledTimes();
    rtBuilder.withRealTimeState(RealTimeState.ADDED);
    var realTimeTripTimes = rtBuilder.build();

    var realTimeTripUpdate = new RealTimeTripUpdate(pattern, realTimeTripTimes, serviceDate);
    LOG.debug("Added new trip {} on {}", tripRef.tripId(), serviceDate);
    return Result.success(realTimeTripUpdate);
  }

  private Result<RealTimeTripUpdate, UpdateError> handleModifyTrip(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    var tripRef = parsedUpdate.tripReference();
    var serviceDate = parsedUpdate.serviceDate();
    var stopTimeUpdates = parsedUpdate.stopTimeUpdates();

    // Validate we have stop time updates
    if (stopTimeUpdates.isEmpty()) {
      LOG.debug("No stop time updates for modified trip {}", tripRef.tripId());
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.NO_UPDATES,
          null,
          context.feedId()
        )
      );
    }

    // Resolve trip from ID
    var trip = transitService.getTrip(tripRef.tripId());
    if (trip == null) {
      LOG.debug("Trip {} not found for modification", tripRef.tripId());
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.TRIP_NOT_FOUND,
          null,
          context.feedId()
        )
      );
    }

    // Find the original trip pattern
    var originalPattern = transitService.findPattern(trip, serviceDate);
    if (originalPattern == null) {
      LOG.debug("Pattern not found for trip {} on {}", tripRef.tripId(), serviceDate);
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN,
          null,
          context.feedId()
        )
      );
    }

    // Resolve the current timetable to get existing trip times
    var snapshotManager = context.snapshotManager();
    var timetable = snapshotManager.resolve(originalPattern, serviceDate);
    if (timetable == null) {
      LOG.debug("No timetable found for pattern {} on {}", originalPattern.getId(), serviceDate);
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND,
          null,
          context.feedId()
        )
      );
    }

    // Get the trip times for this specific trip
    var tripTimes = timetable.getTripTimes(trip);
    if (tripTimes == null) {
      LOG.debug("No trip times found for trip {} on {}", tripRef.tripId(), serviceDate);
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND,
          null,
          context.feedId()
        )
      );
    }

    // Create stop times from updates
    List<StopTime> newStopTimes = new ArrayList<>();
    for (int i = 0; i < stopTimeUpdates.size(); i++) {
      var stopUpdate = stopTimeUpdates.get(i);
      var stop = transitService.getRegularStop(stopUpdate.stopReference().stopId());
      if (stop == null) {
        LOG.debug(
          "Stop {} not found for modified trip {}",
          stopUpdate.stopReference(),
          tripRef.tripId()
        );
        return Result.failure(
          new UpdateError(
            tripRef.tripId(),
            UpdateError.UpdateErrorType.UNKNOWN_STOP,
            null,
            context.feedId()
          )
        );
      }

      var stopTime = new StopTime();
      stopTime.setStop(stop);
      stopTime.setStopSequence(i);

      // Get scheduled time for this stop if it exists in original pattern
      int scheduledArrival = i < tripTimes.getNumStops() ? tripTimes.getScheduledArrivalTime(i) : 0;
      int scheduledDeparture = i < tripTimes.getNumStops()
        ? tripTimes.getScheduledDepartureTime(i)
        : 0;

      // Resolve arrival and departure times
      int arrivalTime = stopUpdate.hasArrivalUpdate()
        ? stopUpdate.arrivalUpdate().resolveTime(scheduledArrival)
        : -1;
      int departureTime = stopUpdate.hasDepartureUpdate()
        ? stopUpdate.departureUpdate().resolveTime(scheduledDeparture)
        : -1;

      stopTime.setArrivalTime(arrivalTime);
      stopTime.setDepartureTime(departureTime);

      newStopTimes.add(stopTime);
    }

    // Create new stop pattern
    var newStopPattern = new StopPattern(newStopTimes);

    // Check if stop pattern actually changed
    boolean stopPatternChanged = !originalPattern.getStopPattern().equals(newStopPattern);

    // Create new scheduled trip times
    var scheduledTripTimes = TripTimesFactory.tripTimes(trip, newStopTimes, new Deduplicator());

    // Create trip pattern (reuse original or create new)
    TripPattern pattern;
    if (stopPatternChanged) {
      // Create new pattern with modified stops
      var patternId = tripRef.tripId().getId() + "-modified-pattern";
      pattern = TripPattern.of(
        new org.opentripplanner.core.model.id.FeedScopedId(context.feedId(), patternId)
      )
        .withRoute(trip.getRoute())
        .withMode(trip.getMode())
        .withStopPattern(newStopPattern)
        .withScheduledTimeTableBuilder(builder -> builder.addTripTimes(scheduledTripTimes))
        .build();
    } else {
      // Reuse original pattern if stops didn't change
      pattern = originalPattern;
    }

    // Create real-time trip times from scheduled
    var rtBuilder = scheduledTripTimes.createRealTimeFromScheduledTimes();

    // Set real-time state based on whether pattern changed
    if (stopPatternChanged) {
      rtBuilder.withRealTimeState(RealTimeState.MODIFIED);
      LOG.debug("Modified trip {} with stop pattern change on {}", tripRef.tripId(), serviceDate);
    } else {
      rtBuilder.withRealTimeState(RealTimeState.UPDATED);
      LOG.debug("Modified trip {} with only time changes on {}", tripRef.tripId(), serviceDate);
    }

    var realTimeTripTimes = rtBuilder.build();
    var realTimeTripUpdate = new RealTimeTripUpdate(pattern, realTimeTripTimes, serviceDate);
    return Result.success(realTimeTripUpdate);
  }

  private Result<RealTimeTripUpdate, UpdateError> handleAddExtraCalls(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    // TODO: Implement
    return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN));
  }
}
