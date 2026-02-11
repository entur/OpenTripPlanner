package org.opentripplanner.updater.trip.handlers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.organization.Agency;
import org.opentripplanner.transit.model.timetable.RealTimeState;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.model.timetable.TripTimesFactory;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.DataValidationExceptionMapper;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.spi.UpdateSuccess;
import org.opentripplanner.updater.trip.TripUpdateApplierContext;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.RouteCreationInfo;
import org.opentripplanner.updater.trip.model.StopReplacementConstraint;
import org.opentripplanner.updater.trip.model.StopUpdateStrategy;
import org.opentripplanner.updater.trip.model.TripCreationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles adding new trips that are not in the schedule.
 * Maps to GTFS-RT NEW/ADDED and SIRI-ET extra journeys.
 */
public class AddNewTripHandler implements TripUpdateHandler {

  private static final Logger LOG = LoggerFactory.getLogger(AddNewTripHandler.class);

  @Override
  public Result<TripUpdateResult, UpdateError> handle(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context,
    TransitEditorService transitService
  ) {
    // Validate tripCreationInfo is present
    var tripCreationInfo = parsedUpdate.tripCreationInfo();
    if (tripCreationInfo == null) {
      LOG.debug("ADD_TRIP: No trip creation info provided");
      return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN));
    }

    FeedScopedId tripId = tripCreationInfo.tripId();

    // Check if trip already exists in scheduled data
    if (transitService.getScheduledTrip(tripId) != null) {
      LOG.debug("ADD_TRIP: Trip {} already exists in scheduled data", tripId);
      return Result.failure(
        new UpdateError(tripId, UpdateError.UpdateErrorType.TRIP_ALREADY_EXISTS)
      );
    }

    // Resolve service date
    var serviceDateResult = context.serviceDateResolver().resolveServiceDate(parsedUpdate);
    if (serviceDateResult.isFailure()) {
      return Result.failure(serviceDateResult.failureValue());
    }
    LocalDate serviceDate = serviceDateResult.successValue();

    // Check if trip was already added in real-time (update rather than add)
    Trip existingRealTimeTrip = transitService.getTrip(tripId);
    if (existingRealTimeTrip != null) {
      LOG.debug("ADD_TRIP: Trip {} already exists as real-time added trip, updating", tripId);
      return updateExistingAddedTrip(
        existingRealTimeTrip,
        parsedUpdate,
        serviceDate,
        context,
        transitService
      );
    }

    // Get or create service ID for this date
    FeedScopedId serviceId = transitService.getOrCreateServiceIdForDate(serviceDate);
    if (serviceId == null) {
      LOG.debug("ADD_TRIP: Cannot get service ID for date {}", serviceDate);
      return Result.failure(
        new UpdateError(tripId, UpdateError.UpdateErrorType.OUTSIDE_SERVICE_PERIOD)
      );
    }

    // Filter stop time updates (GTFS-RT: filter unknown stops, SIRI: fail on unknown stops)
    var stopTimeUpdates = parsedUpdate.stopTimeUpdates();
    var filtered = filterStopTimeUpdates(
      stopTimeUpdates,
      parsedUpdate.options().stopReplacementConstraint(),
      context,
      tripId
    );
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
    var routeResult = resolveOrCreateRoute(tripCreationInfo, context, transitService);
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
      context.stopResolver(),
      serviceDate,
      context.timeZone()
    );
    if (stopPatternResult.isFailure()) {
      return Result.failure(stopPatternResult.failureValue());
    }
    var stopTimesAndPattern = stopPatternResult.successValue();

    // Create scheduled trip times
    var scheduledTripTimes = TripTimesFactory.tripTimes(
      trip,
      stopTimesAndPattern.stopTimes(),
      transitService.getDeduplicator()
    ).withServiceCode(transitService.getServiceCode(serviceId));

    // Validate times
    try {
      scheduledTripTimes.validateNonIncreasingTimes();
    } catch (DataValidationException e) {
      LOG.info("Invalid scheduled times for added trip {}: {}", tripId, e.getMessage());
      return DataValidationExceptionMapper.toResult(e);
    }

    // Create the new pattern
    // For SIRI (FULL_UPDATE), we add scheduled trip times so queries for aimed times work
    // For GTFS-RT (PARTIAL_UPDATE), we don't add to scheduled timetable
    var tripPatternCache = context.tripPatternCache();
    var options = parsedUpdate.options();
    boolean includeScheduledData = options.stopUpdateStrategy() == StopUpdateStrategy.FULL_UPDATE;

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
    HandlerUtils.applyRealTimeUpdates(
      parsedUpdate,
      builder,
      filteredUpdates.updates(),
      serviceDate,
      context.timeZone()
    );
    builder.withRealTimeState(RealTimeState.ADDED);

    // Apply wheelchair accessibility
    if (tripCreationInfo.wheelchairAccessibility() != null) {
      builder.withWheelchairAccessibility(tripCreationInfo.wheelchairAccessibility());
    }

    // Create TripOnServiceDate for lookup by dated vehicle journey
    TripOnServiceDate tripOnServiceDate = TripOnServiceDate.of(tripId)
      .withTrip(trip)
      .withServiceDate(serviceDate)
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
    Trip existingTrip,
    ParsedTripUpdate parsedUpdate,
    LocalDate serviceDate,
    TripUpdateApplierContext context,
    TransitEditorService transitService
  ) {
    FeedScopedId tripId = existingTrip.getId();

    // Filter stop time updates
    var stopTimeUpdates = parsedUpdate.stopTimeUpdates();
    var filtered = filterStopTimeUpdates(
      stopTimeUpdates,
      parsedUpdate.options().stopReplacementConstraint(),
      context,
      tripId
    );
    if (filtered.isFailure()) {
      return Result.failure(filtered.failureValue());
    }
    var filteredUpdates = filtered.successValue();

    // Find the existing pattern for this trip
    TripPattern existingPattern = transitService.findPattern(existingTrip, serviceDate);
    if (existingPattern == null) {
      existingPattern = transitService.findPattern(existingTrip);
    }
    if (existingPattern == null) {
      LOG.warn("UPDATE_ADDED_TRIP: Could not find pattern for existing trip {}", tripId);
      return Result.failure(
        new UpdateError(tripId, UpdateError.UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN)
      );
    }

    // Get the scheduled trip times from the pattern (for added trips, this is the original aimed times)
    TripTimes scheduledTripTimes = existingPattern
      .getScheduledTimetable()
      .getTripTimes(existingTrip);
    if (scheduledTripTimes == null) {
      LOG.warn("UPDATE_ADDED_TRIP: Could not find scheduled trip times for trip {}", tripId);
      return Result.failure(
        new UpdateError(tripId, UpdateError.UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN)
      );
    }

    // Create real-time trip times from the scheduled times
    var builder = scheduledTripTimes.createRealTimeFromScheduledTimes();
    HandlerUtils.applyRealTimeUpdates(
      parsedUpdate,
      builder,
      filteredUpdates.updates(),
      serviceDate,
      context.timeZone()
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
    List<ParsedStopTimeUpdate> updates,
    List<UpdateSuccess.WarningType> warnings
  ) {}

  /**
   * Filter stop time updates to remove unknown stops.
   * For GTFS-RT: filter out unknown stops and add warning
   * For SIRI: fail if any stop is unknown
   */
  private Result<FilteredStopTimeUpdates, UpdateError> filterStopTimeUpdates(
    List<ParsedStopTimeUpdate> updates,
    StopReplacementConstraint constraint,
    TripUpdateApplierContext context,
    FeedScopedId tripId
  ) {
    var stopResolver = context.stopResolver();
    var warnings = new ArrayList<UpdateSuccess.WarningType>();

    // SIRI mode: strict validation - fail on unknown stops
    boolean strictMode = constraint == StopReplacementConstraint.SAME_PARENT_STATION;
    if (strictMode) {
      for (int i = 0; i < updates.size(); i++) {
        var stopUpdate = updates.get(i);
        if (stopResolver.resolve(stopUpdate.stopReference()) == null) {
          LOG.debug("ADD_TRIP: Unknown stop {} in SIRI extra journey", stopUpdate.stopReference());
          return Result.failure(
            new UpdateError(tripId, UpdateError.UpdateErrorType.UNKNOWN_STOP, i)
          );
        }
      }
      return Result.success(new FilteredStopTimeUpdates(updates, warnings));
    }

    // GTFS-RT mode: filter unknown stops
    var filteredUpdates = new ArrayList<ParsedStopTimeUpdate>();
    for (var stopUpdate : updates) {
      if (stopResolver.resolve(stopUpdate.stopReference()) != null) {
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
   * Resolve an existing route or create a new one.
   */
  private Result<Route, UpdateError> resolveOrCreateRoute(
    TripCreationInfo tripCreationInfo,
    TripUpdateApplierContext context,
    TransitEditorService transitService
  ) {
    FeedScopedId tripId = tripCreationInfo.tripId();

    // First try to find route by explicit routeId
    if (tripCreationInfo.routeId() != null) {
      Route existingRoute = findRoute(tripCreationInfo.routeId(), context, transitService);
      if (existingRoute != null) {
        LOG.debug("ADD_TRIP: Using existing route {}", existingRoute.getId());
        return Result.success(existingRoute);
      }
    }

    // Try routeCreationInfo.routeId
    if (tripCreationInfo.routeCreationInfo() != null) {
      FeedScopedId routeId = tripCreationInfo.routeCreationInfo().routeId();
      Route existingRoute = findRoute(routeId, context, transitService);
      if (existingRoute != null) {
        LOG.debug("ADD_TRIP: Using existing route from routeCreationInfo {}", routeId);
        return Result.success(existingRoute);
      }
    }

    // Need to create a new route
    if (tripCreationInfo.routeCreationInfo() != null) {
      return createRoute(
        tripCreationInfo.routeCreationInfo(),
        tripCreationInfo,
        context,
        transitService
      );
    }

    // No routeCreationInfo, but we have a routeId - create a route using that ID
    if (tripCreationInfo.routeId() != null) {
      return createRouteFromRouteId(
        tripCreationInfo.routeId(),
        tripCreationInfo,
        context,
        transitService
      );
    }

    // No route info - create minimal route using trip ID
    return createFallbackRoute(tripId, context, transitService);
  }

  /**
   * Find a route by ID, checking the route cache first, then the transit service.
   */
  private Route findRoute(
    FeedScopedId routeId,
    TripUpdateApplierContext context,
    TransitEditorService transitService
  ) {
    // Check route cache first
    if (context.routeCache() != null) {
      Route cachedRoute = context.routeCache().apply(routeId);
      if (cachedRoute != null) {
        return cachedRoute;
      }
    }
    // Fall back to transit service
    return transitService.getRoute(routeId);
  }

  /**
   * Create a new route from route creation info.
   */
  private Result<Route, UpdateError> createRoute(
    RouteCreationInfo routeCreationInfo,
    TripCreationInfo tripCreationInfo,
    TripUpdateApplierContext context,
    TransitEditorService transitService
  ) {
    FeedScopedId routeId = routeCreationInfo.routeId();
    var builder = Route.of(routeId);

    // Find agency
    Agency agency = findAgencyForRoute(
      tripCreationInfo,
      routeCreationInfo,
      context,
      transitService
    );
    builder.withAgency(agency);

    // Set mode
    TransitMode mode = routeCreationInfo.mode();
    if (mode == null && tripCreationInfo.mode() != null) {
      mode = tripCreationInfo.mode();
    }
    if (mode == null) {
      mode = TransitMode.BUS;
    }
    builder.withMode(mode);

    // Set submode
    String submode = routeCreationInfo.submode();
    if (submode == null && tripCreationInfo.submode() != null) {
      submode = tripCreationInfo.submode();
    }
    if (submode != null) {
      builder.withNetexSubmode(submode);
    }

    // Set name
    I18NString name = NonLocalizedString.ofNullable(routeCreationInfo.routeName());
    if (name == null) {
      name = NonLocalizedString.ofNullable(routeId.getId());
    }
    builder.withLongName(name);

    // Set URL
    if (routeCreationInfo.url() != null) {
      builder.withUrl(routeCreationInfo.url());
    }

    Route route = builder.build();
    LOG.debug("ADD_TRIP: Created new route {}", routeId);
    return Result.success(route);
  }

  /**
   * Create a new route using the routeId from TripCreationInfo when no routeCreationInfo is
   * provided. This happens for SIRI extra journeys where lineRef doesn't match an existing route.
   */
  private Result<Route, UpdateError> createRouteFromRouteId(
    FeedScopedId routeId,
    TripCreationInfo tripCreationInfo,
    TripUpdateApplierContext context,
    TransitEditorService transitService
  ) {
    var builder = Route.of(routeId);

    // Find an agency
    Agency agency = findFallbackAgencyFromTripInfo(tripCreationInfo, context, transitService);
    builder.withAgency(agency);

    // Set mode from trip creation info or default to BUS
    TransitMode mode = tripCreationInfo.mode() != null ? tripCreationInfo.mode() : TransitMode.BUS;
    builder.withMode(mode);

    // Set submode if provided
    if (tripCreationInfo.submode() != null) {
      builder.withNetexSubmode(tripCreationInfo.submode());
    }

    // Use route ID as name
    builder.withLongName(NonLocalizedString.ofNullable(routeId.getId()));

    Route route = builder.build();
    LOG.debug("ADD_TRIP: Created new route from routeId {}", routeId);
    return Result.success(route);
  }

  /**
   * Create a fallback route when no route info is provided.
   */
  private Result<Route, UpdateError> createFallbackRoute(
    FeedScopedId tripId,
    TripUpdateApplierContext context,
    TransitEditorService transitService
  ) {
    // Use trip ID as route ID for fallback route
    var builder = Route.of(tripId);

    // Find a fallback agency
    Agency agency = findFallbackAgency(context.feedId(), transitService);
    builder.withAgency(agency);
    builder.withMode(TransitMode.BUS);
    builder.withLongName(NonLocalizedString.ofNullable(tripId.getId()));

    Route route = builder.build();
    LOG.debug("ADD_TRIP: Created fallback route {}", tripId);
    return Result.success(route);
  }

  /**
   * Find an agency for a new route.
   */
  private Agency findAgencyForRoute(
    TripCreationInfo tripCreationInfo,
    RouteCreationInfo routeCreationInfo,
    TripUpdateApplierContext context,
    TransitEditorService transitService
  ) {
    // Try operator from route creation info
    if (routeCreationInfo.operatorId() != null) {
      var agency = transitService.findAgency(routeCreationInfo.operatorId());
      if (agency.isPresent()) {
        return agency.get();
      }
    }

    // Try operator from trip creation info
    if (tripCreationInfo.operatorId() != null) {
      var agency = transitService.findAgency(tripCreationInfo.operatorId());
      if (agency.isPresent()) {
        return agency.get();
      }
    }

    // Try to find agency from replaced trips
    for (FeedScopedId replacedTripId : tripCreationInfo.replacedTrips()) {
      Trip replacedTrip = transitService.getScheduledTrip(replacedTripId);
      if (replacedTrip != null) {
        return replacedTrip.getRoute().getAgency();
      }
    }

    return findFallbackAgency(context.feedId(), transitService);
  }

  /**
   * Find an agency for a route when we only have TripCreationInfo (no RouteCreationInfo).
   */
  private Agency findFallbackAgencyFromTripInfo(
    TripCreationInfo tripCreationInfo,
    TripUpdateApplierContext context,
    TransitEditorService transitService
  ) {
    // Try operator from trip creation info
    if (tripCreationInfo.operatorId() != null) {
      var agency = transitService.findAgency(tripCreationInfo.operatorId());
      if (agency.isPresent()) {
        return agency.get();
      }
    }

    // Try to find agency from replaced trips
    for (FeedScopedId replacedTripId : tripCreationInfo.replacedTrips()) {
      Trip replacedTrip = transitService.getScheduledTrip(replacedTripId);
      if (replacedTrip != null) {
        return replacedTrip.getRoute().getAgency();
      }
    }

    return findFallbackAgency(context.feedId(), transitService);
  }

  /**
   * Find or create a fallback agency.
   */
  private Agency findFallbackAgency(String feedId, TransitEditorService transitService) {
    // Try to use any existing agency from the feed
    for (Agency agency : transitService.listAgencies()) {
      if (agency.getId().getFeedId().equals(feedId)) {
        return agency;
      }
    }

    // Create a dummy agency
    return Agency.of(new FeedScopedId(feedId, "autogenerated-gtfs-rt-added-route"))
      .withName("Agency automatically added by real-time update")
      .withTimezone(transitService.getTimeZone().toString())
      .build();
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
