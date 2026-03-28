package org.opentripplanner.updater.trip;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ParsedAddNewTrip;
import org.opentripplanner.updater.trip.model.ResolvedNewTrip;
import org.opentripplanner.updater.trip.model.ResolvedStopTimeUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a {@link ParsedAddNewTrip} into a {@link ResolvedNewTrip} for adding new trips
 * or updating previously added trips.
 * <p>
 * Used for ADD_NEW_TRIP update type.
 * <p>
 * Resolution handles two cases:
 * <ul>
 *   <li>New trip creation - returns ResolvedNewTrip with no existing trip</li>
 *   <li>Update to existing added trip - returns ResolvedNewTrip with existing trip data</li>
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
  public ResolvedNewTrip resolve(ParsedAddNewTrip parsedUpdate) {
    // Resolve service date
    LocalDate serviceDate = serviceDateResolver.resolveServiceDate(parsedUpdate);

    var tripId = parsedUpdate.tripCreationInfo().tripId();

    // Check if trip already exists in scheduled data (error case)
    if (transitService.getScheduledTrip(tripId) != null) {
      LOG.debug("ADD_NEW_TRIP: Trip {} already exists in scheduled data", tripId);
      throw UpdateException.of(tripId, UpdateErrorType.TRIP_ALREADY_EXISTS);
    }

    // Check if trip was already added in real-time (update rather than create)
    Trip existingRealTimeTrip = transitService.getTrip(tripId);
    if (existingRealTimeTrip != null) {
      LOG.debug(
        "ADD_NEW_TRIP: Trip {} already exists as real-time added trip, will update",
        tripId
      );

      // Find the existing pattern
      TripPattern existingPattern = transitService.findPattern(existingRealTimeTrip, serviceDate);
      if (existingPattern == null) {
        existingPattern = transitService.findPattern(existingRealTimeTrip);
      }
      if (existingPattern == null) {
        LOG.warn("UPDATE_ADDED_TRIP: Could not find pattern for existing trip {}", tripId);
        throw UpdateException.of(tripId, UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN);
      }

      // Get trip times - check scheduled timetable first, then real-time timetable
      TripTimes scheduledTripTimes = existingPattern
        .getScheduledTimetable()
        .getTripTimes(existingRealTimeTrip);

      if (scheduledTripTimes == null) {
        // For GTFS-RT added trips, the scheduled timetable may be empty.
        // Fall back to the real-time timetable.
        scheduledTripTimes = transitService
          .findTimetable(existingPattern, serviceDate)
          .getTripTimes(existingRealTimeTrip);
      }

      if (scheduledTripTimes == null) {
        LOG.warn("UPDATE_ADDED_TRIP: Could not find trip times for trip {}", tripId);
        throw UpdateException.of(tripId, UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN);
      }

      // Resolve stop time updates now that service date is known
      var resolvedStopTimeUpdates = ResolvedStopTimeUpdate.resolveAll(
        parsedUpdate.stopTimeUpdates(),
        serviceDate,
        timeZone,
        stopResolver
      );

      return ResolvedNewTrip.forExistingAddedTrip(
        parsedUpdate,
        serviceDate,
        resolvedStopTimeUpdates,
        existingRealTimeTrip,
        existingPattern,
        scheduledTripTimes
      );
    }

    // Resolve stop time updates now that service date is known
    var resolvedStopTimeUpdates = ResolvedStopTimeUpdate.resolveAll(
      parsedUpdate.stopTimeUpdates(),
      serviceDate,
      timeZone,
      stopResolver
    );

    // New trip - no existing trip to resolve
    return ResolvedNewTrip.forNewTrip(parsedUpdate, serviceDate, resolvedStopTimeUpdates);
  }
}
