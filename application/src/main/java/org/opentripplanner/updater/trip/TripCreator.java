package org.opentripplanner.updater.trip;

import java.time.LocalDate;
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
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a brand-new trip that is not part of the static schedule.
 * Maps to GTFS-RT NEW/ADDED and SIRI-ET extra journeys.
 * <p>
 * This class only creates trips. Subsequent updates to a trip added earlier are resolved to
 * a {@link org.opentripplanner.updater.trip.model.ResolvedAddedTripUpdate} and updated by
 * {@link AddedTripUpdater}; the {@link NewTripResolver} decides which of the two applies.
 */
public class TripCreator {

  private static final Logger LOG = LoggerFactory.getLogger(TripCreator.class);

  private final TransitEditorService transitService;
  private final DeduplicatorService deduplicator;
  private final TripPatternCache tripPatternCache;
  private final RouteCreationStrategy routeCreationStrategy;

  public TripCreator(
    TransitEditorService transitService,
    DeduplicatorService deduplicator,
    TripPatternCache tripPatternCache,
    RouteCreationStrategy routeCreationStrategy
  ) {
    this.transitService = Objects.requireNonNull(transitService);
    this.deduplicator = Objects.requireNonNull(deduplicator);
    this.tripPatternCache = Objects.requireNonNull(tripPatternCache);
    this.routeCreationStrategy = Objects.requireNonNull(routeCreationStrategy);
  }

  public TripUpdateResult create(ResolvedTripCreation resolvedUpdate) {
    var tripCreationInfo = resolvedUpdate.tripCreationInfo();
    LocalDate serviceDate = resolvedUpdate.serviceDate();
    FeedScopedId tripId = resolvedUpdate.tripId();

    // Filter stop time updates (GTFS-RT: filter unknown stops, SIRI: fail on unknown stops)
    var filteredUpdates = resolvedUpdate.stopTimeUpdatesWithKnownStops();

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
    Trip trip = createTrip(resolvedUpdate, route);

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
    ).withServiceCode(resolvedUpdate.serviceCode());

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
    resolvedUpdate.applyJourneyDescription(builder);
    StopTimeUpdates.applyRealTimeUpdates(builder, filteredUpdates.updates());
    // Extra journeys always retain the "added" flag, even when all stops are cancelled,
    // because they were never part of the static schedule.
    builder.withAdded();
    if (resolvedUpdate.isCancelled()) {
      builder.withCanceled();
    }

    // Create TripOnServiceDate for lookup by dated vehicle journey
    // SIRI names the dated instance of a journey separately - the DatedServiceJourney - and that id
    // identifies the added trip on service date. GTFS-RT names no such entity, so the added trip on
    // service date takes the trip id instead: an added trip is held once per id
    // (realTimeAddedTrips), and a repeat of that id revises the same trip rather than adding a
    // second service date, so the two can never collide.
    var tripOnServiceDateId = tripCreationInfo.tripOnServiceDateId() != null
      ? tripCreationInfo.tripOnServiceDateId()
      : tripId;

    TripOnServiceDate tripOnServiceDate = TripOnServiceDate.of(tripOnServiceDateId)
      .withTrip(trip)
      .withServiceDate(serviceDate)
      .withRealtimeExtraJourney(true)
      .withReplacementFor(resolvedUpdate.replacedTrips())
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
   * Create a new trip from trip creation info. The headsign comes from the update itself rather than
   * the creation info: it is the headsign the trip displays today, and the same value is applied to
   * the real-time trip times.
   */
  private Trip createTrip(ResolvedTripCreation resolvedUpdate, Route route) {
    var builder = Trip.of(resolvedUpdate.tripId());
    builder.withRoute(route);
    builder.withServiceId(resolvedUpdate.serviceId());
    if (resolvedUpdate.tripHeadsign() != null) {
      builder.withHeadsign(resolvedUpdate.tripHeadsign());
    }
    resolvedUpdate.tripCreationInfo().applyTo(builder, route);
    return builder.build();
  }
}
