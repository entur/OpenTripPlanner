package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Parsed update to cancel a trip. The trip is still visible but marked as cancelled.
 * <p>
 * Maps to SIRI Cancellation=true or GTFS-RT CANCELED.
 */
public final class ParsedCancelTrip implements ParsedTripRemoval {

  private final TripReference tripReference;

  @Nullable
  private final LocalDate serviceDate;

  @Nullable
  private final ZonedDateTime aimedDepartureTime;

  @Nullable
  private final String dataSource;

  public ParsedCancelTrip(
    TripReference tripReference,
    @Nullable LocalDate serviceDate,
    @Nullable ZonedDateTime aimedDepartureTime,
    @Nullable String dataSource
  ) {
    this.tripReference = Objects.requireNonNull(tripReference);
    ParsedTripUpdate.validateServiceDateAvailable(tripReference, serviceDate, aimedDepartureTime);
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
      "ParsedCancelTrip{" + "tripReference=" + tripReference + ", serviceDate=" + serviceDate + '}'
    );
  }
}
