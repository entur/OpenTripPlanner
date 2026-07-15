package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * The duplication of an existing scheduled trip: a copy of the trip running at a
 * new start time (and possibly on a different service date).
 * <p>
 * Maps to GTFS-RT DUPLICATED. SIRI-ET has no equivalent concept.
 */
public final class TripDuplication implements ParsedTripUpdate {

  private final TripReference tripReference;
  private final LocalDate serviceDate;
  private final LocalTime newStartTime;

  /**
   * @param tripReference reference to the original (scheduled) trip to duplicate
   * @param serviceDate the service date the duplicated trip runs on
   * @param newStartTime the departure time from the first stop of the duplicated trip
   */
  public TripDuplication(
    TripReference tripReference,
    LocalDate serviceDate,
    LocalTime newStartTime
  ) {
    this.tripReference = Objects.requireNonNull(tripReference);
    this.serviceDate = Objects.requireNonNull(serviceDate);
    this.newStartTime = Objects.requireNonNull(newStartTime);
  }

  @Override
  public TripReference tripReference() {
    return tripReference;
  }

  @Override
  public LocalDate serviceDate() {
    return serviceDate;
  }

  @Override
  @Nullable
  public ZonedDateTime aimedDepartureTime() {
    return null;
  }

  @Override
  @Nullable
  public String dataSource() {
    return null;
  }

  public LocalTime newStartTime() {
    return newStartTime;
  }

  @Override
  public String toString() {
    return (
      "TripDuplication{" +
      "tripReference=" +
      tripReference +
      ", serviceDate=" +
      serviceDate +
      ", newStartTime=" +
      newStartTime +
      '}'
    );
  }
}
