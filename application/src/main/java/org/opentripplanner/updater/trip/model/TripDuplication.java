package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.ScheduledTripTimes;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;
import org.opentripplanner.updater.trip.TripUpdateResult;

/**
 * The duplication of an existing scheduled trip.
 * <p>
 * The duplication applies itself through {@link #apply}: the duplicated trip and its
 * real-time times are built from the resolved state.
 * {@link org.opentripplanner.updater.trip.TripDuplicator} drives it.
 */
public final class TripDuplication {

  /** The scheduled trip to duplicate. */
  private final Trip originalTrip;

  /** The pattern of the original trip, which the duplicated trip is added to. */
  private final TripPattern originalPattern;

  /** The scheduled times of the original trip, the template for the duplicated trip. */
  private final ScheduledTripTimes originalScheduledTimes;

  /** The service id valid for the duplicated trip's service date. */
  private final FeedScopedId serviceId;

  /** The service code corresponding to {@link #serviceId}. */
  private final int serviceCode;

  /** The service date the duplicated trip runs on. */
  private final LocalDate serviceDate;

  /** The departure time from the first stop of the duplicated trip. */
  private final LocalTime newStartTime;

  public TripDuplication(
    Trip originalTrip,
    TripPattern originalPattern,
    ScheduledTripTimes originalScheduledTimes,
    FeedScopedId serviceId,
    int serviceCode,
    LocalDate serviceDate,
    LocalTime newStartTime
  ) {
    this.originalTrip = Objects.requireNonNull(originalTrip, "originalTrip must not be null");
    this.originalPattern = Objects.requireNonNull(
      originalPattern,
      "originalPattern must not be null"
    );
    this.originalScheduledTimes = Objects.requireNonNull(
      originalScheduledTimes,
      "originalScheduledTimes must not be null"
    );
    this.serviceId = Objects.requireNonNull(serviceId, "serviceId must not be null");
    this.serviceCode = serviceCode;
    this.serviceDate = Objects.requireNonNull(serviceDate, "serviceDate must not be null");
    this.newStartTime = Objects.requireNonNull(newStartTime, "newStartTime must not be null");
  }

  /**
   * Create the duplicated trip and its real-time times from the resolved state, and return them
   * as an update that adds the new trip to the original pattern on the service date.
   *
   * @param deduplicator deduplicates the shifted scheduled times the duplicated trip is built from
   */
  public TripUpdateResult apply(DeduplicatorService deduplicator) {
    // Calculate how many seconds to shift all stop times
    int originalFirstDeparture = originalScheduledTimes.getScheduledDepartureTime(0);
    int newFirstDeparture = newStartTime.toSecondOfDay();
    int offsetSeconds = newFirstDeparture - originalFirstDeparture;

    // Build the new trip entity (copy of original with a new ID)
    var newTripId = duplicatedTripId();
    var newTrip = Trip.of(newTripId)
      .withRoute(originalTrip.getRoute())
      .withServiceId(serviceId)
      .build();

    // Shift all scheduled times and rebind to the new trip
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

    var realTimeTripUpdate = RealTimeTripUpdate.of(originalPattern, newTripTimes, serviceDate)
      .withTripCreation(true)
      .withAddedTripOnServiceDate(tripOnServiceDate)
      .build();
    return new TripUpdateResult(realTimeTripUpdate);
  }

  /// The spec is silent about how these ids should be constructed, so we create a new ID
  /// ourselves.
  /// It is therefore not possible to send a spec-compliant vehicle position update for this
  /// trip. If this is a requirement, then we need to update the spec.
  private FeedScopedId duplicatedTripId() {
    var originalTripId = originalTrip.getId();
    var localDateTime = serviceDate.atTime(newStartTime);
    return new FeedScopedId(
      originalTripId.getFeedId(),
      originalTripId.getId() + ":duplicated:" + localDateTime
    );
  }

  @Override
  public String toString() {
    return (
      "TripDuplication{" +
      "originalTrip=" +
      originalTrip.getId() +
      ", serviceDate=" +
      serviceDate +
      ", newStartTime=" +
      newStartTime +
      '}'
    );
  }
}
