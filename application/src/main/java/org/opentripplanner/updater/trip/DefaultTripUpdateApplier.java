package org.opentripplanner.updater.trip;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.core.model.id.FeedScopedId;
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
import org.opentripplanner.updater.trip.gtfs.BackwardsDelayInterpolator;
import org.opentripplanner.updater.trip.gtfs.ForwardsDelayInterpolator;
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

    // Create builder without scheduled times so interpolation can fill them in
    var builder = tripTimes.createRealTimeWithoutScheduledTimes();

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

    // Apply each stop time update - iterate through ALL stops in pattern
    // and match updates by stop ID (similar to GTFS-RT TripTimesUpdater)
    int updateIndex = 0;
    boolean anyUpdatesApplied = false;
    int numStops = builder.numberOfStops();

    for (int i = 0; i < numStops; i++) {
      var stopUpdate = updateIndex < stopUpdates.size() ? stopUpdates.get(updateIndex) : null;

      // Check if this update matches the current stop
      boolean match = false;
      if (stopUpdate != null && stopUpdate.stopReference() != null) {
        var stopId = pattern.getStop(i).getId();
        if (stopUpdate.stopReference().stopId() != null) {
          match = stopUpdate.stopReference().stopId().equals(stopId);
        }
      }

      if (match) {
        // Apply time updates - use delay-based updates when available for interpolation
        if (stopUpdate.arrivalUpdate() != null) {
          if (stopUpdate.arrivalUpdate().hasDelay()) {
            builder.withArrivalDelay(i, stopUpdate.arrivalUpdate().delaySeconds());
          } else {
            int scheduledArrival = builder.getScheduledArrivalTime(i);
            int updatedArrival = stopUpdate.arrivalUpdate().resolveTime(scheduledArrival);
            builder.withArrivalTime(i, updatedArrival);
          }
          anyUpdatesApplied = true;
        }

        if (stopUpdate.departureUpdate() != null) {
          if (stopUpdate.departureUpdate().hasDelay()) {
            builder.withDepartureDelay(i, stopUpdate.departureUpdate().delaySeconds());
          } else {
            int scheduledDeparture = builder.getScheduledDepartureTime(i);
            int updatedDeparture = stopUpdate.departureUpdate().resolveTime(scheduledDeparture);
            builder.withDepartureTime(i, updatedDeparture);
          }
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

        // Move to next update
        updateIndex++;
      }
      // If no match, leave this stop with scheduled times (interpolation will fill it in if needed)
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

    // Apply delay interpolation if configured
    var options = parsedUpdate.options();
    if (options.propagatesDelays()) {
      // Apply forward interpolation to fill in missing times
      ForwardsDelayInterpolator.getInstance(options.forwardsPropagation()).interpolateDelay(
        builder
      );

      // Apply backward interpolation to ensure non-decreasing times
      BackwardsDelayInterpolator.getInstance(options.backwardsPropagation()).propagateBackwards(
        builder
      );
    } else {
      // No interpolation - copy scheduled times for stops without explicit updates
      builder.copyMissingTimesFromScheduledTimetable();
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
    var stopTimeUpdates = parsedUpdate.stopTimeUpdates();
    if (stopTimeUpdates.isEmpty()) {
      return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.NO_UPDATES));
    }

    var serviceDate = parsedUpdate.serviceDate();
    var tripReference = parsedUpdate.tripReference();

    // Resolve the trip
    var trip = transitService.getTrip(tripReference.tripId());
    if (trip == null) {
      return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.TRIP_NOT_FOUND));
    }

    // Get the original pattern
    var originalPattern = transitService.findPattern(trip, serviceDate);
    if (originalPattern == null) {
      return Result.failure(
        UpdateError.noTripId(UpdateError.UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN)
      );
    }

    // Count extra calls
    long numExtraCalls = stopTimeUpdates.stream().filter(u -> u.isExtraCall()).count();
    int numOriginalStops = (int) (stopTimeUpdates.size() - numExtraCalls);

    // Validate that non-extra calls match the original pattern
    if (numOriginalStops != originalPattern.numberOfStops()) {
      return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN));
    }

    // Resolve the existing trip times to use as base for modifications
    var timetable = context.snapshotManager().resolve(originalPattern, serviceDate);
    var existingTripTimes = timetable.getTripTimes(trip);
    if (existingTripTimes == null) {
      return Result.failure(
        UpdateError.noTripId(UpdateError.UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN)
      );
    }

    // Build new stop times list (original + extra)
    List<StopTime> newStopTimes = new ArrayList<>();
    int originalStopIndex = 0;

    for (var stopTimeUpdate : stopTimeUpdates) {
      var stopRef = stopTimeUpdate.stopReference();
      var stop = transitService.getRegularStop(stopRef.stopId());

      if (stop == null) {
        return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN_STOP));
      }

      // Validate that non-extra stops match the original pattern
      if (!stopTimeUpdate.isExtraCall()) {
        var originalStop = originalPattern.getStop(originalStopIndex);
        if (!stop.getId().equals(originalStop.getId())) {
          return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN));
        }
        originalStopIndex++;
      }

      var stopTime = new StopTime();
      stopTime.setStop(stop);
      stopTime.setTrip(trip);
      stopTime.setStopSequence(newStopTimes.size());

      // Resolve times
      int arrivalTime;
      int departureTime;

      if (stopTimeUpdate.hasArrivalUpdate() && stopTimeUpdate.hasDepartureUpdate()) {
        arrivalTime = stopTimeUpdate.arrivalUpdate().resolveTime(0);
        departureTime = stopTimeUpdate.departureUpdate().resolveTime(0);
      } else if (stopTimeUpdate.hasDepartureUpdate()) {
        departureTime = stopTimeUpdate.departureUpdate().resolveTime(0);
        arrivalTime = departureTime;
      } else if (stopTimeUpdate.hasArrivalUpdate()) {
        arrivalTime = stopTimeUpdate.arrivalUpdate().resolveTime(0);
        departureTime = arrivalTime;
      } else {
        return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.NO_UPDATES));
      }

      stopTime.setArrivalTime(arrivalTime);
      stopTime.setDepartureTime(departureTime);
      newStopTimes.add(stopTime);
    }

    // Create new stop pattern
    var newStopPattern = new StopPattern(newStopTimes);

    // Create new trip pattern with the modified stop pattern
    var newPatternId = FeedScopedId.parse(trip.getId() + "-extra-calls-pattern");
    var newPattern = TripPattern.of(newPatternId)
      .withRoute(trip.getRoute())
      .withMode(originalPattern.getMode())
      .withStopPattern(newStopPattern)
      .withScheduledTimeTableBuilder(builder ->
        builder.addTripTimes(TripTimesFactory.tripTimes(trip, newStopTimes, new Deduplicator()))
      )
      .build();

    // Create scheduled trip times for the new pattern
    var scheduledTripTimes = TripTimesFactory.tripTimes(trip, newStopTimes, new Deduplicator());

    // Create real-time trip times based on the scheduled times
    var rtBuilder = scheduledTripTimes.createRealTimeFromScheduledTimes();
    rtBuilder.withRealTimeState(RealTimeState.MODIFIED);

    // Apply real-time updates to the trip times
    for (int i = 0; i < stopTimeUpdates.size(); i++) {
      var stopTimeUpdate = stopTimeUpdates.get(i);

      if (stopTimeUpdate.hasArrivalUpdate()) {
        rtBuilder.withArrivalTime(i, stopTimeUpdate.arrivalUpdate().resolveTime(0));
      }

      if (stopTimeUpdate.hasDepartureUpdate()) {
        rtBuilder.withDepartureTime(i, stopTimeUpdate.departureUpdate().resolveTime(0));
      }
    }

    var realTimeTripTimes = rtBuilder.build();
    var realTimeTripUpdate = new RealTimeTripUpdate(newPattern, realTimeTripTimes, serviceDate);
    return Result.success(realTimeTripUpdate);
  }
}
