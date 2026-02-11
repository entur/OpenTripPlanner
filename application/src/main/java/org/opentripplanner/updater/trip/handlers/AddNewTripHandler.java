package org.opentripplanner.updater.trip.handlers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
import org.opentripplanner.transit.model.timetable.TripTimesFactory;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.DataValidationExceptionMapper;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.spi.UpdateSuccess;
import org.opentripplanner.updater.trip.TripUpdateApplierContext;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ResolvedNewTrip;
import org.opentripplanner.updater.trip.model.RouteCreationInfo;
import org.opentripplanner.updater.trip.model.ScheduledDataInclusion;
import org.opentripplanner.updater.trip.model.TripCreationInfo;
import org.opentripplanner.updater.trip.model.UnknownStopBehavior;
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

  @Override
  public Result<TripUpdateResult, UpdateError> handle(
    ResolvedNewTrip resolvedUpdate,
    TripUpdateApplierContext context,
    TransitEditorService transitService
  ) {
    var tripCreationInfo = resolvedUpdate.tripCreationInfo();
    LocalDate serviceDate = resolvedUpdate.serviceDate();

    // Check if this is an update to an existing added trip
    if (resolvedUpdate.isUpdateToExistingTrip()) {
      return updateExistingAddedTrip(resolvedUpdate, context, transitService);
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
    var filtered = filterStopTimeUpdates(
      stopTimeUpdates,
      resolvedUpdate.options().unknownStopBehavior(),
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
      context.timeZone(),
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
    // For SIRI (INCLUDE), we add scheduled trip times so queries for aimed times work
    // For GTFS-RT (EXCLUDE), we don't add to scheduled timetable
    var tripPatternCache = context.tripPatternCache();
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
    HandlerUtils.applyRealTimeUpdates(
      resolvedUpdate.parsedUpdate(),
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
    TripUpdateApplierContext context,
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
    var filtered = filterStopTimeUpdates(
      stopTimeUpdates,
      resolvedUpdate.options().unknownStopBehavior(),
      context,
      tripId
    );
    if (filtered.isFailure()) {
      return Result.failure(filtered.failureValue());
    }
    var filteredUpdates = filtered.successValue();

    // Create real-time trip times from the scheduled times
    var builder = scheduledTripTimes.createRealTimeFromScheduledTimes();
    HandlerUtils.applyRealTimeUpdates(
      resolvedUpdate.parsedUpdate(),
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
   * For GTFS-RT (IGNORE): filter out unknown stops and add warning
   * For SIRI (FAIL): fail if any stop is unknown
   */
  private Result<FilteredStopTimeUpdates, UpdateError> filterStopTimeUpdates(
    List<ParsedStopTimeUpdate> updates,
    UnknownStopBehavior unknownStopBehavior,
    TripUpdateApplierContext context,
    FeedScopedId tripId
  ) {
    var stopResolver = context.stopResolver();
    var warnings = new ArrayList<UpdateSuccess.WarningType>();

    // FAIL mode: strict validation - fail on unknown stops
    if (unknownStopBehavior == UnknownStopBehavior.FAIL) {
      for (int i = 0; i < updates.size(); i++) {
        var stopUpdate = updates.get(i);
        if (stopResolver.resolve(stopUpdate.stopReference()) == null) {
          LOG.debug("ADD_TRIP: Unknown stop {} in added trip", stopUpdate.stopReference());
          return Result.failure(
            new UpdateError(tripId, UpdateError.UpdateErrorType.UNKNOWN_STOP, i)
          );
        }
      }
      return Result.success(new FilteredStopTimeUpdates(updates, warnings));
    }

    // IGNORE mode: filter unknown stops
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
