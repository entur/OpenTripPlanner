package org.opentripplanner.ext.updater.trip.unified.factory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.model.change.AddedTripRevision;
import org.opentripplanner.ext.updater.trip.unified.model.change.ResolvedStopTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.change.TripAddition;
import org.opentripplanner.ext.updater.trip.unified.model.change.TripCreation;
import org.opentripplanner.ext.updater.trip.unified.model.command.AddTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.ParsedStopTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripCreationInfo;
import org.opentripplanner.ext.updater.trip.unified.resolver.ServiceDateResolver;
import org.opentripplanner.ext.updater.trip.unified.resolver.StopResolver;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a {@link TripAddition} from an {@link AddTrip} command by resolving it against the
 * transit model. Used for the ADD_NEW_TRIP update type.
 * <p>
 * The parsers are state-free, so whether an ADD_NEW_TRIP update creates a trip or updates a
 * previously added one can only be decided here, against the current transit model:
 * <ul>
 *   <li>Trip not yet in the transit model - returns {@link TripCreation}, carrying the
 *       route the trip runs on, the service id/code for its service date and the dated trips it
 *       replaces</li>
 *   <li>Trip already added in real-time - returns {@link AddedTripRevision}</li>
 * </ul>
 * A {@link TripCreation} validates itself on construction, so the factory also rejects a
 * message that cannot describe a trip at all.
 */
public class TripAdditionFactory {

  private static final Logger LOG = LoggerFactory.getLogger(TripAdditionFactory.class);

  private final TransitEditorService transitService;
  private final ServiceDateResolver serviceDateResolver;
  private final StopResolver stopResolver;
  private final RouteCreationStrategy routeCreationStrategy;
  private final ZoneId timeZone;

  public TripAdditionFactory(
    TransitEditorService transitService,
    ServiceDateResolver serviceDateResolver,
    StopResolver stopResolver,
    RouteCreationStrategy routeCreationStrategy,
    ZoneId timeZone
  ) {
    this.transitService = Objects.requireNonNull(transitService, "transitService must not be null");
    this.serviceDateResolver = Objects.requireNonNull(
      serviceDateResolver,
      "serviceDateResolver must not be null"
    );
    this.stopResolver = Objects.requireNonNull(stopResolver, "stopResolver must not be null");
    this.routeCreationStrategy = Objects.requireNonNull(
      routeCreationStrategy,
      "routeCreationStrategy must not be null"
    );
    this.timeZone = Objects.requireNonNull(timeZone, "timeZone must not be null");
  }

  /**
   * Create the addition an {@link AddTrip} command asks for, resolved against the transit model.
   *
   * @param command the command to resolve against the transit model
   * @return the trip addition, ready to apply
   * @throws UpdateException if resolution fails
   */
  public TripAddition create(AddTrip command) {
    // Resolve service date
    LocalDate serviceDate = serviceDateResolver.resolveServiceDate(command);

    var tripId = command.tripCreationInfo().tripId();

    // Check if trip already exists in scheduled data (error case)
    if (transitService.getScheduledTrip(tripId) != null) {
      LOG.debug("ADD_NEW_TRIP: Trip {} already exists in scheduled data", tripId);
      throw UpdateException.of(tripId, UpdateErrorType.TRIP_ALREADY_EXISTS);
    }

    // Resolve stop time updates now that service date is known
    var resolvedStopTimeUpdates = resolveStopTimeUpdates(command.stopTimeUpdates(), serviceDate);

    // Check if trip was already added in real-time (update rather than create)
    Trip existingRealTimeTrip = transitService.getTrip(tripId);
    if (existingRealTimeTrip != null) {
      LOG.debug(
        "ADD_NEW_TRIP: Trip {} already exists as real-time added trip, will update",
        tripId
      );
      return createAddedTripRevision(
        command,
        serviceDate,
        resolvedStopTimeUpdates,
        existingRealTimeTrip
      );
    }

    // New trip - resolve the service id and code the created trip will run under
    FeedScopedId serviceId = transitService.getOrCreateServiceIdForDate(serviceDate);
    if (serviceId == null) {
      LOG.debug("ADD_NEW_TRIP: Cannot get service ID for date {}", serviceDate);
      throw UpdateException.of(tripId, UpdateErrorType.OUTSIDE_SERVICE_PERIOD);
    }
    int serviceCode = transitService.getTripCalendars().getServiceCode(serviceId);

    // Resolve the route the trip runs on, creating one if the transit model has none for it
    var routeResolution = routeCreationStrategy.resolveOrCreateRoute(
      command.tripCreationInfo(),
      transitService
    );

    return new TripCreation(
      command,
      serviceDate,
      resolvedStopTimeUpdates,
      serviceId,
      serviceCode,
      routeResolution.route(),
      routeResolution.isNewRoute(),
      resolveReplacedTrips(command.tripCreationInfo())
    );
  }

  /**
   * Resolve the dated trips the created trip replaces. References to trips the transit model does
   * not know are dropped.
   */
  private List<TripOnServiceDate> resolveReplacedTrips(TripCreationInfo tripCreationInfo) {
    return tripCreationInfo
      .replacedTrips()
      .stream()
      .map(transitService::getTripOnServiceDate)
      .filter(Objects::nonNull)
      .toList();
  }

  /**
   * Resolve the pattern and baseline trip times for an update to a previously added trip.
   */
  private AddedTripRevision createAddedTripRevision(
    AddTrip command,
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

    return new AddedTripRevision(
      command,
      serviceDate,
      resolvedStopTimeUpdates,
      trip,
      pattern,
      tripTimes
    );
  }

  /**
   * Resolve the stop reference of each parsed stop time update and convert its time values, now
   * that the service date is known.
   */
  private List<ResolvedStopTimeUpdate> resolveStopTimeUpdates(
    List<ParsedStopTimeUpdate> updates,
    LocalDate serviceDate
  ) {
    return updates
      .stream()
      .map(u ->
        ResolvedStopTimeUpdate.of(u, serviceDate, timeZone, stopResolver.resolve(u.stopReference()))
      )
      .toList();
  }
}
