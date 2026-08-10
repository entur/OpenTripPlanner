package org.opentripplanner.ext.updater.trip.unified.factory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.model.change.AddedTripRevision;
import org.opentripplanner.ext.updater.trip.unified.model.change.ResolvedStopReference;
import org.opentripplanner.ext.updater.trip.unified.model.change.ResolvedStopTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.change.TripAddition;
import org.opentripplanner.ext.updater.trip.unified.model.change.TripCreation;
import org.opentripplanner.ext.updater.trip.unified.model.command.AddTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.ParsedStopTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripCreationInfo;
import org.opentripplanner.ext.updater.trip.unified.resolver.ServiceDateResolver;
import org.opentripplanner.ext.updater.trip.unified.resolver.StopResolver;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.organization.Operator;
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
 *   <li>Trip already added in real-time - returns {@link AddedTripRevision}, but only for a format
 *       that revises an added trip in place. A format that rebuilds it takes the
 *       {@link TripCreation} branch again, see
 *       {@link org.opentripplanner.ext.updater.trip.unified.policy.RepeatedAdditionPolicy}</li>
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

    // The trip may already have been added by an earlier message. Whether that makes this message a
    // revision of it or a rebuild of it is the format's answer to give.
    Trip existingRealTimeTrip = transitService.getTrip(tripId);
    if (existingRealTimeTrip != null && command.revisesAnAlreadyAddedTrip()) {
      LOG.debug(
        "ADD_NEW_TRIP: Trip {} already exists as real-time added trip, will revise it",
        tripId
      );
      return createAddedTripRevision(
        command,
        serviceDate,
        resolvedStopTimeUpdates,
        existingRealTimeTrip
      );
    }

    // Built from scratch - resolve the service id and code the trip will run under
    FeedScopedId serviceId = transitService.getOrCreateServiceIdForDate(serviceDate);
    if (serviceId == null) {
      LOG.debug("ADD_NEW_TRIP: Cannot get service ID for date {}", serviceDate);
      throw UpdateException.of(tripId, UpdateErrorType.OUTSIDE_SERVICE_PERIOD);
    }
    int serviceCode = transitService.getTripCalendars().getServiceCode(serviceId);

    // Resolve the operator the trip is operated by, before the route: the created trip and a route
    // created for it are stamped with the same operator, so it is resolved in one place.
    var operator = resolveOperator(command.tripCreationInfo());

    // Resolve the route the trip runs on, creating one if the transit model has none for it
    var routeResolution = routeCreationStrategy.resolveOrCreateRoute(
      command.tripCreationInfo(),
      operator,
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
      operator,
      routeResolution.netexSubmode(),
      resolveReplacedTrips(command.tripCreationInfo())
    );
  }

  /**
   * The operator the message says operates the created trip. An operator the transit model does not
   * know is dropped rather than rejected - the trip then falls back to the operator of the route it
   * runs on.
   */
  @Nullable
  private Operator resolveOperator(TripCreationInfo tripCreationInfo) {
    var operatorId = tripCreationInfo.operatorId();
    return operatorId != null ? transitService.getOperator(operatorId) : null;
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
   * Resolve the pattern and baseline trip times for an update to a previously added trip: the
   * pattern the trip was <em>added</em> to and the aimed times it was added with. The pattern is
   * deliberately not looked up by service date - that lookup prefers a pattern a later message
   * modified, and a revision measured against that pattern could never revert the modification.
   * Legacy resolves the same way, in {@code SiriRealTimeUpdateHandler.handleModifiedTrip}.
   */
  private AddedTripRevision createAddedTripRevision(
    AddTrip command,
    LocalDate serviceDate,
    List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates,
    Trip trip
  ) {
    var tripId = trip.getId();

    TripPattern pattern = transitService.findPattern(trip);
    if (pattern == null) {
      LOG.warn("UPDATE_ADDED_TRIP: Could not find pattern for existing trip {}", tripId);
      throw UpdateException.of(tripId, UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN);
    }

    // The aimed times of the addition. Only a format that revises an added trip in place gets
    // here, and such a format holds the aimed times in the scheduled timetable of the added
    // pattern - see ScheduledDataPolicy.
    TripTimes tripTimes = pattern.getScheduledTimetable().getTripTimes(trip);
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
   * <p>
   * A created trip is the only source of its own stop pattern, so it is built from the stops the
   * calls report. A stop assignment has nothing to modify here and is ignored, which is also what
   * the legacy updaters do.
   */
  private List<ResolvedStopTimeUpdate> resolveStopTimeUpdates(
    List<ParsedStopTimeUpdate> updates,
    LocalDate serviceDate
  ) {
    return updates
      .stream()
      .map(u ->
        ResolvedStopTimeUpdate.of(
          u,
          serviceDate,
          timeZone,
          ResolvedStopReference.ofReferencedStop(
            stopResolver.resolveReferencedStop(u.stopReference())
          )
        )
      )
      .toList();
  }
}
