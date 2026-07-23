package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.updater.trip.policy.FormatPolicy;

/**
 * An update of an existing trip without stop pattern changes: delays, changed times and
 * per-stop details.
 * <p>
 * Maps to SIRI trip update or GTFS-RT SCHEDULED.
 */
public final class ScheduledTripUpdate implements ExistingTripUpdate {

  private final TripReference tripReference;

  @Nullable
  private final LocalDate serviceDate;

  @Nullable
  private final ZonedDateTime aimedDepartureTime;

  private final List<ParsedStopTimeUpdate> stopTimeUpdates;
  private final FormatPolicy formatPolicy;

  @Nullable
  private final String dataSource;

  @Nullable
  private final String vehicleId;

  ScheduledTripUpdate(
    TripReference tripReference,
    @Nullable LocalDate serviceDate,
    @Nullable ZonedDateTime aimedDepartureTime,
    List<ParsedStopTimeUpdate> stopTimeUpdates,
    FormatPolicy formatPolicy,
    @Nullable String dataSource,
    @Nullable String vehicleId
  ) {
    this.tripReference = Objects.requireNonNull(tripReference);
    ParsedTripUpdate.validateServiceDateAvailable(tripReference, serviceDate, aimedDepartureTime);
    this.serviceDate = serviceDate;
    this.aimedDepartureTime = aimedDepartureTime;
    this.stopTimeUpdates = stopTimeUpdates != null ? List.copyOf(stopTimeUpdates) : List.of();
    this.formatPolicy = Objects.requireNonNull(formatPolicy);
    this.dataSource = dataSource;
    this.vehicleId = vehicleId;
  }

  public static Builder builder(TripReference tripReference, @Nullable LocalDate serviceDate) {
    return new Builder(tripReference, serviceDate);
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
  public List<ParsedStopTimeUpdate> stopTimeUpdates() {
    return stopTimeUpdates;
  }

  @Override
  public FormatPolicy formatPolicy() {
    return formatPolicy;
  }

  @Override
  @Nullable
  public String dataSource() {
    return dataSource;
  }

  @Override
  @Nullable
  public String vehicleId() {
    return vehicleId;
  }

  @Override
  public String toString() {
    return (
      "ScheduledTripUpdate{" +
      "tripReference=" +
      tripReference +
      ", serviceDate=" +
      serviceDate +
      '}'
    );
  }

  public static class Builder {

    private final TripReference tripReference;

    @Nullable
    private final LocalDate serviceDate;

    @Nullable
    private ZonedDateTime aimedDepartureTime;

    private List<ParsedStopTimeUpdate> stopTimeUpdates = new ArrayList<>();
    private FormatPolicy formatPolicy = FormatPolicy.siri();

    @Nullable
    private String dataSource;

    @Nullable
    private String vehicleId;

    private Builder(TripReference tripReference, @Nullable LocalDate serviceDate) {
      this.tripReference = Objects.requireNonNull(tripReference);
      this.serviceDate = serviceDate;
    }

    public Builder withAimedDepartureTime(ZonedDateTime aimedDepartureTime) {
      this.aimedDepartureTime = aimedDepartureTime;
      return this;
    }

    public Builder withStopTimeUpdates(List<ParsedStopTimeUpdate> stopTimeUpdates) {
      this.stopTimeUpdates = new ArrayList<>(stopTimeUpdates);
      return this;
    }

    public Builder addStopTimeUpdate(ParsedStopTimeUpdate stopTimeUpdate) {
      this.stopTimeUpdates.add(stopTimeUpdate);
      return this;
    }

    public Builder withFormatPolicy(FormatPolicy formatPolicy) {
      this.formatPolicy = formatPolicy;
      return this;
    }

    public Builder withDataSource(String dataSource) {
      this.dataSource = dataSource;
      return this;
    }

    public Builder withVehicleId(@Nullable String vehicleId) {
      this.vehicleId = vehicleId;
      return this;
    }

    public ScheduledTripUpdate build() {
      return new ScheduledTripUpdate(
        tripReference,
        serviceDate,
        aimedDepartureTime,
        stopTimeUpdates,
        formatPolicy,
        dataSource,
        vehicleId
      );
    }
  }
}
