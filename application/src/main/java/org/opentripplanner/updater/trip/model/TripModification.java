package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.updater.trip.policy.FormatPolicy;

/**
 * A modification of the stop pattern of an existing trip (rerouting it on one service date).
 * <p>
 * Maps to SIRI-ET extra calls or GTFS-RT REPLACEMENT.
 */
public final class TripModification implements ExistingTripUpdate {

  private final TripReference tripReference;

  @Nullable
  private final LocalDate serviceDate;

  @Nullable
  private final ZonedDateTime aimedDepartureTime;

  private final List<ParsedStopTimeUpdate> stopTimeUpdates;

  @Nullable
  private final TripCreationInfo tripCreationInfo;

  private final FormatPolicy formatPolicy;

  @Nullable
  private final String dataSource;

  private final boolean cancellation;

  private final boolean extraJourney;

  TripModification(
    TripReference tripReference,
    @Nullable LocalDate serviceDate,
    @Nullable ZonedDateTime aimedDepartureTime,
    List<ParsedStopTimeUpdate> stopTimeUpdates,
    @Nullable TripCreationInfo tripCreationInfo,
    FormatPolicy formatPolicy,
    @Nullable String dataSource,
    boolean cancellation,
    boolean extraJourney
  ) {
    this.tripReference = Objects.requireNonNull(tripReference);
    ParsedTripUpdate.validateServiceDateAvailable(tripReference, serviceDate, aimedDepartureTime);
    this.serviceDate = serviceDate;
    this.aimedDepartureTime = aimedDepartureTime;
    this.stopTimeUpdates = stopTimeUpdates != null ? List.copyOf(stopTimeUpdates) : List.of();
    this.tripCreationInfo = tripCreationInfo;
    this.formatPolicy = Objects.requireNonNull(formatPolicy);
    this.dataSource = dataSource;
    this.cancellation = cancellation;
    this.extraJourney = extraJourney;
  }

  public boolean isCancellation() {
    return cancellation;
  }

  /**
   * Whether the SIRI journey was flagged as an extra journey (ExtraJourney=true). When an extra
   * journey also carries extra calls it is classified as a MODIFY_TRIP; the modified trip is then
   * also marked as added, mirroring the legacy {@code ExtraCallTripBuilder}.
   */
  public boolean isExtraJourney() {
    return extraJourney;
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
  public String toString() {
    return (
      "TripModification{" +
      "tripReference=" +
      tripReference +
      ", serviceDate=" +
      serviceDate +
      ", cancellation=" +
      cancellation +
      ", extraJourney=" +
      extraJourney +
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

    @Nullable
    private TripCreationInfo tripCreationInfo;

    private FormatPolicy formatPolicy = FormatPolicy.siri();

    @Nullable
    private String dataSource;

    private boolean cancellation = false;

    private boolean extraJourney = false;

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

    public Builder withFormatPolicy(FormatPolicy formatPolicy) {
      this.formatPolicy = formatPolicy;
      return this;
    }

    public Builder withDataSource(String dataSource) {
      this.dataSource = dataSource;
      return this;
    }

    public Builder withCancellation(boolean cancellation) {
      this.cancellation = cancellation;
      return this;
    }

    public Builder withExtraJourney(boolean extraJourney) {
      this.extraJourney = extraJourney;
      return this;
    }

    public TripModification build() {
      return new TripModification(
        tripReference,
        serviceDate,
        aimedDepartureTime,
        stopTimeUpdates,
        tripCreationInfo,
        formatPolicy,
        dataSource,
        cancellation,
        extraJourney
      );
    }
  }
}
