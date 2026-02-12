package org.opentripplanner.updater.trip.handlers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.model.framework.DeduplicatorService;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeState;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;
import org.opentripplanner.transit.model.timetable.TripTimesFactory;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.DataValidationExceptionMapper;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.spi.UpdateSuccess;
import org.opentripplanner.updater.trip.model.ResolvedNewTrip;
import org.opentripplanner.updater.trip.model.ResolvedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ScheduledDataInclusion;
import org.opentripplanner.updater.trip.model.TripCreationInfo;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles adding new trips that are not in the schedule.
 * Maps to GTFS-RT NEW/ADDED and SIRI-ET extra journeys.
 * <p>
 * This handler receives a {@link ResolvedNewTrip} which may contain:
 * <ul>
 *   <li>No trip (new trip creation)</li>
 *   <li>Existing trip (update to previously added trip)</li>
 * </ul>
 */
public class AddNewTripHandler implements TripUpdateHandler.ForNewTrip {

  private static final Logger LOG = LoggerFactory.getLogger(AddNewTripHandler.class);

  private final String feedId;
  private final TransitEditorService transitService;
  private final DeduplicatorService deduplicator;
  private final TripPatternCache tripPatternCache;
  private final RouteCreationStrategy routeCreationStrategy;

  public AddNewTripHandler(
    String feedId,
    TransitEditorService transitService,
    DeduplicatorService deduplicator,
    TripPatternCache tripPatternCache,
    RouteCreationStrategy routeCreationStrategy
  ) {
    this.feedId = Objects.requireNonNull(feedId);
    this.transitService = Objects.requireNonNull(transitService);
    this.deduplicator = Objects.requireNonNull(deduplicator);
    this.tripPatternCache = Objects.requireNonNull(tripPatternCache);
    this.routeCreationStrategy = Objects.requireNonNull(routeCreationStrategy);
  }

  @Override
  public Result<TripUpdateResult, UpdateError> handle(ResolvedNewTrip resolvedUpdate) {
    var tripCreationInfo = resolvedUpdate.tripCreationInfo();
    LocalDate serviceDate = resolvedUpdate.serviceDate();

    // Check if this is an update to an existing added trip
    if (resolvedUpdate.isUpdateToExistingTrip()) {
      return updateExistingAddedTrip(resolvedUpdate, transitService);
    }

    // Creating a new trip
    FeedScopedId tripId = tripCreationInfo.tripId();

    // Get or create service ID for this date
    FeedScopedId serviceId = transitService.getOrCreateServiceIdForDate(serviceDate);
    if (serviceId == null) {
      LOG.debug("ADD_TRIP: Cannot get service ID for date {}", serviceDate);
      return Result.failure(
        new UpdateError(tripId, UpdateError.UpdateErrorType.OUTSIDE_SERVICE_PERIOD)
      );
    }

    // Filter stop time updates (GTFS-RT: filter unknown stops, SIRI: fail on unknown stops)
    var stopTimeUpdates = resolvedUpdate.stopTimeUpdates();
    var filtered = filterStopTimeUpdates(stopTimeUpdates, tripId);
    if (filtered.isFailure()) {
      return Result.failure(filtered.failureValue());
    }
    var filteredUpdates = filtered.successValue();

    // Check minimum stops
    if (filteredUpdates.updates().size() < 2) {
      LOG.debug("ADD_TRIP: Trip {} has fewer than 2 stops after filtering", tripId);
      return Result.failure(new UpdateError(tripId, UpdateError.UpdateErrorType.TOO_FEW_STOPS));
    }

    // Resolve or create route
    var routeResult = routeCreationStrategy.resolveOrCreateRoute(tripCreationInfo, transitService);
    if (routeResult.isFailure()) {
      return Result.failure(routeResult.failureValue());
    }
    Route route = routeResult.successValue();

    // Create the trip
    Trip trip = createTrip(tripId, tripCreationInfo, route, serviceId);

    // Build stop pattern from stop time updates
    var stopPatternResult = HandlerUtils.buildNewStopPattern(
      trip,
      filteredUpdates.updates(),
      resolvedUpdate.options().firstLastStopTimeAdjustment()
    );
    if (stopPatternResult.isFailure()) {
      return Result.failure(stopPatternResult.failureValue());
    }
    var stopTimesAndPattern = stopPatternResult.successValue();

    // Create scheduled trip times
    var scheduledTripTimes = TripTimesFactory.tripTimes(
      trip,
      stopTimesAndPattern.stopTimes(),
      deduplicator
    ).withServiceCode(transitService.getServiceCode(serviceId));

    // Validate times
    try {
      scheduledTripTimes.validateNonIncreasingTimes();
    } catch (DataValidationException e) {
      LOG.info("Invalid scheduled times for added trip {}: {}", tripId, e.getMessage());
      return DataValidationExceptionMapper.toResult(e);
    }

    // Create the new pattern
    // For SIRI (INCLUDE), we add scheduled trip times so queries for aimed times work
    // For GTFS-RT (EXCLUDE), we don't add to scheduled timetable
    var options = resolvedUpdate.options();
    boolean includeScheduledData =
      options.scheduledDataInclusion() == ScheduledDataInclusion.INCLUDE;

    TripPattern pattern;
    if (includeScheduledData) {
      // SIRI-style: include scheduled times
      pattern = TripPattern.of(tripPatternCache.generatePatternId(trip))
        .withRoute(route)
        .withMode(trip.getMode())
        .withNetexSubmode(trip.getNetexSubMode())
        .withStopPattern(stopTimesAndPattern.stopPattern())
        .withRealTimeAddedTrip()
        .withScheduledTimeTableBuilder(builder -> builder.addTripTimes(scheduledTripTimes))
        .build();
    } else {
      // GTFS-RT style: no scheduled times
      pattern = TripPattern.of(tripPatternCache.generatePatternId(trip))
        .withRoute(route)
        .withMode(trip.getMode())
        .withNetexSubmode(trip.getNetexSubMode())
        .withStopPattern(stopTimesAndPattern.stopPattern())
        .withRealTimeStopPatternModified()
        .build();
    }

    // Create real-time trip times
    var builder = scheduledTripTimes.createRealTimeFromScheduledTimes();
    HandlerUtils.applyRealTimeUpdates(tripCreationInfo, builder, filteredUpdates.updates());
    builder.withRealTimeState(RealTimeState.ADDED);

    // Apply wheelchair accessibility
    if (tripCreationInfo.wheelchairAccessibility() != null) {
      builder.withWheelchairAccessibility(tripCreationInfo.wheelchairAccessibility());
    }

    // Create TripOnServiceDate for lookup by dated vehicle journey
    // Resolve replacement trips from TripCreationInfo
    List<TripOnServiceDate> replacedTrips = tripCreationInfo
      .replacedTrips()
      .stream()
      .map(transitService::getTripOnServiceDate)
      .filter(Objects::nonNull)
      .toList();

    TripOnServiceDate tripOnServiceDate = TripOnServiceDate.of(tripId)
      .withTrip(trip)
      .withServiceDate(serviceDate)
      .withReplacementFor(replacedTrips)
      .build();

    // Build and return result
    try {
      // tripCreation=true since we're creating a new trip
      // routeCreation=true since we may have created a new route
      var realTimeTripUpdate = new RealTimeTripUpdate(
        pattern,
        builder.build(),
        serviceDate,
        tripOnServiceDate,
        true,
        true
      );

      LOG.debug("Added trip {} on {} with pattern {}", tripId, serviceDate, pattern.getId());
      return Result.success(new TripUpdateResult(realTimeTripUpdate, filteredUpdates.warnings()));
    } catch (DataValidationException e) {
      LOG.info("Invalid real-time data for added trip {}: {}", tripId, e.getMessage());
      return DataValidationExceptionMapper.toResult(e);
    }
  }

  /**
   * Update an existing real-time added trip with new data.
   * This is called when the same trip is added again (subsequent updates to an extra journey).
   */
  private Result<TripUpdateResult, UpdateError> updateExistingAddedTrip(
    ResolvedNewTrip resolvedUpdate,
    TransitEditorService transitService
  ) {
    Trip existingTrip = resolvedUpdate.existingTrip();
    TripPattern existingPattern = resolvedUpdate.existingPattern();
    var scheduledTripTimes = resolvedUpdate.existingTripTimes();
    LocalDate serviceDate = resolvedUpdate.serviceDate();
    FeedScopedId tripId = existingTrip.getId();

    LOG.debug("Updating existing added trip {} on {}", tripId, serviceDate);

    // Filter stop time updates
    var stopTimeUpdates = resolvedUpdate.stopTimeUpdates();
    var filtered = filterStopTimeUpdates(stopTimeUpdates, tripId);
    if (filtered.isFailure()) {
      return Result.failure(filtered.failureValue());
    }
    var filteredUpdates = filtered.successValue();

    // Create real-time trip times from the scheduled times
    var builder = scheduledTripTimes.createRealTimeFromScheduledTimes();
    HandlerUtils.applyRealTimeUpdates(
      resolvedUpdate.tripCreationInfo(),
      builder,
      filteredUpdates.updates()
    );
    builder.withRealTimeState(RealTimeState.UPDATED);

    // Build and return result
    // tripCreation=false since this is an update to an existing added trip
    // routeCreation=false since the route already exists
    try {
      var realTimeTripUpdate = new RealTimeTripUpdate(
        existingPattern,
        builder.build(),
        serviceDate
      );

      LOG.debug("Updated existing added trip {} on {}", tripId, serviceDate);
      return Result.success(new TripUpdateResult(realTimeTripUpdate, filteredUpdates.warnings()));
    } catch (DataValidationException e) {
      LOG.info("Invalid real-time data for updated added trip {}: {}", tripId, e.getMessage());
      return DataValidationExceptionMapper.toResult(e);
    }
  }

  /**
   * Result of filtering stop time updates.
   */
  private record FilteredStopTimeUpdates(
    List<ResolvedStopTimeUpdate> updates,
    List<UpdateSuccess.WarningType> warnings
  ) {}

  /**
   * Filter stop time updates to remove unknown stops.
   * Unknown stops in FAIL mode are caught by the validator before reaching this handler,
   * so this method only needs to handle IGNORE mode filtering.
   */
  private Result<FilteredStopTimeUpdates, UpdateError> filterStopTimeUpdates(
    List<ResolvedStopTimeUpdate> updates,
    FeedScopedId tripId
  ) {
    var warnings = new ArrayList<UpdateSuccess.WarningType>();

    // Filter unknown stops (IGNORE mode)
    var filteredUpdates = new ArrayList<ResolvedStopTimeUpdate>();
    for (var stopUpdate : updates) {
      if (stopUpdate.stop() != null) {
        filteredUpdates.add(stopUpdate);
      } else {
        LOG.debug("ADD_TRIP: Removing unknown stop {} from added trip", stopUpdate.stopReference());
      }
    }

    if (filteredUpdates.size() < updates.size()) {
      warnings.add(UpdateSuccess.WarningType.UNKNOWN_STOPS_REMOVED_FROM_ADDED_TRIP);
    }

    return Result.success(new FilteredStopTimeUpdates(filteredUpdates, warnings));
  }

  /**
   * Create a new trip from trip creation info.
   */
  private Trip createTrip(
    FeedScopedId tripId,
    TripCreationInfo tripCreationInfo,
    Route route,
    FeedScopedId serviceId
  ) {
    var builder = Trip.of(tripId);
    builder.withRoute(route);
    builder.withServiceId(serviceId);

    // Set mode
    if (tripCreationInfo.mode() != null) {
      builder.withMode(tripCreationInfo.mode());
    } else {
      builder.withMode(route.getMode());
    }

    // Set submode
    if (tripCreationInfo.submode() != null) {
      builder.withNetexSubmode(tripCreationInfo.submode());
    }

    // Set headsign
    if (tripCreationInfo.headsign() != null) {
      builder.withHeadsign(tripCreationInfo.headsign());
    }

    // Set short name
    if (tripCreationInfo.shortName() != null) {
      builder.withShortName(tripCreationInfo.shortName());
    }

    return builder.build();
  }
}
