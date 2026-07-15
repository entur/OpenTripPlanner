package org.opentripplanner.updater.trip;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;
import org.opentripplanner.transit.model.timetable.TripTimesFactory;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.DataValidationExceptionMapper;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ResolvedTripCreation;
import org.opentripplanner.updater.trip.model.TripCreationInfo;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a brand-new trip that is not part of the static schedule.
 * Maps to GTFS-RT NEW/ADDED and SIRI-ET extra journeys.
 * <p>
 * This class only creates trips. Subsequent updates to a trip added earlier are resolved to
 * a {@link org.opentripplanner.updater.trip.model.ResolvedAddedTripUpdate} and revised by
 * {@link AddedTripReviser}; {@link TripAdder} routes between the two.
 */
public class TripCreator {

  private static final Logger LOG = LoggerFactory.getLogger(TripCreator.class);

  private final String feedId;
  private final AddNewTripValidator validator = new AddNewTripValidator();
  private final TransitEditorService transitService;
  private final DeduplicatorService deduplicator;
  private final TripPatternCache tripPatternCache;
  private final RouteCreationStrategy routeCreationStrategy;

  public TripCreator(
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

  public TripUpdateResult create(ResolvedTripCreation resolvedUpdate) {
    validator.validate(resolvedUpdate);
    var tripCreationInfo = resolvedUpdate.tripCreationInfo();
    LocalDate serviceDate = resolvedUpdate.serviceDate();
    FeedScopedId tripId = tripCreationInfo.tripId();

    // Get or create service ID for this date
    FeedScopedId serviceId = transitService.getOrCreateServiceIdForDate(serviceDate);
    if (serviceId == null) {
      LOG.debug("ADD_TRIP: Cannot get service ID for date {}", serviceDate);
      throw UpdateException.of(tripId, UpdateErrorType.OUTSIDE_SERVICE_PERIOD);
    }

    // Filter stop time updates (GTFS-RT: filter unknown stops, SIRI: fail on unknown stops)
    var stopTimeUpdates = resolvedUpdate.stopTimeUpdates();
    var filteredUpdates = StopTimeUpdates.filterUnknownStops(stopTimeUpdates);

    // Check minimum stops
    if (filteredUpdates.updates().size() < 2) {
      LOG.debug("ADD_TRIP: Trip {} has fewer than 2 stops after filtering", tripId);
      throw UpdateException.of(tripId, UpdateErrorType.TOO_FEW_STOPS);
    }

    // Resolve or create route
    var routeResolution = routeCreationStrategy.resolveOrCreateRoute(
      tripCreationInfo,
      transitService
    );
    Route route = routeResolution.route();
    boolean routeCreation = routeResolution.isNewRoute();

    // Create the trip
    Trip trip = createTrip(tripId, tripCreationInfo, route, serviceId);

    // Build stop pattern from stop time updates
    var stopTimesAndPattern = NewStopPatternFactory.buildNewStopPattern(
      trip,
      filteredUpdates.updates(),
      resolvedUpdate.formatPolicy().firstLastStopTime()
    );

    // Create scheduled trip times
    var scheduledTripTimes = TripTimesFactory.tripTimes(
      trip,
      stopTimesAndPattern.stopTimes(),
      deduplicator
    ).withServiceCode(transitService.getTripCalendars().getServiceCode(serviceId));

    // Validate times
    try {
      scheduledTripTimes.validateNonIncreasingTimes();
    } catch (DataValidationException e) {
      LOG.info("Invalid scheduled times for added trip {}: {}", tripId, e.getMessage());
      throw DataValidationExceptionMapper.map(e);
    }

    // Create the new pattern
    // For SIRI (INCLUDE), we add scheduled trip times so queries for aimed times work
    // For GTFS-RT (EXCLUDE), we don't add to scheduled timetable
    boolean includeScheduledData = resolvedUpdate
      .formatPolicy()
      .scheduledData()
      .includesScheduledData();

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
    StopTimeUpdates.applyRealTimeUpdates(tripCreationInfo, builder, filteredUpdates.updates());
    // Extra journeys always retain the "added" flag, even when all stops are cancelled,
    // because they were never part of the static schedule.
    builder.withAdded();
    if (resolvedUpdate.isCancellation() || resolvedUpdate.isAllStopsCancelled()) {
      builder.withCanceled();
    }

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
      .withRealtimeExtraJourney(true)
      .withReplacementFor(replacedTrips)
      .build();

    // Build and return result
    try {
      // tripCreation=true since we're creating a new trip
      var realTimeTripUpdate = RealTimeTripUpdate.of(pattern, builder.build(), serviceDate)
        .withAddedTripOnServiceDate(tripOnServiceDate)
        .withTripCreation(true)
        .withRouteCreation(routeCreation)
        .withProducer(resolvedUpdate.dataSource())
        .build();

      LOG.debug("Added trip {} on {} with pattern {}", tripId, serviceDate, pattern.getId());
      return new TripUpdateResult(realTimeTripUpdate, filteredUpdates.warnings());
    } catch (DataValidationException e) {
      LOG.info("Invalid real-time data for added trip {}: {}", tripId, e.getMessage());
      throw DataValidationExceptionMapper.map(e);
    }
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
    tripCreationInfo.applyTo(builder, route);
    return builder.build();
  }
}
