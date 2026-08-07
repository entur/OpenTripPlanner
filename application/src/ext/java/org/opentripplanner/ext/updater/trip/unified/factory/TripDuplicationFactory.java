package org.opentripplanner.ext.updater.trip.unified.factory;

import static org.opentripplanner.updater.spi.UpdateErrorType.OUTSIDE_SERVICE_PERIOD;
import static org.opentripplanner.updater.spi.UpdateErrorType.TRIP_NOT_FOUND;
import static org.opentripplanner.updater.spi.UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN;

import java.util.Objects;
import org.opentripplanner.ext.updater.trip.unified.model.change.TripDuplication;
import org.opentripplanner.ext.updater.trip.unified.model.command.DuplicateTrip;
import org.opentripplanner.ext.updater.trip.unified.resolver.FuzzyTripMatcher;
import org.opentripplanner.ext.updater.trip.unified.resolver.TripAndPattern;
import org.opentripplanner.transit.model.timetable.ScheduledTripTimes;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateException;

/**
 * Creates a {@link TripDuplication} from a {@link DuplicateTrip} command: looks up the
 * original scheduled trip - by the trip id the message names or, failing that, by fuzzy trip
 * matching - its pattern and scheduled times, and the service id/code for the duplicated trip's
 * service date.
 */
public class TripDuplicationFactory {

  private final TransitEditorService transitService;
  private final FuzzyTripMatcher fuzzyTripMatcher;

  public TripDuplicationFactory(
    TransitEditorService transitService,
    FuzzyTripMatcher fuzzyTripMatcher
  ) {
    this.transitService = Objects.requireNonNull(transitService);
    this.fuzzyTripMatcher = Objects.requireNonNull(fuzzyTripMatcher);
  }

  /**
   * Create the duplication a {@link DuplicateTrip} command asks for, resolved against the
   * transit model.
   *
   * @throws UpdateException if the original trip or its scheduled times cannot be found, or the
   *                         service date is outside the service period
   */
  public TripDuplication create(DuplicateTrip command) {
    var originalTrip = resolveOriginalTrip(command);
    var tripId = originalTrip.getId();

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

  /**
   * The trip to duplicate, by the trip id the message names or by fuzzy matching when that names
   * nothing. The duplicate is minted from the matched trip's id, since the message named no other.
   * A matcher with no verdict leaves the lookup this factory was doing as the answer: the original
   * trip was not found.
   */
  private Trip resolveOriginalTrip(DuplicateTrip command) {
    var tripReference = command.tripReference();
    var tripId = tripReference.tripId();

    if (tripId != null) {
      var trip = transitService.getTrip(tripId);
      if (trip != null) {
        return trip;
      }
    }
    return fuzzyTripMatcher
      .match(tripReference, command, command.serviceDate())
      .map(TripAndPattern::trip)
      .orElseThrow(() -> UpdateException.of(tripId, TRIP_NOT_FOUND));
  }
}
