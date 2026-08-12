package org.opentripplanner.ext.updater.trip.unified.resolver;

import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripReference;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;

/**
 * Resolves a {@link Trip} from a {@link TripReference}.
 * <p>
 * A message may name its journey in more than one way, and the ways are ranked: the trip id the
 * feed states outright, then the journey of a day, and last the id an earlier real-time message
 * added the journey under. A name the transit model does not know is no name at all, so it leaves
 * the next one to speak; only a reference none of whose names is known is unresolvable.
 */
public class TripResolver {

  private final TransitService transitService;

  public TripResolver(TransitService transitService) {
    this.transitService = Objects.requireNonNull(transitService, "transitService must not be null");
  }

  /**
   * Resolve a {@link Trip} from a {@link TripReference}, trying every name the reference carries
   * in turn.
   *
   * @param reference the trip reference containing identification information
   * @return the resolved Trip
   * @throws UpdateException if none of the names the reference carries is known
   */
  public Trip resolveTrip(TripReference reference) {
    Objects.requireNonNull(reference, "reference must not be null");

    var statedTrip = findTrip(reference.statedTripId());
    if (statedTrip != null) {
      return statedTrip;
    }

    var statedDatedTrip = findTripOnServiceDate(reference.statedTripOnServiceDateId());
    if (statedDatedTrip != null) {
      return statedDatedTrip.getTrip();
    }

    var previouslyAddedTrip = findTrip(reference.previouslyAddedTripId());
    if (previouslyAddedTrip != null) {
      return previouslyAddedTrip;
    }

    throw unresolvable(reference);
  }

  @Nullable
  private Trip findTrip(@Nullable FeedScopedId tripId) {
    return tripId != null ? transitService.getTrip(tripId) : null;
  }

  @Nullable
  private TripOnServiceDate findTripOnServiceDate(@Nullable FeedScopedId tripOnServiceDateId) {
    return tripOnServiceDateId != null
      ? transitService.getTripOnServiceDate(tripOnServiceDateId)
      : null;
  }

  /**
   * The rejection of a reference nothing in the transit model answers to, identified by the trip
   * id it names or, when it names none, by the journey of a day it names.
   */
  private UpdateException unresolvable(TripReference reference) {
    var id = reference.hasTripId() ? reference.tripId() : reference.tripOnServiceDateId();
    return id != null
      ? UpdateException.of(id, UpdateErrorType.TRIP_NOT_FOUND)
      : UpdateException.noTripId(UpdateErrorType.TRIP_NOT_FOUND);
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
   * Resolve the {@link TripOnServiceDate} a reference names - the dated trip the feed states, or
   * the one an earlier real-time message added under the journey code when the feed states none.
   * <p>
   * This is useful when the caller needs both the Trip and the service date, which are both
   * contained in TripOnServiceDate. Unlike {@link #resolveTrip}, a stated dated trip the transit
   * model does not know is the last word: the feed named a dated journey, and no other name it
   * carries speaks of a day.
   *
   * @param reference the trip reference containing tripOnServiceDateId
   * @return the resolved TripOnServiceDate
   * @throws UpdateException if the TripOnServiceDate cannot be found
   */
  public TripOnServiceDate resolveTripOnServiceDate(TripReference reference) {
    Objects.requireNonNull(reference, "reference must not be null");

    if (reference.hasTripOnServiceDateId()) {
      var tripOnServiceDate = findTripOnServiceDate(reference.tripOnServiceDateId());
      if (tripOnServiceDate != null) {
        return tripOnServiceDate;
      }
      throw UpdateException.of(reference.tripOnServiceDateId(), UpdateErrorType.TRIP_NOT_FOUND);
    }

    // No TripOnServiceDate ID provided
    throw UpdateException.noTripId(UpdateErrorType.TRIP_NOT_FOUND);
  }
}
