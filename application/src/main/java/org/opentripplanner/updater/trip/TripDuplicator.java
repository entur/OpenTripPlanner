package org.opentripplanner.updater.trip;

import java.util.Objects;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ResolvedDuplicateTrip;
import org.opentripplanner.updater.trip.model.TripDuplication;

/**
 * Duplicates an existing scheduled trip at a new start time. Creates a copy of the
 * original trip with all stop times shifted to the new start time, added to the original
 * pattern on the requested service date.
 * <p>
 * Maps to GTFS-RT DUPLICATED. SIRI-ET has no equivalent concept.
 */
public class TripDuplicator {

  private final DuplicateTripResolver resolver;
  private final DeduplicatorService deduplicator;

  public TripDuplicator(DuplicateTripResolver resolver, DeduplicatorService deduplicator) {
    this.resolver = Objects.requireNonNull(resolver);
    this.deduplicator = Objects.requireNonNull(deduplicator);
  }

  public TripUpdateResult duplicate(TripDuplication parsedUpdate) throws UpdateException {
    return duplicate(resolver.resolve(parsedUpdate));
  }

  public TripUpdateResult duplicate(ResolvedDuplicateTrip resolvedUpdate) {
    var originalTrip = resolvedUpdate.originalTrip();
    var originalScheduledTimes = resolvedUpdate.originalScheduledTimes();
    var serviceDate = resolvedUpdate.serviceDate();

    // Calculate how many seconds to shift all stop times
    int originalFirstDeparture = originalScheduledTimes.getScheduledDepartureTime(0);
    int newFirstDeparture = resolvedUpdate.newStartTime().toSecondOfDay();
    int offsetSeconds = newFirstDeparture - originalFirstDeparture;

    // Build the new trip entity (copy of original with a new ID)
    var newTripId = duplicatedTripId(resolvedUpdate);
    var newTrip = Trip.of(newTripId)
      .withRoute(originalTrip.getRoute())
      .withServiceId(resolvedUpdate.serviceId())
      .build();

    // Shift all scheduled times and rebind to the new trip
    int serviceCode = resolvedUpdate.serviceCode();
    var newScheduledTimes = originalScheduledTimes
      .copyOf(deduplicator)
      .withTrip(newTrip)
      .withServiceCode(serviceCode)
      .plusTimeShift(offsetSeconds)
      .build();

    // Produce real-time trip times marked as an added trip
    var newTripTimes = newScheduledTimes
      .createRealTimeFromScheduledTimes()
      .withServiceCode(serviceCode)
      .withAdded()
      .withRealTimeUpdated()
      .build();

    var tripOnServiceDate = TripOnServiceDate.of(newTripId)
      .withTrip(newTrip)
      .withServiceDate(serviceDate)
      .build();

    var realTimeTripUpdate = RealTimeTripUpdate.of(
      resolvedUpdate.originalPattern(),
      newTripTimes,
      serviceDate
    )
      .withTripCreation(true)
      .withAddedTripOnServiceDate(tripOnServiceDate)
      .build();
    return new TripUpdateResult(realTimeTripUpdate);
  }

  /// The spec is silent about how these ids should be constructed, so we create a new ID
  /// ourselves.
  /// It is therefore not possible to send a spec-compliant vehicle position update for this
  /// trip. If this is a requirement, then we need to update the spec.
  private static FeedScopedId duplicatedTripId(ResolvedDuplicateTrip resolvedUpdate) {
    var originalTripId = resolvedUpdate.originalTrip().getId();
    var localDateTime = resolvedUpdate.serviceDate().atTime(resolvedUpdate.newStartTime());
    return new FeedScopedId(
      originalTripId.getFeedId(),
      originalTripId.getId() + ":duplicated:" + localDateTime
    );
  }
}
