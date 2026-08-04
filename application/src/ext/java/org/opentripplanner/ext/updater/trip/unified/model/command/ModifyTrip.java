package org.opentripplanner.ext.updater.trip.unified.model.command;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.ext.updater.trip.unified.policy.FormatPolicy;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;

/**
 * A command to modify the stop pattern of an existing trip, rerouting it on one service date.
 * <p>
 * Maps to SIRI-ET extra calls or GTFS-RT REPLACEMENT.
 */
public final class ModifyTrip implements ExistingTripCommand {

  private final TripReference tripReference;

  @Nullable
  private final LocalDate serviceDate;

  @Nullable
  private final ZonedDateTime aimedDepartureTime;

  private final List<ParsedStopTimeUpdate> stopTimeUpdates;

  private final FormatPolicy formatPolicy;

  @Nullable
  private final String dataSource;

  private final VehicleDescription vehicleDescription;

  @Nullable
  private final I18NString tripHeadsign;

  private final boolean cancellation;

  private final boolean extraJourney;

  ModifyTrip(
    TripReference tripReference,
    @Nullable LocalDate serviceDate,
    @Nullable ZonedDateTime aimedDepartureTime,
    List<ParsedStopTimeUpdate> stopTimeUpdates,
    FormatPolicy formatPolicy,
    @Nullable String dataSource,
    VehicleDescription vehicleDescription,
    @Nullable I18NString tripHeadsign,
    boolean cancellation,
    boolean extraJourney
  ) {
    this.tripReference = Objects.requireNonNull(tripReference);
    TripUpdateCommand.validateServiceDateAvailable(tripReference, serviceDate, aimedDepartureTime);
    this.serviceDate = serviceDate;
    this.aimedDepartureTime = aimedDepartureTime;
    this.stopTimeUpdates = stopTimeUpdates != null ? List.copyOf(stopTimeUpdates) : List.of();
    this.formatPolicy = Objects.requireNonNull(formatPolicy);
    this.dataSource = dataSource;
    this.vehicleDescription = Objects.requireNonNull(vehicleDescription);
    this.tripHeadsign = tripHeadsign;
    this.cancellation = cancellation;
    this.extraJourney = extraJourney;
    validate();
  }

  /**
   * A modification reroutes the trip onto a new stop pattern, and a pattern needs at least two
   * calls. The calls are known as soon as the message is parsed, so a message that cannot
   * describe a pattern is rejected here rather than after resolving the trip.
   *
   * @throws UpdateException if the message calls fewer than two times
   */
  private void validate() {
    if (stopTimeUpdates.size() < 2) {
      throw UpdateException.of(tripReference.tripId(), UpdateErrorType.TOO_FEW_STOPS);
    }
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
  public FormatPolicy formatPolicy() {
    return formatPolicy;
  }

  @Override
  @Nullable
  public String dataSource() {
    return dataSource;
  }

  @Override
  public VehicleDescription vehicleDescription() {
    return vehicleDescription;
  }

  @Override
  @Nullable
  public I18NString tripHeadsign() {
    return tripHeadsign;
  }

  @Override
  public String toString() {
    return (
      "ModifyTrip{" +
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

    private FormatPolicy formatPolicy = FormatPolicy.siri();

    @Nullable
    private String dataSource;

    private VehicleDescription vehicleDescription = VehicleDescription.unknown();

    @Nullable
    private I18NString tripHeadsign;

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

    public Builder withFormatPolicy(FormatPolicy formatPolicy) {
      this.formatPolicy = formatPolicy;
      return this;
    }

    public Builder withDataSource(String dataSource) {
      this.dataSource = dataSource;
      return this;
    }

    public Builder withVehicleDescription(VehicleDescription vehicleDescription) {
      this.vehicleDescription = Objects.requireNonNull(vehicleDescription);
      return this;
    }

    public Builder withTripHeadsign(@Nullable I18NString tripHeadsign) {
      this.tripHeadsign = tripHeadsign;
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

    public ModifyTrip build() {
      return new ModifyTrip(
        tripReference,
        serviceDate,
        aimedDepartureTime,
        stopTimeUpdates,
        formatPolicy,
        dataSource,
        vehicleDescription,
        tripHeadsign,
        cancellation,
        extraJourney
      );
    }
  }
}
