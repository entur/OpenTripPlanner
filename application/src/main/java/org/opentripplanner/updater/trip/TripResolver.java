package org.opentripplanner.updater.trip;

import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.TripReference;

/**
 * Resolves a {@link Trip} from a {@link TripReference}.
 * <p>
 * This class handles the multi-stage trip resolution logic:
 * <ol>
 *   <li>Try direct trip ID lookup via {@code tripId}</li>
 *   <li>Try TripOnServiceDate ID lookup via {@code tripOnServiceDateId}, then get the Trip</li>
 * </ol>
 * <p>
 * This follows the pattern established in {@code EntityResolver.resolveTrip()} but operates
 * on the parsed {@link TripReference} rather than raw SIRI message objects.
 */
public class TripResolver {

  private final TransitService transitService;

  public TripResolver(TransitService transitService) {
    this.transitService = Objects.requireNonNull(transitService, "transitService must not be null");
  }

  /**
   * Resolve a {@link Trip} from a {@link TripReference}.
   * <p>
   * Resolution order:
   * <ol>
   *   <li>If {@code tripId} is present, look up the Trip directly</li>
   *   <li>If {@code tripOnServiceDateId} is present, look up the TripOnServiceDate and extract the Trip</li>
   * </ol>
   *
   * @param reference the trip reference containing identification information
   * @return Result containing the resolved Trip on success, or an UpdateError if not found
   */
  public Result<Trip, UpdateError> resolveTrip(TripReference reference) {
    Objects.requireNonNull(reference, "reference must not be null");

    // Try direct trip ID lookup first
    if (reference.hasTripId()) {
      Trip trip = transitService.getTrip(reference.tripId());
      if (trip != null) {
        return Result.success(trip);
      }
      // Trip ID was provided but not found
      return Result.failure(
        new UpdateError(reference.tripId(), UpdateError.UpdateErrorType.TRIP_NOT_FOUND)
      );
    }

    // Try TripOnServiceDate ID lookup
    if (reference.hasTripOnServiceDateId()) {
      TripOnServiceDate tripOnServiceDate = transitService.getTripOnServiceDate(
        reference.tripOnServiceDateId()
      );
      if (tripOnServiceDate != null) {
        return Result.success(tripOnServiceDate.getTrip());
      }
      // TripOnServiceDate ID was provided but not found
      return Result.failure(
        new UpdateError(reference.tripOnServiceDateId(), UpdateError.UpdateErrorType.TRIP_NOT_FOUND)
      );
    }

    // No trip identifier provided
    return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.TRIP_NOT_FOUND));
  }

  /**
   * Resolve a {@link Trip} from a {@link TripReference}, returning null if not found.
   * <p>
   * This is a convenience method for cases where the caller prefers null over Result.
   *
   * @param reference the trip reference containing identification information
   * @return the resolved Trip, or null if not found
   */
  @Nullable
  public Trip resolveTripOrNull(TripReference reference) {
    var result = resolveTrip(reference);
    return result.isSuccess() ? result.successValue() : null;
  }
}
