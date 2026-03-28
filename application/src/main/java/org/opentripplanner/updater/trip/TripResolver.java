package org.opentripplanner.updater.trip;

import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
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
   * @return the resolved Trip
   * @throws UpdateException if the trip cannot be found
   */
  public Trip resolveTrip(TripReference reference) {
    Objects.requireNonNull(reference, "reference must not be null");

    // Try direct trip ID lookup first
    if (reference.hasTripId()) {
      Trip trip = transitService.getTrip(reference.tripId());
      if (trip != null) {
        return trip;
      }
      // Trip ID was provided but not found
      throw UpdateException.of(reference.tripId(), UpdateErrorType.TRIP_NOT_FOUND);
    }

    // Try TripOnServiceDate ID lookup
    if (reference.hasTripOnServiceDateId()) {
      TripOnServiceDate tripOnServiceDate = transitService.getTripOnServiceDate(
        reference.tripOnServiceDateId()
      );
      if (tripOnServiceDate != null) {
        return tripOnServiceDate.getTrip();
      }
      // TripOnServiceDate ID was provided but not found
      throw UpdateException.of(reference.tripOnServiceDateId(), UpdateErrorType.TRIP_NOT_FOUND);
    }

    // No trip identifier provided
    throw UpdateException.noTripId(UpdateErrorType.TRIP_NOT_FOUND);
  }

  /**
   * Resolve a {@link Trip} from a {@link TripReference}, returning null if not found.
   * <p>
   * This is a convenience method for cases where the caller prefers null over an exception.
   *
   * @param reference the trip reference containing identification information
   * @return the resolved Trip, or null if not found
   */
  @Nullable
  public Trip resolveTripOrNull(TripReference reference) {
    try {
      return resolveTrip(reference);
    } catch (UpdateException e) {
      return null;
    }
  }

  /**
   * Resolve a {@link TripOnServiceDate} from a {@link TripReference}.
   * <p>
   * This is useful when the caller needs both the Trip and the service date,
   * which are both contained in TripOnServiceDate.
   *
   * @param reference the trip reference containing tripOnServiceDateId
   * @return the resolved TripOnServiceDate
   * @throws UpdateException if the TripOnServiceDate cannot be found
   */
  public TripOnServiceDate resolveTripOnServiceDate(TripReference reference) {
    Objects.requireNonNull(reference, "reference must not be null");

    if (reference.hasTripOnServiceDateId()) {
      TripOnServiceDate tripOnServiceDate = transitService.getTripOnServiceDate(
        reference.tripOnServiceDateId()
      );
      if (tripOnServiceDate != null) {
        return tripOnServiceDate;
      }
      throw UpdateException.of(reference.tripOnServiceDateId(), UpdateErrorType.TRIP_NOT_FOUND);
    }

    // No TripOnServiceDate ID provided
    throw UpdateException.noTripId(UpdateErrorType.TRIP_NOT_FOUND);
  }

  /**
   * Resolve a {@link TripOnServiceDate} from a {@link TripReference}, returning null if not found.
   *
   * @param reference the trip reference containing tripOnServiceDateId
   * @return the resolved TripOnServiceDate, or null if not found
   */
  @Nullable
  public TripOnServiceDate resolveTripOnServiceDateOrNull(TripReference reference) {
    try {
      return resolveTripOnServiceDate(reference);
    } catch (UpdateException e) {
      return null;
    }
  }
}
