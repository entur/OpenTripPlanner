package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Parsed update that modifies the stop pattern of an existing trip.
 * <p>
 * Maps to SIRI-ET extra calls or GTFS-RT REPLACEMENT.
 */
public final class ParsedModifyTrip implements ParsedExistingTripUpdate {

  private final TripReference tripReference;

  @Nullable
  private final LocalDate serviceDate;

  @Nullable
  private final ZonedDateTime aimedDepartureTime;

  private final List<ParsedStopTimeUpdate> stopTimeUpdates;

  @Nullable
  private final TripCreationInfo tripCreationInfo;

  @Nullable
  private final StopPatternModification stopPatternModification;

  private final TripUpdateOptions options;

  @Nullable
  private final String dataSource;

  ParsedModifyTrip(
    TripReference tripReference,
    @Nullable LocalDate serviceDate,
    @Nullable ZonedDateTime aimedDepartureTime,
    List<ParsedStopTimeUpdate> stopTimeUpdates,
    @Nullable TripCreationInfo tripCreationInfo,
    @Nullable StopPatternModification stopPatternModification,
    TripUpdateOptions options,
    @Nullable String dataSource
  ) {
    this.tripReference = Objects.requireNonNull(tripReference);
    ParsedTripUpdate.validateServiceDateAvailable(tripReference, serviceDate, aimedDepartureTime);
    this.serviceDate = serviceDate;
    this.aimedDepartureTime = aimedDepartureTime;
    this.stopTimeUpdates = stopTimeUpdates != null ? List.copyOf(stopTimeUpdates) : List.of();
    this.tripCreationInfo = tripCreationInfo;
    this.stopPatternModification = stopPatternModification;
    this.options = Objects.requireNonNull(options);
    this.dataSource = dataSource;
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
  @Nullable
  public TripCreationInfo tripCreationInfo() {
    return tripCreationInfo;
  }

  @Nullable
  public StopPatternModification stopPatternModification() {
    return stopPatternModification;
  }

  public boolean hasStopPatternModification() {
    return stopPatternModification != null && stopPatternModification.hasModifications();
  }

  @Override
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
      "ParsedModifyTrip{" + "tripReference=" + tripReference + ", serviceDate=" + serviceDate + '}'
    );
  }

  public static class Builder {

    private final TripReference tripReference;

    @Nullable
    private final LocalDate serviceDate;

    @Nullable
    private ZonedDateTime aimedDepartureTime;

    private List<ParsedStopTimeUpdate> stopTimeUpdates = new ArrayList<>();

    @Nullable
    private TripCreationInfo tripCreationInfo;

    @Nullable
    private StopPatternModification stopPatternModification;

    private TripUpdateOptions options = TripUpdateOptions.siriDefaults();

    @Nullable
    private String dataSource;

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

    public Builder withTripCreationInfo(TripCreationInfo tripCreationInfo) {
      this.tripCreationInfo = tripCreationInfo;
      return this;
    }

    public Builder withStopPatternModification(StopPatternModification stopPatternModification) {
      this.stopPatternModification = stopPatternModification;
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

    public ParsedModifyTrip build() {
      return new ParsedModifyTrip(
        tripReference,
        serviceDate,
        aimedDepartureTime,
        stopTimeUpdates,
        tripCreationInfo,
        stopPatternModification,
        options,
        dataSource
      );
    }
  }
}
