package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Parsed update for an existing trip without stop pattern changes.
 * <p>
 * Maps to SIRI trip update or GTFS-RT SCHEDULED.
 */
public final class ParsedUpdateExisting implements ParsedExistingTripUpdate {

  private final TripReference tripReference;

  @Nullable
  private final LocalDate serviceDate;

  @Nullable
  private final ZonedDateTime aimedDepartureTime;

  private final List<ParsedStopTimeUpdate> stopTimeUpdates;
  private final TripUpdateOptions options;

  @Nullable
  private final String dataSource;

  ParsedUpdateExisting(
    TripReference tripReference,
    @Nullable LocalDate serviceDate,
    @Nullable ZonedDateTime aimedDepartureTime,
    List<ParsedStopTimeUpdate> stopTimeUpdates,
    TripUpdateOptions options,
    @Nullable String dataSource
  ) {
    this.tripReference = Objects.requireNonNull(tripReference);
    ParsedTripUpdate.validateServiceDateAvailable(tripReference, serviceDate, aimedDepartureTime);
    this.serviceDate = serviceDate;
    this.aimedDepartureTime = aimedDepartureTime;
    this.stopTimeUpdates = stopTimeUpdates != null ? List.copyOf(stopTimeUpdates) : List.of();
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
      "ParsedUpdateExisting{" +
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

    public Builder withOptions(TripUpdateOptions options) {
      this.options = options;
      return this;
    }

    public Builder withDataSource(String dataSource) {
      this.dataSource = dataSource;
      return this;
    }

    public ParsedUpdateExisting build() {
      return new ParsedUpdateExisting(
        tripReference,
        serviceDate,
        aimedDepartureTime,
        stopTimeUpdates,
        options,
        dataSource
      );
    }
  }
}
