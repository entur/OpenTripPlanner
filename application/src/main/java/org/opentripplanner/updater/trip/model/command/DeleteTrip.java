package org.opentripplanner.updater.trip.model.command;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * A command to delete a trip. Stronger than a cancellation — the trip is removed from routing.
 * <p>
 * Maps to GTFS-RT DELETED.
 */
public final class DeleteTrip implements RemoveTripCommand {

  private final TripReference tripReference;

  @Nullable
  private final LocalDate serviceDate;

  @Nullable
  private final ZonedDateTime aimedDepartureTime;

  @Nullable
  private final String dataSource;

  public DeleteTrip(
    TripReference tripReference,
    @Nullable LocalDate serviceDate,
    @Nullable ZonedDateTime aimedDepartureTime,
    @Nullable String dataSource
  ) {
    this.tripReference = Objects.requireNonNull(tripReference);
    TripUpdateCommand.validateServiceDateAvailable(tripReference, serviceDate, aimedDepartureTime);
    this.serviceDate = serviceDate;
    this.aimedDepartureTime = aimedDepartureTime;
    this.dataSource = dataSource;
  }

  @Override
  public TripReference tripReference() {
    return tripReference;
  }

  @Override
  @Nullable
  public LocalDate serviceDate() {
    return serviceDate;
  }

  @Override
  @Nullable
  public ZonedDateTime aimedDepartureTime() {
    return aimedDepartureTime;
  }

  @Override
  @Nullable
  public String dataSource() {
    return dataSource;
  }

  @Override
  public String toString() {
    return (
      "DeleteTrip{" + "tripReference=" + tripReference + ", serviceDate=" + serviceDate + '}'
    );
  }
}
