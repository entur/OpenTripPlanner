package org.opentripplanner.updater.trip;

import static org.opentripplanner.updater.spi.UpdateErrorType.OUTSIDE_SERVICE_PERIOD;
import static org.opentripplanner.updater.spi.UpdateErrorType.TRIP_NOT_FOUND;
import static org.opentripplanner.updater.spi.UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN;

import java.util.Objects;
import org.opentripplanner.transit.model.timetable.ScheduledTripTimes;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.DuplicateTrip;
import org.opentripplanner.updater.trip.model.TripDuplication;

/**
 * Creates a {@link TripDuplication} from a {@link DuplicateTrip} command: looks up the
 * original scheduled trip, its pattern and scheduled times, and the service id/code for the
 * duplicated trip's service date.
 */
public class TripDuplicationFactory {

  private final TransitEditorService transitService;

  public TripDuplicationFactory(TransitEditorService transitService) {
    this.transitService = Objects.requireNonNull(transitService);
  }

  /**
   * Create the duplication a {@link DuplicateTrip} command asks for, resolved against the
   * transit model.
   *
   * @throws UpdateException if the original trip or its scheduled times cannot be found, or the
   *                         service date is outside the service period
   */
  public TripDuplication create(DuplicateTrip command) {
    var tripId = command.tripReference().tripId();

    var originalTrip = transitService.getTrip(tripId);
    if (originalTrip == null) {
      throw UpdateException.of(tripId, TRIP_NOT_FOUND);
    }

    var serviceId = transitService.getOrCreateServiceIdForDate(command.serviceDate());
    if (serviceId == null) {
      throw UpdateException.of(tripId, OUTSIDE_SERVICE_PERIOD);
    }
    int serviceCode = transitService.getTripCalendars().getServiceCode(serviceId);

    var originalPattern = transitService.findPattern(originalTrip);
    if (originalPattern == null) {
      throw UpdateException.of(tripId, TRIP_NOT_FOUND_IN_PATTERN);
    }

    var originalScheduledTimes = (ScheduledTripTimes) originalPattern
      .getScheduledTimetable()
      .getTripTimes(tripId);
    if (originalScheduledTimes == null) {
      throw UpdateException.of(tripId, TRIP_NOT_FOUND_IN_PATTERN);
    }

    return new TripDuplication(
      originalTrip,
      originalPattern,
      originalScheduledTimes,
      serviceId,
      serviceCode,
      command.serviceDate(),
      command.newStartTime()
    );
  }
}
