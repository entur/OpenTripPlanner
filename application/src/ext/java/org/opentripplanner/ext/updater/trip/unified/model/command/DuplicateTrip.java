package org.opentripplanner.ext.updater.trip.unified.model.command;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.ext.updater.trip.unified.model.ServiceTime;

/**
 * A command to duplicate an existing scheduled trip: a copy of the trip running at a
 * new start time (and possibly on a different service date).
 * <p>
 * Maps to GTFS-RT DUPLICATED. SIRI-ET has no equivalent concept.
 */
public final class DuplicateTrip implements TripUpdateCommand {

  private final TripReference tripReference;
  private final LocalDate serviceDate;
  private final ServiceTime newStartTime;

  /**
   * @param tripReference reference to the original (scheduled) trip to duplicate
   * @param serviceDate the service date the duplicated trip runs on
   * @param newStartTime the departure time from the first stop of the duplicated trip
   */
  public DuplicateTrip(
    TripReference tripReference,
    LocalDate serviceDate,
    ServiceTime newStartTime
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

  public ServiceTime newStartTime() {
    return newStartTime;
  }

  @Override
  public String toString() {
    return (
      "DuplicateTrip{" +
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
