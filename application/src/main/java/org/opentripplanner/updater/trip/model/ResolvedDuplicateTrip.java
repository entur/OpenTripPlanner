package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.ScheduledTripTimes;
import org.opentripplanner.transit.model.timetable.Trip;

/**
 * Resolved data for duplicating an existing scheduled trip.
 * <p>
 * Used by {@link org.opentripplanner.updater.trip.handlers.DuplicateTripHandler}.
 *
 * @param originalTrip the scheduled trip to duplicate
 * @param originalPattern the pattern of the original trip
 * @param originalScheduledTimes the scheduled times of the original trip, used as the template
 *                               for the duplicated trip
 * @param serviceId the service id valid for the duplicated trip's service date
 * @param serviceCode the service code corresponding to {@code serviceId}
 * @param serviceDate the service date the duplicated trip runs on
 * @param newStartTime the departure time from the first stop of the duplicated trip
 */
public record ResolvedDuplicateTrip(
  Trip originalTrip,
  TripPattern originalPattern,
  ScheduledTripTimes originalScheduledTimes,
  FeedScopedId serviceId,
  int serviceCode,
  LocalDate serviceDate,
  LocalTime newStartTime
) {
  public ResolvedDuplicateTrip {
    Objects.requireNonNull(originalTrip);
    Objects.requireNonNull(originalPattern);
    Objects.requireNonNull(originalScheduledTimes);
    Objects.requireNonNull(serviceId);
    Objects.requireNonNull(serviceDate);
    Objects.requireNonNull(newStartTime);
  }
}
