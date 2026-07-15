package org.opentripplanner.updater.trip;

import static org.opentripplanner.updater.spi.UpdateErrorType.OUTSIDE_SERVICE_PERIOD;
import static org.opentripplanner.updater.spi.UpdateErrorType.TRIP_NOT_FOUND;

import java.util.Objects;
import org.opentripplanner.transit.model.timetable.ScheduledTripTimes;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ParsedDuplicateTrip;
import org.opentripplanner.updater.trip.model.ResolvedDuplicateTrip;

/**
 * Resolves a {@link ParsedDuplicateTrip} into a {@link ResolvedDuplicateTrip}: looks up the
 * original scheduled trip, its pattern and scheduled times, and the service id/code for the
 * duplicated trip's service date.
 */
public class DuplicateTripResolver {

  private final TransitEditorService transitService;

  public DuplicateTripResolver(TransitEditorService transitService) {
    this.transitService = Objects.requireNonNull(transitService);
  }

  /**
   * Resolve a ParsedDuplicateTrip against the transit model.
   *
   * @throws UpdateException if the original trip cannot be found or the service date is outside
   *                         the service period
   */
  public ResolvedDuplicateTrip resolve(ParsedDuplicateTrip parsedUpdate) {
    var tripId = parsedUpdate.tripReference().tripId();

    var originalTrip = transitService.getTrip(tripId);
    if (originalTrip == null) {
      throw UpdateException.of(tripId, TRIP_NOT_FOUND);
    }

    var serviceId = transitService.getOrCreateServiceIdForDate(parsedUpdate.serviceDate());
    if (serviceId == null) {
      throw UpdateException.of(tripId, OUTSIDE_SERVICE_PERIOD);
    }
    int serviceCode = transitService.getTripCalendars().getServiceCode(serviceId);

    var originalPattern = transitService.findPattern(originalTrip);
    var originalScheduledTimes = (ScheduledTripTimes) originalPattern
      .getScheduledTimetable()
      .getTripTimes(tripId);

    return new ResolvedDuplicateTrip(
      originalTrip,
      originalPattern,
      originalScheduledTimes,
      serviceId,
      serviceCode,
      parsedUpdate.serviceDate(),
      parsedUpdate.newStartTime()
    );
  }
}
