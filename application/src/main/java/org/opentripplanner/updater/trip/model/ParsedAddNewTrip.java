package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Parsed update for adding a new trip that does not exist in the scheduled data.
 * <p>
 * Maps to SIRI REPLACEMENT_DEPARTURE or GTFS-RT NEW/ADDED.
 * <p>
 * Unlike other update types, {@link #tripCreationInfo()} is always non-null — the type
 * system enforces that ADD_NEW_TRIP always carries creation info.
 */
public final class ParsedAddNewTrip implements ParsedTripUpdate {

  private final TripReference tripReference;

  @Nullable
  private final LocalDate serviceDate;

  @Nullable
  private final ZonedDateTime aimedDepartureTime;

  private final List<ParsedStopTimeUpdate> stopTimeUpdates;
  private final TripCreationInfo tripCreationInfo;
  private final TripUpdateOptions options;

  @Nullable
  private final String dataSource;

  ParsedAddNewTrip(
    TripReference tripReference,
    @Nullable LocalDate serviceDate,
    @Nullable ZonedDateTime aimedDepartureTime,
    List<ParsedStopTimeUpdate> stopTimeUpdates,
    TripCreationInfo tripCreationInfo,
    TripUpdateOptions options,
    @Nullable String dataSource
  ) {
    this.tripReference = Objects.requireNonNull(tripReference);
    ParsedTripUpdate.validateServiceDateAvailable(tripReference, serviceDate, aimedDepartureTime);
    this.serviceDate = serviceDate;
    this.aimedDepartureTime = aimedDepartureTime;
    this.stopTimeUpdates = stopTimeUpdates != null ? List.copyOf(stopTimeUpdates) : List.of();
    this.tripCreationInfo = Objects.requireNonNull(
      tripCreationInfo,
      "tripCreationInfo is required for ADD_NEW_TRIP"
    );
    this.options = Objects.requireNonNull(options);
    this.dataSource = dataSource;
  }

  public static Builder builder(
    TripReference tripReference,
    @Nullable LocalDate serviceDate,
    TripCreationInfo tripCreationInfo
  ) {
    return new Builder(tripReference, serviceDate, tripCreationInfo);
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

  public List<ParsedStopTimeUpdate> stopTimeUpdates() {
    return stopTimeUpdates;
  }

  public TripCreationInfo tripCreationInfo() {
    return tripCreationInfo;
  }

  public TripUpdateOptions options() {
    return options;
  }

  @Override
  @Nullable
  public String dataSource() {
    return dataSource;
  }

  @Override
  public String toString() {
    return (
      "ParsedAddNewTrip{" + "tripReference=" + tripReference + ", serviceDate=" + serviceDate + '}'
    );
  }

  public static class Builder {

    private final TripReference tripReference;

    @Nullable
    private final LocalDate serviceDate;

    private final TripCreationInfo tripCreationInfo;

    @Nullable
    private ZonedDateTime aimedDepartureTime;

    private List<ParsedStopTimeUpdate> stopTimeUpdates = new ArrayList<>();
    private TripUpdateOptions options = TripUpdateOptions.siriDefaults();

    @Nullable
    private String dataSource;

    private Builder(
      TripReference tripReference,
      @Nullable LocalDate serviceDate,
      TripCreationInfo tripCreationInfo
    ) {
      this.tripReference = Objects.requireNonNull(tripReference);
      this.serviceDate = serviceDate;
      this.tripCreationInfo = Objects.requireNonNull(tripCreationInfo);
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

    public Builder withOptions(TripUpdateOptions options) {
      this.options = options;
      return this;
    }

    public Builder withDataSource(String dataSource) {
      this.dataSource = dataSource;
      return this;
    }

    public ParsedAddNewTrip build() {
      return new ParsedAddNewTrip(
        tripReference,
        serviceDate,
        aimedDepartureTime,
        stopTimeUpdates,
        tripCreationInfo,
        options,
        dataSource
      );
    }
  }
}
