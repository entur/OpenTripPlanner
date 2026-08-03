package org.opentripplanner.ext.updater.trip.unified.factory;

import java.time.LocalDate;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.model.change.AddedTripRemoval;
import org.opentripplanner.ext.updater.trip.unified.model.change.ScheduledTripRemoval;
import org.opentripplanner.ext.updater.trip.unified.model.change.TripRemoval;
import org.opentripplanner.ext.updater.trip.unified.model.command.RemoveTripCommand;
import org.opentripplanner.ext.updater.trip.unified.model.command.VehicleDescription;
import org.opentripplanner.ext.updater.trip.unified.resolver.ServiceDateResolver;
import org.opentripplanner.ext.updater.trip.unified.resolver.TripResolver;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;

/**
 * Creates a {@link TripRemoval} from a {@link RemoveTripCommand} by resolving it against the
 * transit model.
 * <p>
 * Used for CANCEL_TRIP ({@link org.opentripplanner.ext.updater.trip.unified.model.command.CancelTrip})
 * and DELETE_TRIP ({@link org.opentripplanner.ext.updater.trip.unified.model.command.DeleteTrip}).
 * <p>
 * The factory looks up scheduled trips first, then checks for previously added (real-time)
 * trips via the transit service, which sees all in-progress real-time updates in the
 * timetable snapshot buffer of the current update task. Which of the two it finds decides whether
 * the removal is a {@link ScheduledTripRemoval} or a {@link AddedTripRemoval}.
 */
public class TripRemovalFactory {

  private final TransitEditorService transitService;
  private final TripResolver tripResolver;
  private final ServiceDateResolver serviceDateResolver;

  public TripRemovalFactory(
    TransitEditorService transitService,
    TripResolver tripResolver,
    ServiceDateResolver serviceDateResolver
  ) {
    this.transitService = Objects.requireNonNull(transitService, "transitService must not be null");
    this.tripResolver = Objects.requireNonNull(tripResolver, "tripResolver must not be null");
    this.serviceDateResolver = Objects.requireNonNull(
      serviceDateResolver,
      "serviceDateResolver must not be null"
    );
  }

  /**
   * Create the removal a {@link RemoveTripCommand} asks for, resolved against the transit model.
   *
   * @param command the command to resolve against the transit model
   * @return the trip removal, ready to apply
   * @throws UpdateException if trip cannot be found at all
   */
  public TripRemoval create(RemoveTripCommand command) {
    // Resolve service date
    LocalDate serviceDate = serviceDateResolver.resolveServiceDate(command);

    var tripReference = command.tripReference();
    FeedScopedId tripId = tripReference.tripId();
    String dataSource = command.dataSource();
    var vehicleDescription = command.vehicleDescription();

    // Try to resolve as scheduled trip from static transit model
    Trip trip;
    try {
      trip = tripResolver.resolveTrip(tripReference);
    } catch (UpdateException e) {
      // Trip not found in scheduled data - check for previously added trips
      return resolveAddedTripOrNotFound(serviceDate, tripId, dataSource, vehicleDescription);
    }

    // Find pattern for the trip
    TripPattern pattern = transitService.findPattern(trip);
    if (pattern == null) {
      return resolveAddedTripOrNotFound(serviceDate, trip.getId(), dataSource, vehicleDescription);
    }

    // If the resolved pattern is itself a real-time added pattern (i.e., this trip was added via
    // real-time, not in the static schedule), look up the RT timetable times and treat the trip
    // as a previously-added trip so that TripRemover preserves the "added" flag.
    if (pattern.isRealTimeTripPattern() && !pattern.isStopPatternModifiedInRealTime()) {
      var rtTimetable = transitService.findTimetable(pattern, serviceDate);
      var rtTripTimes = rtTimetable.getTripTimes(trip.getId());
      if (rtTripTimes != null && rtTripTimes.isAdded()) {
        return new AddedTripRemoval(
          serviceDate,
          trip.getId(),
          pattern,
          rtTripTimes,
          dataSource,
          vehicleDescription
        );
      }
    }

    // Get trip times
    TripTimes tripTimes = pattern.getScheduledTimetable().getTripTimes(trip);
    if (tripTimes == null) {
      return resolveAddedTripOrNotFound(serviceDate, trip.getId(), dataSource, vehicleDescription);
    }

    // Note: extra call cancellations (SIRI Cancellation=true with extra call stops) are NOT
    // routed here — they go through TripModifier instead (see SiriTripUpdateParser).
    return new ScheduledTripRemoval(
      serviceDate,
      trip,
      pattern,
      tripTimes,
      dataSource,
      vehicleDescription
    );
  }

  /**
   * Check for a previously added (real-time) trip in the timetable snapshot.
   * Returns the removal if found, or throws UpdateException otherwise.
   */
  private TripRemoval resolveAddedTripOrNotFound(
    LocalDate serviceDate,
    @Nullable FeedScopedId tripId,
    @Nullable String dataSource,
    VehicleDescription vehicleDescription
  ) {
    if (tripId != null) {
      var pattern = transitService.findNewTripPatternForModifiedTrip(tripId, serviceDate);
      if (pattern != null) {
        var timetable = transitService.findTimetable(pattern, serviceDate);
        var tripTimes = timetable.getTripTimes(tripId);
        if (tripTimes != null && tripTimes.isAdded()) {
          return new AddedTripRemoval(
            serviceDate,
            tripId,
            pattern,
            tripTimes,
            dataSource,
            vehicleDescription
          );
        }
      }
    }
    throw UpdateException.of(tripId, UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND);
  }
}
