package org.opentripplanner.updater.trip;

import java.time.LocalDate;
import java.util.Objects;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves service dates from parsed trip updates.
 * <p>
 * When a ParsedTripUpdate has a null service date but contains a tripOnServiceDateId,
 * this resolver can look up the TripOnServiceDate entity and extract the service date.
 */
public class ServiceDateResolver {

  private static final Logger LOG = LoggerFactory.getLogger(ServiceDateResolver.class);

  private final TripResolver tripResolver;

  public ServiceDateResolver(TripResolver tripResolver) {
    this.tripResolver = Objects.requireNonNull(tripResolver, "tripResolver must not be null");
  }

  /**
   * Resolve the service date for a trip update.
   * <p>
   * Resolution order:
   * <ol>
   *   <li>If serviceDate is present in parsedUpdate, return it</li>
   *   <li>If tripOnServiceDateId is present, look up TripOnServiceDate and extract serviceDate</li>
   *   <li>If neither is available, return failure with NO_START_DATE error</li>
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

    // No service date available
    LOG.warn("No service date available for trip update");
    return Result.failure(
      new UpdateError(tripReference.tripId(), UpdateError.UpdateErrorType.NO_START_DATE)
    );
  }
}
