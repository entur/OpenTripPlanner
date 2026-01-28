package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Format-independent representation of a trip update parsed from either SIRI-ET or GTFS-RT.
 * This is the common model that both parsers produce and that the common applier consumes.
 */
public final class ParsedTripUpdate {

  private final TripUpdateType updateType;
  private final TripReference tripReference;
  private final LocalDate serviceDate;
  private final List<ParsedStopTimeUpdate> stopTimeUpdates;

  @Nullable
  private final TripCreationInfo tripCreationInfo;

  @Nullable
  private final StopPatternModification stopPatternModification;

  private final TripUpdateOptions options;

  @Nullable
  private final String dataSource;

  /**
   * @param updateType The type of update (modify existing, cancel, add new, etc.)
   * @param tripReference Reference to the trip being updated
   * @param serviceDate The service date for which this update applies
   * @param stopTimeUpdates Updates for individual stops in the trip
   * @param tripCreationInfo Information for creating a new trip (only for ADD_NEW_TRIP)
   * @param stopPatternModification Modifications to the stop pattern (skipped/added stops)
   * @param options Processing options (delay propagation, etc.)
   * @param dataSource The source of the real-time update (feed name, producer, etc.)
   */
  public ParsedTripUpdate(
    TripUpdateType updateType,
    TripReference tripReference,
    LocalDate serviceDate,
    List<ParsedStopTimeUpdate> stopTimeUpdates,
    @Nullable TripCreationInfo tripCreationInfo,
    @Nullable StopPatternModification stopPatternModification,
    TripUpdateOptions options,
    @Nullable String dataSource
  ) {
    this.updateType = Objects.requireNonNull(updateType, "updateType must not be null");
    this.tripReference = Objects.requireNonNull(tripReference, "tripReference must not be null");
    this.serviceDate = Objects.requireNonNull(serviceDate, "serviceDate must not be null");
    this.stopTimeUpdates = stopTimeUpdates != null ? List.copyOf(stopTimeUpdates) : List.of();
    this.tripCreationInfo = tripCreationInfo;
    this.stopPatternModification = stopPatternModification;
    this.options = Objects.requireNonNull(options, "options must not be null");
    this.dataSource = dataSource;
  }

  /**
   * Create a builder for a parsed trip update.
   */
  public static Builder builder(
    TripUpdateType updateType,
    TripReference tripReference,
    LocalDate serviceDate
  ) {
    return new Builder(updateType, tripReference, serviceDate);
  }

  public TripUpdateType updateType() {
    return updateType;
  }

  public TripReference tripReference() {
    return tripReference;
  }

  public LocalDate serviceDate() {
    return serviceDate;
  }

  public List<ParsedStopTimeUpdate> stopTimeUpdates() {
    return stopTimeUpdates;
  }

  @Nullable
  public TripCreationInfo tripCreationInfo() {
    return tripCreationInfo;
  }

  @Nullable
  public StopPatternModification stopPatternModification() {
    return stopPatternModification;
  }

  public TripUpdateOptions options() {
    return options;
  }

  @Nullable
  public String dataSource() {
    return dataSource;
  }

  /**
   * Returns true if this update cancels or deletes the trip.
   */
  public boolean isCancellation() {
    return updateType.removesTrip();
  }

  /**
   * Returns true if this update creates a new trip.
   */
  public boolean isNewTrip() {
    return updateType.createsNewTrip();
  }

  /**
   * Returns true if this update has stop pattern modifications.
   */
  public boolean hasStopPatternModification() {
    return stopPatternModification != null && stopPatternModification.hasModifications();
  }

  /**
   * Returns true if any stop time update has an explicit stop sequence number.
   * GTFS-RT updates typically have stop sequences, while SIRI updates do not.
   * This is used to determine whether to enforce strict stop count validation.
   */
  public boolean hasStopSequences() {
    return stopTimeUpdates.stream().anyMatch(u -> u.stopSequence() != null);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ParsedTripUpdate that = (ParsedTripUpdate) o;
    return (
      updateType == that.updateType &&
      Objects.equals(tripReference, that.tripReference) &&
      Objects.equals(serviceDate, that.serviceDate) &&
      Objects.equals(stopTimeUpdates, that.stopTimeUpdates) &&
      Objects.equals(tripCreationInfo, that.tripCreationInfo) &&
      Objects.equals(stopPatternModification, that.stopPatternModification) &&
      Objects.equals(options, that.options) &&
      Objects.equals(dataSource, that.dataSource)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      updateType,
      tripReference,
      serviceDate,
      stopTimeUpdates,
      tripCreationInfo,
      stopPatternModification,
      options,
      dataSource
    );
  }

  @Override
  public String toString() {
    return (
      "ParsedTripUpdate{" +
      "updateType=" +
      updateType +
      ", tripReference=" +
      tripReference +
      ", serviceDate=" +
      serviceDate +
      ", stopTimeUpdates=" +
      stopTimeUpdates +
      ", tripCreationInfo=" +
      tripCreationInfo +
      ", stopPatternModification=" +
      stopPatternModification +
      ", options=" +
      options +
      ", dataSource='" +
      dataSource +
      '\'' +
      '}'
    );
  }

  /**
   * Builder for ParsedTripUpdate.
   */
  public static class Builder {

    private final TripUpdateType updateType;
    private final TripReference tripReference;
    private final LocalDate serviceDate;
    private List<ParsedStopTimeUpdate> stopTimeUpdates = new ArrayList<>();
    private TripCreationInfo tripCreationInfo;
    private StopPatternModification stopPatternModification;
    private TripUpdateOptions options = TripUpdateOptions.siriDefaults();
    private String dataSource;

    private Builder(TripUpdateType updateType, TripReference tripReference, LocalDate serviceDate) {
      this.updateType = Objects.requireNonNull(updateType);
      this.tripReference = Objects.requireNonNull(tripReference);
      this.serviceDate = Objects.requireNonNull(serviceDate);
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

    public ParsedTripUpdate build() {
      return new ParsedTripUpdate(
        updateType,
        tripReference,
        serviceDate,
        stopTimeUpdates,
        tripCreationInfo,
        stopPatternModification,
        options,
        dataSource
      );
    }
  }
}
