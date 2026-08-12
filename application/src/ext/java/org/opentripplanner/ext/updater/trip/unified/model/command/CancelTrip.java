package org.opentripplanner.ext.updater.trip.unified.model.command;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * A command to cancel a trip. The trip stays visible but is marked as cancelled.
 * <p>
 * Maps to SIRI Cancellation=true or GTFS-RT CANCELED.
 */
public final class CancelTrip implements RemoveTripCommand {

  private final TripReference tripReference;

  @Nullable
  private final LocalDate serviceDate;

  @Nullable
  private final ZonedDateTime aimedDepartureTime;

  @Nullable
  private final String dataSource;

  private final VehicleDescription vehicleDescription;

  @Nullable
  private final JourneyEndpoints journeyEndpoints;

  /** A cancellation that says nothing about the vehicle that was to serve the trip. */
  public CancelTrip(
    TripReference tripReference,
    @Nullable LocalDate serviceDate,
    @Nullable ZonedDateTime aimedDepartureTime,
    @Nullable String dataSource
  ) {
    this(tripReference, serviceDate, aimedDepartureTime, dataSource, VehicleDescription.unknown());
  }

  public CancelTrip(
    TripReference tripReference,
    @Nullable LocalDate serviceDate,
    @Nullable ZonedDateTime aimedDepartureTime,
    @Nullable String dataSource,
    VehicleDescription vehicleDescription
  ) {
    this(tripReference, serviceDate, aimedDepartureTime, dataSource, vehicleDescription, null);
  }

  public CancelTrip(
    TripReference tripReference,
    @Nullable LocalDate serviceDate,
    @Nullable ZonedDateTime aimedDepartureTime,
    @Nullable String dataSource,
    VehicleDescription vehicleDescription,
    @Nullable JourneyEndpoints journeyEndpoints
  ) {
    this.tripReference = Objects.requireNonNull(tripReference);
    TripUpdateCommand.validateServiceDateAvailable(tripReference, serviceDate, aimedDepartureTime);
    this.serviceDate = serviceDate;
    this.aimedDepartureTime = aimedDepartureTime;
    this.dataSource = dataSource;
    this.vehicleDescription = Objects.requireNonNull(vehicleDescription);
    this.journeyEndpoints = journeyEndpoints;
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

  /**
   * A cancellation still states the vehicle that was to serve the journey - SIRI-ET carries the
   * {@code VehicleRef} on the cancellation message - and the cancelled trip keeps it.
   */
  @Override
  public VehicleDescription vehicleDescription() {
    return vehicleDescription;
  }

  /**
   * Where the cancelled journey starts and ends, when the message describes its calls. This is what
   * identifies the journey to the SIRI fuzzy trip matcher, for the producers whose journey ids name
   * no trip; a cancellation that lists no calls, and every GTFS-RT cancellation, carries nothing
   * here.
   */
  @Nullable
  public JourneyEndpoints journeyEndpoints() {
    return journeyEndpoints;
  }

  @Override
  public String toString() {
    return (
      "CancelTrip{" + "tripReference=" + tripReference + ", serviceDate=" + serviceDate + '}'
    );
  }
}
