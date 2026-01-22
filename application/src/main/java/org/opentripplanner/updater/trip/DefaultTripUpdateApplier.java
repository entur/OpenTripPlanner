package org.opentripplanner.updater.trip;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.model.StopTime;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.model.framework.Deduplicator;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.organization.Agency;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.timetable.RealTimeState;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;
import org.opentripplanner.transit.model.timetable.TripTimesFactory;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.DataValidationExceptionMapper;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.gtfs.BackwardsDelayInterpolator;
import org.opentripplanner.updater.trip.gtfs.ForwardsDelayInterpolator;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.StopReference;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.siri.SiriTripPatternCache;
import org.opentripplanner.updater.trip.siri.SiriTripPatternIdGenerator;
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
  private final SiriTripPatternCache tripPatternCache;
  private final TripMatcher tripMatcher;

  public DefaultTripUpdateApplier(TransitEditorService transitService) {
    this(transitService, null);
  }

  public DefaultTripUpdateApplier(
    TransitEditorService transitService,
    @Nullable TripMatcher tripMatcher
  ) {
    this.transitService = Objects.requireNonNull(transitService);
    this.tripPatternCache = new SiriTripPatternCache(
      new SiriTripPatternIdGenerator(),
      transitService::findPattern
    );
    this.tripMatcher = tripMatcher;
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
    } catch (DataValidationException e) {
      return DataValidationExceptionMapper.toResult(e, parsedUpdate.dataSource());
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

    // Try fuzzy matching if tripId is null
    if (tripRef.tripId() == null) {
      if (tripMatcher != null) {
        return handleFuzzyMatch(parsedUpdate, context);
      }
      return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.TRIP_NOT_FOUND));
    }

    // Resolve trip based on reference type
    var trip = resolveTrip(tripRef, serviceDate);

    if (trip == null) {
      // Trip not found - try fuzzy matching as fallback if available
      if (tripMatcher != null) {
        return handleFuzzyMatch(parsedUpdate, context);
      }
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
    var tripTimes = timetable.getTripTimes(trip);
    if (tripTimes == null) {
      LOG.debug("Trip times not found for trip {} on {}", trip.getId(), serviceDate);
      return Result.failure(
        new UpdateError(
          trip.getId(),
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

    // Validate stop count before processing
    int numStopsInUpdate = stopUpdates.size();
    int numStopsInPattern = pattern.numberOfStops();
    if (numStopsInUpdate < numStopsInPattern) {
      LOG.debug(
        "Too few stops in update for trip {} - {} stops in update, {} in pattern",
        tripRef.tripId(),
        numStopsInUpdate,
        numStopsInPattern
      );
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.TOO_FEW_STOPS,
          null,
          context.feedId()
        )
      );
    }
    if (numStopsInUpdate > numStopsInPattern) {
      LOG.debug(
        "Too many stops in update for trip {} - {} stops in update, {} in pattern",
        tripRef.tripId(),
        numStopsInUpdate,
        numStopsInPattern
      );
      return Result.failure(
        new UpdateError(
          tripRef.tripId(),
          UpdateError.UpdateErrorType.TOO_MANY_STOPS,
          null,
          context.feedId()
        )
      );
    }

    // Check for unknown stops (must be done before pattern change detection)
    for (int i = 0; i < numStopsInUpdate; i++) {
      var stopUpdate = stopUpdates.get(i);
      if (stopUpdate.stopReference() == null) {
        continue;
      }
      var resolvedStop = resolveStop(stopUpdate.stopReference(), context.feedId());
      if (resolvedStop == null) {
        LOG.debug(
          "Unknown stop {} in update for trip {}",
          stopUpdate.stopReference(),
          tripRef.tripId()
        );
        return Result.failure(
          new UpdateError(
            tripRef.tripId(),
            UpdateError.UpdateErrorType.UNKNOWN_STOP,
            i,
            context.feedId()
          )
        );
      }
    }

    // Check if the update involves a stop pattern change (quay change)
    // If so, delegate to handleModifyTrip
    boolean stopPatternChange = detectStopPatternChange(stopUpdates, pattern, context.feedId());
    if (stopPatternChange) {
      LOG.debug(
        "Detected stop pattern change for trip {}, delegating to modify handler",
        tripRef.tripId()
      );
      return handleModifyTrip(parsedUpdate, context);
    }

    // Apply each stop time update - iterate through ALL stops in pattern
    // and match updates by stop ID (similar to GTFS-RT TripTimesUpdater)
    int updateIndex = 0;
    boolean anyUpdatesApplied = false;
    boolean anyCancelledStops = false;
    int numStops = builder.numberOfStops();

    for (int i = 0; i < numStops; i++) {
      var stopUpdate = updateIndex < stopUpdates.size() ? stopUpdates.get(updateIndex) : null;

      // Check if this update matches the current stop
      boolean match = false;
      if (stopUpdate != null && stopUpdate.stopReference() != null) {
        var patternStop = pattern.getStop(i);
        var resolvedStop = resolveStop(stopUpdate.stopReference(), context.feedId());
        if (resolvedStop != null) {
          match = resolvedStop.getId().equals(patternStop.getId());
        }
      }

      if (match) {
        // Apply time updates - ensure both arrival and departure are set to prevent negative dwell
        int scheduledArrival = builder.getScheduledArrivalTime(i);
        int scheduledDeparture = builder.getScheduledDepartureTime(i);

        Integer updatedArrival = null;
        Integer updatedDeparture = null;

        // Calculate updated arrival
        if (stopUpdate.arrivalUpdate() != null) {
          if (stopUpdate.arrivalUpdate().hasDelay()) {
            updatedArrival = scheduledArrival + stopUpdate.arrivalUpdate().delaySeconds();
          } else {
            updatedArrival = stopUpdate.arrivalUpdate().resolveTime(scheduledArrival);
          }
        }

        // Calculate updated departure
        if (stopUpdate.departureUpdate() != null) {
          if (stopUpdate.departureUpdate().hasDelay()) {
            updatedDeparture = scheduledDeparture + stopUpdate.departureUpdate().delaySeconds();
          } else {
            updatedDeparture = stopUpdate.departureUpdate().resolveTime(scheduledDeparture);
          }
        }

        // Apply times, ensuring no negative dwell time
        if (updatedArrival != null && updatedDeparture != null) {
          // Both provided - use as-is
          builder.withArrivalTime(i, updatedArrival);
          builder.withDepartureTime(i, updatedDeparture);
          anyUpdatesApplied = true;
        } else if (updatedArrival != null) {
          // Only arrival - set departure to max of arrival and scheduled departure
          builder.withArrivalTime(i, updatedArrival);
          builder.withDepartureTime(i, Math.max(updatedArrival, scheduledDeparture));
          anyUpdatesApplied = true;
        } else if (updatedDeparture != null) {
          // Only departure - set arrival to departure (assume zero dwell time)
          builder.withArrivalTime(i, updatedDeparture);
          builder.withDepartureTime(i, updatedDeparture);
          anyUpdatesApplied = true;
        }

        // Apply stop status
        switch (stopUpdate.status()) {
          case CANCELLED, SKIPPED -> {
            builder.withCanceled(i);
            anyUpdatesApplied = true;
            anyCancelledStops = true;
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

    // Determine the appropriate real-time state
    var currentState = tripTimes.getRealTimeState();
    if (currentState == RealTimeState.ADDED) {
      // Keep the ADDED state for dynamically added trips
      builder.withRealTimeState(RealTimeState.ADDED);
    } else if (anyCancelledStops) {
      // If any stop was cancelled, use MODIFIED state
      builder.withRealTimeState(RealTimeState.MODIFIED);
    } else {
      // Regular trip update - mark as UPDATED
      builder.withRealTimeState(RealTimeState.UPDATED);
    }

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
   * When canceling, the trip reverts to its scheduled pattern and times (not real-time modified).
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

    // Resolve trip based on reference type
    var trip = resolveTrip(tripRef, serviceDate);
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

    // Get the snapshot manager
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

    // Find the pattern for this trip
    // For scheduled trips, this returns the static pattern
    // For added trips, this returns the RT-added pattern (which has isCreatedByRealtimeUpdater = true)
    var pattern = transitService.findPattern(trip);
    org.opentripplanner.transit.model.timetable.TripTimes tripTimes;
    boolean isAddedTrip;

    if (pattern != null && !pattern.isCreatedByRealtimeUpdater()) {
      // Scheduled trip - revert to scheduled pattern and times
      isAddedTrip = false;
      snapshotManager.revertTripToScheduledTripPattern(trip.getId(), serviceDate);
      var timetable = pattern.getScheduledTimetable();
      tripTimes = timetable.getTripTimes(trip);
    } else {
      // Added trip - first revert any pattern modifications (like quay changes)
      // This ensures we get the ORIGINAL added pattern, not a modified one
      isAddedTrip = true;
      snapshotManager.revertTripToScheduledTripPattern(trip.getId(), serviceDate);

      // Now find the original real-time pattern (no scheduled pattern exists for added trips)
      pattern = transitService.findPattern(trip, serviceDate);
      if (pattern == null) {
        // Fallback: try findPattern(trip) which checks timetableSnapshot
        pattern = transitService.findPattern(trip);
      }
      if (pattern == null) {
        LOG.debug("Pattern not found for added trip {}", tripRef.tripId());
        return Result.failure(
          new UpdateError(
            tripRef.tripId(),
            UpdateError.UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN,
            null,
            context.feedId()
          )
        );
      }
      // For added trips, use the scheduled timetable (original times when trip was added)
      // not the real-time timetable (which may have been modified)
      var timetable = pattern.getScheduledTimetable();
      tripTimes = timetable.getTripTimes(trip);
    }

    if (tripTimes == null) {
      LOG.debug("Trip times not found for trip {} on {}", trip.getId(), serviceDate);
      return Result.failure(
        new UpdateError(
          trip.getId(),
          UpdateError.UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND,
          null,
          context.feedId()
        )
      );
    }

    // Create real-time trip times and mark as canceled/deleted
    var builder = tripTimes.createRealTimeFromScheduledTimes();

    // For added trips, apply first/last stop time adjustment to avoid negative dwell times
    // First stop: arrival = departure; Last stop: departure = arrival
    if (isAddedTrip) {
      int numStops = pattern.numberOfStops();
      if (numStops > 0) {
        // First stop: set arrival = departure
        int firstStopDeparture = tripTimes.getScheduledDepartureTime(0);
        builder.withArrivalTime(0, firstStopDeparture);
        // Last stop: set departure = arrival
        int lastStopIndex = numStops - 1;
        int lastStopArrival = tripTimes.getScheduledArrivalTime(lastStopIndex);
        builder.withDepartureTime(lastStopIndex, lastStopArrival);
      }
    }

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
    var tripCreationInfo = parsedUpdate.tripCreationInfo();

    // Validate we have trip creation info (required for ADD_NEW_TRIP in SIRI)
    if (tripCreationInfo == null) {
      LOG.debug(
        "No trip creation info for new trip {} - EstimatedVehicleJourneyCode required",
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

    // Check if this is an update to an already-added trip
    boolean isUpdateToExistingAddedTrip = false;
    if (context.snapshotManager() != null) {
      var existingPattern = context
        .snapshotManager()
        .getNewTripPatternForModifiedTrip(tripRef.tripId(), serviceDate);
      isUpdateToExistingAddedTrip = existingPattern != null;
    }

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

    // Get or create the route
    boolean routeCreation = false;
    var route = transitService.getRoute(tripRef.routeId());
    if (route == null) {
      // Route doesn't exist - try to create it

      // Resolve operator/agency
      Agency agency = null;
      if (tripCreationInfo != null && tripCreationInfo.operatorId() != null) {
        agency = transitService.getAgency(tripCreationInfo.operatorId());
      }
      if (agency == null) {
        // Use first available agency as fallback
        var agencies = transitService.listAgencies();
        if (!agencies.isEmpty()) {
          agency = agencies.iterator().next();
        }
      }
      if (agency == null) {
        LOG.debug("Cannot resolve agency for new route {}", tripRef.routeId());
        return Result.failure(
          new UpdateError(
            tripRef.tripId(),
            UpdateError.UpdateErrorType.UNKNOWN,
            null,
            context.feedId()
          )
        );
      }

      // Get mode from trip creation info or default to BUS
      TransitMode mode = TransitMode.BUS;
      if (tripCreationInfo != null && tripCreationInfo.mode() != null) {
        mode = tripCreationInfo.mode();
      }

      // Use routeCreationInfo if available, otherwise use tripRef.routeId()
      FeedScopedId routeId = tripRef.routeId();
      I18NString longName = null;
      if (tripCreationInfo != null && tripCreationInfo.requiresRouteCreation()) {
        var routeInfo = tripCreationInfo.routeCreationInfo();
        if (routeInfo.mode() != null) {
          mode = routeInfo.mode();
        }
        if (routeInfo.routeName() != null) {
          longName = new NonLocalizedString(routeInfo.routeName());
        }
      }

      // Use route ID as fallback long name if not provided
      if (longName == null) {
        longName = new NonLocalizedString(routeId.getId());
      }

      route = Route.of(routeId).withAgency(agency).withMode(mode).withLongName(longName).build();
      routeCreation = true;
      LOG.info("Creating new route {} for added trip {}", route.getId(), tripRef.tripId());
    }

    // Get or create service ID for the service date
    var serviceId = transitService.getOrCreateServiceIdForDate(serviceDate);

    // Create the trip
    var trip = Trip.of(tripRef.tripId()).withRoute(route).withServiceId(serviceId).build();

    // Create stop times from updates
    List<StopTime> stopTimes = new ArrayList<>();
    int numStops = stopTimeUpdates.size();
    for (int i = 0; i < numStops; i++) {
      var stopUpdate = stopTimeUpdates.get(i);
      var stop = resolveStop(stopUpdate.stopReference(), context.feedId());
      if (stop == null) {
        LOG.debug(
          "Stop {} not found for new trip {}",
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

      // Get scheduled (aimed) times for the static schedule
      // Use scheduled times if available, otherwise fall back to realtime times
      Integer scheduledArrival = stopUpdate.hasArrivalUpdate()
        ? stopUpdate.arrivalUpdate().scheduledTimeSecondsSinceMidnight()
        : null;
      Integer scheduledDeparture = stopUpdate.hasDepartureUpdate()
        ? stopUpdate.departureUpdate().scheduledTimeSecondsSinceMidnight()
        : null;

      // If no scheduled time, fall back to realtime/absolute time
      int rawArrivalTime = scheduledArrival != null
        ? scheduledArrival
        : (stopUpdate.hasArrivalUpdate() ? stopUpdate.arrivalUpdate().resolveTime(0) : -1);
      int rawDepartureTime = scheduledDeparture != null
        ? scheduledDeparture
        : (stopUpdate.hasDepartureUpdate() ? stopUpdate.departureUpdate().resolveTime(0) : -1);

      // Apply time fallback: if one is missing, use the other
      int arrivalTime = rawArrivalTime >= 0 ? rawArrivalTime : rawDepartureTime;
      int departureTime = rawDepartureTime >= 0 ? rawDepartureTime : rawArrivalTime;

      // First stop: if only departure was provided, use it for arrival too
      boolean isFirstStop = (i == 0);
      // Last stop: if only arrival was provided, use it for departure too
      boolean isLastStop = (i == numStops - 1);

      // Only adjust if the time was not explicitly provided
      if (isFirstStop && rawArrivalTime < 0 && departureTime >= 0) {
        arrivalTime = departureTime;
      }
      if (isLastStop && rawDepartureTime < 0 && arrivalTime >= 0) {
        departureTime = arrivalTime;
      }

      stopTime.setArrivalTime(arrivalTime);
      stopTime.setDepartureTime(departureTime);

      stopTimes.add(stopTime);
    }

    // Create stop pattern
    var stopPattern = new StopPattern(stopTimes);

    // Create scheduled trip times with service code for routing
    var serviceCode = transitService.getServiceCode(trip.getServiceId());
    var scheduledTripTimes = TripTimesFactory.tripTimes(
      trip,
      stopTimes,
      new Deduplicator()
    ).withServiceCode(serviceCode);

    // Validate that scheduled times don't have negative hops or dwell times
    scheduledTripTimes.validateNonIncreasingTimes();

    // Create pattern with scheduled timetable containing the trip times
    var patternIdGenerator = new SiriTripPatternIdGenerator();
    var patternId = patternIdGenerator.generateUniqueTripPatternId(trip);
    var pattern = TripPattern.of(patternId)
      .withRoute(trip.getRoute())
      .withMode(trip.getMode())
      .withNetexSubmode(trip.getNetexSubMode())
      .withStopPattern(stopPattern)
      .withScheduledTimeTableBuilder(builder -> builder.addTripTimes(scheduledTripTimes))
      .withCreatedByRealtimeUpdater(true)
      .build();

    // Create real-time trip times from scheduled
    var rtBuilder = scheduledTripTimes.createRealTimeFromScheduledTimes();
    // Use UPDATED state if this is re-applying to an already-added trip, otherwise ADDED
    rtBuilder.withRealTimeState(
      isUpdateToExistingAddedTrip ? RealTimeState.UPDATED : RealTimeState.ADDED
    );

    // Apply realtime updates (use absolute/actual times) and recorded flag
    int numStopsForRt = stopTimeUpdates.size();
    for (int i = 0; i < numStopsForRt; i++) {
      var stopUpdate = stopTimeUpdates.get(i);
      boolean isFirstStop = (i == 0);
      boolean isLastStop = (i == numStopsForRt - 1);

      int rtArrival = -1;
      int rtDeparture = -1;
      boolean hasArrival = stopUpdate.hasArrivalUpdate();
      boolean hasDeparture = stopUpdate.hasDepartureUpdate();

      if (hasArrival) {
        rtArrival = stopUpdate.arrivalUpdate().resolveTime(0);
      }
      if (hasDeparture) {
        rtDeparture = stopUpdate.departureUpdate().resolveTime(0);
      }

      // Apply fallback: if one is missing, use the other
      if (rtArrival < 0 && rtDeparture >= 0) {
        rtArrival = rtDeparture;
      }
      if (rtDeparture < 0 && rtArrival >= 0) {
        rtDeparture = rtArrival;
      }

      // First stop: if only departure was provided, use it for arrival too
      if (isFirstStop && !hasArrival && rtDeparture >= 0) {
        rtArrival = rtDeparture;
      }
      // Last stop: if only arrival was provided, use it for departure too
      if (isLastStop && !hasDeparture && rtArrival >= 0) {
        rtDeparture = rtArrival;
      }

      if (rtArrival >= 0) {
        rtBuilder.withArrivalTime(i, rtArrival);
      }
      if (rtDeparture >= 0) {
        rtBuilder.withDepartureTime(i, rtDeparture);
      }

      if (stopUpdate.recorded()) {
        rtBuilder.withRecorded(i);
      }
    }

    var realTimeTripTimes = rtBuilder.build();

    // Create TripOnServiceDate for the added trip
    var tripOnServiceDate = TripOnServiceDate.of(tripRef.tripId())
      .withTrip(trip)
      .withServiceDate(serviceDate)
      .build();

    var realTimeTripUpdate = new RealTimeTripUpdate(
      pattern,
      realTimeTripTimes,
      serviceDate,
      tripOnServiceDate,
      true,
      routeCreation
    );
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

    // Resolve trip based on reference type
    var trip = resolveTrip(tripRef, serviceDate);
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

    // Create stop times from updates - validate stop siblings where applicable
    List<StopTime> newStopTimes = new ArrayList<>();
    for (int i = 0; i < stopTimeUpdates.size(); i++) {
      var stopUpdate = stopTimeUpdates.get(i);
      var stop = resolveStop(stopUpdate.stopReference(), context.feedId());
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

      // Validate that the stop is either the original stop or a sibling (same station)
      if (i < originalPattern.numberOfStops()) {
        var originalStop = originalPattern.getStop(i);
        if (!stop.getId().equals(originalStop.getId())) {
          // Check if they're siblings (same parent station)
          boolean areSiblings =
            stop.getParentStation() != null &&
            originalStop.getParentStation() != null &&
            stop.getParentStation().equals(originalStop.getParentStation());
          if (!areSiblings) {
            LOG.debug(
              "Stop mismatch at position {} for trip {} - {} is not a sibling of {}",
              i,
              tripRef.tripId(),
              stop.getId(),
              originalStop.getId()
            );
            return Result.failure(
              new UpdateError(
                tripRef.tripId(),
                UpdateError.UpdateErrorType.STOP_MISMATCH,
                i,
                context.feedId()
              )
            );
          }
        }
      }

      var stopTime = new StopTime();
      stopTime.setStop(stop);
      stopTime.setStopSequence(i);

      // Get scheduled time for this stop if it exists in original pattern
      int scheduledArrival = i < tripTimes.getNumStops() ? tripTimes.getScheduledArrivalTime(i) : 0;
      int scheduledDeparture = i < tripTimes.getNumStops()
        ? tripTimes.getScheduledDepartureTime(i)
        : 0;

      // Resolve arrival and departure times - ensure both are set to prevent -1 values
      int arrivalTime;
      int departureTime;

      if (stopUpdate.hasArrivalUpdate() && stopUpdate.hasDepartureUpdate()) {
        // Both provided
        arrivalTime = stopUpdate.arrivalUpdate().resolveTime(scheduledArrival);
        departureTime = stopUpdate.departureUpdate().resolveTime(scheduledDeparture);
      } else if (stopUpdate.hasArrivalUpdate()) {
        // Only arrival - set departure to same as arrival
        arrivalTime = stopUpdate.arrivalUpdate().resolveTime(scheduledArrival);
        departureTime = arrivalTime;
      } else if (stopUpdate.hasDepartureUpdate()) {
        // Only departure - set arrival to same as departure
        departureTime = stopUpdate.departureUpdate().resolveTime(scheduledDeparture);
        arrivalTime = departureTime;
      } else {
        // Neither - use scheduled times
        arrivalTime = scheduledArrival;
        departureTime = scheduledDeparture;
      }

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

    // Get trip pattern (reuse original or create new using cache)
    TripPattern pattern;
    if (stopPatternChanged) {
      // Get or create pattern with modified stops using cache
      pattern = tripPatternCache.getOrCreateTripPattern(newStopPattern, trip);
    } else {
      // Reuse original pattern if stops didn't change
      pattern = originalPattern;
    }

    // Create real-time trip times from scheduled
    var rtBuilder = scheduledTripTimes.createRealTimeFromScheduledTimes();

    // Apply recorded flag for each stop
    for (int i = 0; i < stopTimeUpdates.size(); i++) {
      var stopUpdate = stopTimeUpdates.get(i);
      if (stopUpdate.recorded()) {
        rtBuilder.withRecorded(i);
      }
    }

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

    // Try fuzzy matching if tripId is null
    if (tripReference.tripId() == null) {
      if (tripMatcher != null) {
        return handleFuzzyMatch(parsedUpdate, context);
      }
      return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.NO_TRIP_ID));
    }

    // Resolve the trip based on reference type
    var trip = resolveTrip(tripReference, serviceDate);
    if (trip == null) {
      return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.TRIP_NOT_FOUND));
    }

    // Get the original SCHEDULED pattern (not the realtime modified one)
    // Use findPattern without serviceDate to get the scheduled pattern
    var originalPattern = transitService.findPattern(trip);
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
      return Result.failure(
        UpdateError.noTripId(UpdateError.UpdateErrorType.INVALID_STOP_SEQUENCE)
      );
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
      var stop = resolveStop(stopRef, context.feedId());

      if (stop == null) {
        return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN_STOP));
      }

      // Validate that non-extra stops match the original pattern
      if (!stopTimeUpdate.isExtraCall()) {
        var originalStop = originalPattern.getStop(originalStopIndex);
        if (!stop.getId().equals(originalStop.getId())) {
          return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.STOP_MISMATCH));
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

    // Get or create trip pattern with the modified stop pattern using cache
    var newPattern = tripPatternCache.getOrCreateTripPattern(newStopPattern, trip);

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

      if (stopTimeUpdate.recorded()) {
        rtBuilder.withRecorded(i);
      }
    }

    var realTimeTripTimes = rtBuilder.build();
    var realTimeTripUpdate = new RealTimeTripUpdate(newPattern, realTimeTripTimes, serviceDate);
    return Result.success(realTimeTripUpdate);
  }

  /**
   * Resolves a stop from a StopReference, handling both GTFS-style stop IDs and SIRI-style
   * stop point references.
   *
   * @param stopRef The stop reference to resolve
   * @param feedId The feed ID to use when creating FeedScopedIds for SIRI stop point refs
   * @return The resolved RegularStop, or null if not found
   */
  /**
   * Checks if the stop time updates indicate a stop pattern change (e.g., quay change).
   * Returns true if any of the update stops don't match the corresponding pattern stops.
   */
  private boolean detectStopPatternChange(
    List<org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate> stopUpdates,
    TripPattern pattern,
    String feedId
  ) {
    int numPatternStops = pattern.numberOfStops();
    int updateIndex = 0;

    LOG.debug(
      "detectStopPatternChange: {} pattern stops, {} update stops",
      numPatternStops,
      stopUpdates.size()
    );

    for (int i = 0; i < numPatternStops && updateIndex < stopUpdates.size(); i++) {
      var stopUpdate = stopUpdates.get(updateIndex);
      var stopRef = stopUpdate.stopReference();
      var patternStop = pattern.getStop(i);

      var resolvedStop = resolveStop(stopRef, feedId);
      LOG.debug(
        "detectStopPatternChange: i={}, updateIndex={}, stopRef={}, resolvedStop={}, patternStop={}",
        i,
        updateIndex,
        stopRef,
        resolvedStop != null ? resolvedStop.getId() : null,
        patternStop.getId()
      );
      if (resolvedStop == null) {
        // Unknown stop - could be a pattern change
        continue;
      }

      // Check if the resolved stop matches the pattern stop
      if (!resolvedStop.getId().equals(patternStop.getId())) {
        // Stops don't match - this is a pattern change
        LOG.debug("detectStopPatternChange: DETECTED pattern change at position {}", i);
        return true;
      }

      updateIndex++;
    }

    LOG.debug("detectStopPatternChange: NO pattern change detected");
    return false;
  }

  @Nullable
  private RegularStop resolveStop(StopReference stopRef, String feedId) {
    // GTFS-style: direct stop ID lookup
    if (stopRef.stopId() != null) {
      return transitService.getRegularStop(stopRef.stopId());
    }
    // SIRI-style: stop point reference (quay) - resolve via scheduled stop point or direct ID
    if (stopRef.stopPointRef() != null) {
      var id = new FeedScopedId(feedId, stopRef.stopPointRef());
      return transitService
        .findStopByScheduledStopPoint(id)
        .orElseGet(() -> transitService.getRegularStop(id));
    }
    return null;
  }

  /**
   * Resolves a trip based on the trip reference type.
   * For DATED_SERVICE_JOURNEY, uses TripOnServiceDate lookup by ID, with fallback to direct lookup.
   * For STANDARD, uses direct trip ID lookup.
   */
  @Nullable
  private Trip resolveTrip(TripReference tripRef, LocalDate serviceDate) {
    if (tripRef.tripReferenceType() == TripReference.TripReferenceType.DATED_SERVICE_JOURNEY) {
      // SIRI dated vehicle journey ref - first try TripOnServiceDate lookup
      var tripOnServiceDate = transitService.getTripOnServiceDate(tripRef.tripId());
      if (tripOnServiceDate != null) {
        return tripOnServiceDate.getTrip();
      }
      // Fallback: try direct trip lookup (for dynamically added trips)
      return transitService.getTrip(tripRef.tripId());
    } else {
      // Standard trip reference - direct lookup
      return transitService.getTrip(tripRef.tripId());
    }
  }

  /**
   * Handles fuzzy matching when trip ID is not available.
   * Delegates to the TripMatcher to find the trip by alternative identifiers.
   */
  private Result<RealTimeTripUpdate, UpdateError> handleFuzzyMatch(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    var matchResult = tripMatcher.match(parsedUpdate, context);

    if (matchResult.isFailure()) {
      return Result.failure(matchResult.failureValue());
    }

    var tripAndPattern = matchResult.successValue();
    var trip = tripAndPattern.trip();

    // Now process the update with the matched trip
    // Create a new ParsedTripUpdate with the resolved trip ID
    var updatedTripRef = org.opentripplanner.updater.trip.model.TripReference.builder()
      .withTripId(trip.getId())
      .withRouteId(parsedUpdate.tripReference().routeId())
      .withStartTime(parsedUpdate.tripReference().startTime())
      .withStartDate(parsedUpdate.tripReference().startDate())
      .withDirection(parsedUpdate.tripReference().direction())
      .withVehicleRef(parsedUpdate.tripReference().vehicleRef())
      .withLineRef(parsedUpdate.tripReference().lineRef())
      .withFuzzyMatchingHint(parsedUpdate.tripReference().fuzzyMatchingHint())
      .build();

    var updatedParsedUpdate = org.opentripplanner.updater.trip.model.ParsedTripUpdate.builder(
      parsedUpdate.updateType(),
      updatedTripRef,
      parsedUpdate.serviceDate()
    )
      .withStopTimeUpdates(parsedUpdate.stopTimeUpdates())
      .withTripCreationInfo(parsedUpdate.tripCreationInfo())
      .withStopPatternModification(parsedUpdate.stopPatternModification())
      .withOptions(parsedUpdate.options())
      .withDataSource(parsedUpdate.dataSource())
      .build();

    // Recursively call the appropriate handler with the resolved trip ID
    return switch (parsedUpdate.updateType()) {
      case UPDATE_EXISTING -> handleUpdateExisting(updatedParsedUpdate, context);
      case ADD_EXTRA_CALLS -> handleAddExtraCalls(updatedParsedUpdate, context);
      default -> Result.failure(
        new UpdateError(
          null,
          UpdateError.UpdateErrorType.UNKNOWN,
          Integer.valueOf(0),
          context.feedId()
        )
      );
    };
  }
}
