package org.opentripplanner.updater.trip;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ResolvedAddedTripUpdate;
import org.opentripplanner.updater.trip.model.ResolvedNewTrip;
import org.opentripplanner.updater.trip.model.ResolvedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ResolvedTripCreation;
import org.opentripplanner.updater.trip.model.TripAddition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a {@link TripAddition} into a {@link ResolvedNewTrip}.
 * <p>
 * Used for ADD_NEW_TRIP update type.
 * <p>
 * The parsers are state-free, so whether an ADD_NEW_TRIP update creates a trip or updates a
 * previously added one can only be decided here, against the current transit model:
 * <ul>
 *   <li>Trip not yet in the transit model - returns {@link ResolvedTripCreation}</li>
 *   <li>Trip already added in real-time - returns {@link ResolvedAddedTripUpdate}</li>
 * </ul>
 */
public class NewTripResolver {

  private static final Logger LOG = LoggerFactory.getLogger(NewTripResolver.class);

  private final TransitEditorService transitService;
  private final ServiceDateResolver serviceDateResolver;
  private final StopResolver stopResolver;
  private final ZoneId timeZone;

  public NewTripResolver(
    TransitEditorService transitService,
    ServiceDateResolver serviceDateResolver,
    StopResolver stopResolver,
    ZoneId timeZone
  ) {
    this.transitService = Objects.requireNonNull(transitService, "transitService must not be null");
    this.serviceDateResolver = Objects.requireNonNull(
      serviceDateResolver,
      "serviceDateResolver must not be null"
    );
    this.stopResolver = Objects.requireNonNull(stopResolver, "stopResolver must not be null");
    this.timeZone = Objects.requireNonNull(timeZone, "timeZone must not be null");
  }

  /**
   * Resolve a ParsedTripUpdate for adding a new trip.
   *
   * @param parsedUpdate The parsed update to resolve
   * @return the resolved data
   * @throws UpdateException if resolution fails
   */
  public ResolvedNewTrip resolve(TripAddition parsedUpdate) {
    // Resolve service date
    LocalDate serviceDate = serviceDateResolver.resolveServiceDate(parsedUpdate);

    var tripId = parsedUpdate.tripCreationInfo().tripId();

    // Check if trip already exists in scheduled data (error case)
    if (transitService.getScheduledTrip(tripId) != null) {
      LOG.debug("ADD_NEW_TRIP: Trip {} already exists in scheduled data", tripId);
      throw UpdateException.of(tripId, UpdateErrorType.TRIP_ALREADY_EXISTS);
    }

    // Resolve stop time updates now that service date is known
    var resolvedStopTimeUpdates = ResolvedStopTimeUpdate.resolveAll(
      parsedUpdate.stopTimeUpdates(),
      serviceDate,
      timeZone,
      stopResolver
    );

    // Check if trip was already added in real-time (update rather than create)
    Trip existingRealTimeTrip = transitService.getTrip(tripId);
    if (existingRealTimeTrip != null) {
      LOG.debug(
        "ADD_NEW_TRIP: Trip {} already exists as real-time added trip, will update",
        tripId
      );
      return resolveAddedTripUpdate(
        parsedUpdate,
        serviceDate,
        resolvedStopTimeUpdates,
        existingRealTimeTrip
      );
    }

    // New trip - no existing trip to resolve
    return new ResolvedTripCreation(parsedUpdate, serviceDate, resolvedStopTimeUpdates);
  }

  /**
   * Resolve the pattern and baseline trip times for an update to a previously added trip.
   */
  private ResolvedAddedTripUpdate resolveAddedTripUpdate(
    TripAddition parsedUpdate,
    LocalDate serviceDate,
    List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates,
    Trip trip
  ) {
    var tripId = trip.getId();

    // Find the existing pattern
    TripPattern pattern = transitService.findPattern(trip, serviceDate);
    if (pattern == null) {
      pattern = transitService.findPattern(trip);
    }
    if (pattern == null) {
      LOG.warn("UPDATE_ADDED_TRIP: Could not find pattern for existing trip {}", tripId);
      throw UpdateException.of(tripId, UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN);
    }

    // Get trip times - check scheduled timetable first, then real-time timetable
    TripTimes tripTimes = pattern.getScheduledTimetable().getTripTimes(trip);

    if (tripTimes == null) {
      // For GTFS-RT added trips, the scheduled timetable may be empty.
      // Fall back to the real-time timetable.
      tripTimes = transitService.findTimetable(pattern, serviceDate).getTripTimes(trip);
    }

    if (tripTimes == null) {
      LOG.warn("UPDATE_ADDED_TRIP: Could not find trip times for trip {}", tripId);
      throw UpdateException.of(tripId, UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN);
    }

    return new ResolvedAddedTripUpdate(
      parsedUpdate,
      serviceDate,
      resolvedStopTimeUpdates,
      trip,
      pattern,
      tripTimes
    );
  }
}
