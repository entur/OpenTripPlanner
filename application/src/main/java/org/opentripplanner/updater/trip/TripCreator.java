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

  private final DeduplicatorService deduplicator;
  private final TripPatternCache tripPatternCache;

  public TripCreator(DeduplicatorService deduplicator, TripPatternCache tripPatternCache) {
    this.deduplicator = Objects.requireNonNull(deduplicator);
    this.tripPatternCache = Objects.requireNonNull(tripPatternCache);
  }

  public TripUpdateResult create(ResolvedTripCreation resolvedUpdate) {
    LocalDate serviceDate = resolvedUpdate.serviceDate();
    FeedScopedId tripId = resolvedUpdate.tripId();

    // Filter stop time updates (GTFS-RT: filter unknown stops, SIRI: fail on unknown stops)
    var filteredUpdates = resolvedUpdate.stopTimeUpdatesWithKnownStops();

    // Check minimum stops
    if (filteredUpdates.updates().size() < 2) {
      LOG.debug("ADD_TRIP: Trip {} has fewer than 2 stops after filtering", tripId);
      throw UpdateException.of(tripId, UpdateErrorType.TOO_FEW_STOPS);
    }

    Route route = resolvedUpdate.route();

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
    TripOnServiceDate tripOnServiceDate = TripOnServiceDate.of(resolvedUpdate.tripOnServiceDateId())
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
        .withRouteCreation(resolvedUpdate.isNewRoute())
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
   * Create the new trip. The headsign comes from the update itself rather than the creation data:
   * it is the headsign the trip displays today, and the same value is applied to the real-time
   * trip times.
   */
  private Trip createTrip(ResolvedTripCreation resolvedUpdate, Route route) {
    var builder = Trip.of(resolvedUpdate.tripId());
    builder.withRoute(route);
    builder.withServiceId(resolvedUpdate.serviceId());
    if (resolvedUpdate.tripHeadsign() != null) {
      builder.withHeadsign(resolvedUpdate.tripHeadsign());
    }
    resolvedUpdate.applyTripDescription(builder);
    return builder.build();
  }
}
