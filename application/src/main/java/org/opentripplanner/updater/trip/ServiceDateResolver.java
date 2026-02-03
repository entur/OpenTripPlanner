package org.opentripplanner.updater.trip;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves service dates from parsed trip updates.
 * <p>
 * When a ParsedTripUpdate has a null service date but contains a tripOnServiceDateId,
 * this resolver can look up the TripOnServiceDate entity and extract the service date.
 * <p>
 * For overnight trips (departing after midnight but registered on the previous service date),
 * this resolver can calculate the correct service date using the Trip's scheduled departure
 * time offset.
 */
public class ServiceDateResolver {

  private static final Logger LOG = LoggerFactory.getLogger(ServiceDateResolver.class);

  private final TripResolver tripResolver;
  private final TransitService transitService;

  public ServiceDateResolver(TripResolver tripResolver, TransitService transitService) {
    this.tripResolver = Objects.requireNonNull(tripResolver, "tripResolver must not be null");
    this.transitService = Objects.requireNonNull(transitService, "transitService must not be null");
  }

  /**
   * Resolve the service date for a trip update.
   * <p>
   * Resolution order:
   * <ol>
   *   <li>If serviceDate is present in parsedUpdate, return it</li>
   *   <li>If tripOnServiceDateId is present, look up TripOnServiceDate and extract serviceDate</li>
   *   <li>If aimedDepartureTime is present and Trip is resolvable, calculate serviceDate using
   *       the Trip's scheduled departure offset (handles overnight trips correctly)</li>
   *   <li>If aimedDepartureTime is present but Trip not resolvable, extract the date from
   *       the ZonedDateTime using its embedded timezone</li>
   *   <li>If none of the above, return failure with NO_START_DATE error</li>
   * </ol>
   *
   * @param parsedUpdate the parsed trip update
   * @return Result containing the resolved service date, or an UpdateError if not found
   */
  public Result<LocalDate, UpdateError> resolveServiceDate(ParsedTripUpdate parsedUpdate) {
    var serviceDate = parsedUpdate.serviceDate();
    var tripReference = parsedUpdate.tripReference();

    // If service date is already present, return it
    if (serviceDate != null) {
      return Result.success(serviceDate);
    }

    // Try to resolve from tripOnServiceDateId
    if (tripReference.hasTripOnServiceDateId()) {
      var result = tripResolver.resolveTripOnServiceDate(tripReference);
      if (result.isFailure()) {
        return Result.failure(result.failureValue());
      }
      return Result.success(result.successValue().getServiceDate());
    }

    // Try deferred resolution using aimedDepartureTime
    var aimedDepartureTime = parsedUpdate.aimedDepartureTime();
    if (aimedDepartureTime != null) {
      return resolveFromAimedDepartureTime(parsedUpdate, aimedDepartureTime);
    }

    // No service date available
    LOG.warn("No service date available for trip update");
    return Result.failure(
      new UpdateError(tripReference.tripId(), UpdateError.UpdateErrorType.NO_START_DATE)
    );
  }

  /**
   * Resolve service date from aimed departure time.
   * <p>
   * If the Trip can be resolved, calculate the day offset from the Trip's scheduled
   * departure time to handle overnight trips correctly. Otherwise, fall back to
   * simple date extraction.
   */
  private Result<LocalDate, UpdateError> resolveFromAimedDepartureTime(
    ParsedTripUpdate parsedUpdate,
    ZonedDateTime aimedDepartureTime
  ) {
    var tripReference = parsedUpdate.tripReference();

    // Try to resolve the Trip to calculate day offset for overnight trips
    Trip trip = tripResolver.resolveTripOrNull(tripReference);
    if (trip != null) {
      int daysOffset = calculateDayOffset(trip);
      LocalDate resolvedDate = aimedDepartureTime.toLocalDate().minusDays(daysOffset);
      LOG.debug(
        "Resolved service date {} for trip {} using day offset {} from aimed departure time {}",
        resolvedDate,
        trip.getId(),
        daysOffset,
        aimedDepartureTime
      );
      return Result.success(resolvedDate);
    }

    // Fallback: Trip not resolvable, extract date from ZonedDateTime using its embedded timezone
    // This matches EntityResolver behavior and may be incorrect for overnight trips,
    // but is the best we can do without Trip data
    LOG.debug(
      "Trip not resolvable for deferred service date resolution, falling back to simple date extraction from {}",
      aimedDepartureTime
    );
    return Result.success(aimedDepartureTime.toLocalDate());
  }

  /**
   * Calculate the difference in days between the service date and the departure at the first stop.
   * <p>
   * For trips departing after midnight (e.g., 02:00 on the calendar day following the service date),
   * the departure time in seconds will be >= 86400 (24 hours), resulting in a day offset of 1.
   * For trips departing before midnight on a previous day (rare), the departure time will be negative.
   *
   * @param trip the trip to calculate offset for
   * @return the number of days to subtract from the calendar date to get the service date
   */
  private int calculateDayOffset(Trip trip) {
    var pattern = transitService.findPattern(trip);
    if (pattern == null) {
      return 0;
    }
    var tripTimes = pattern.getScheduledTimetable().getTripTimes(trip);
    if (tripTimes == null) {
      return 0;
    }
    var departureTime = tripTimes.getDepartureTime(0);
    var days = (int) Duration.ofSeconds(departureTime).toDays();
    if (departureTime < 0) {
      return days - 1;
    } else {
      return days;
    }
  }
}
