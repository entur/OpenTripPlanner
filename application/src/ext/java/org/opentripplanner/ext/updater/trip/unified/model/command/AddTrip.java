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
 * A command to add a new trip that does not exist in the scheduled data.
 * <p>
 * Maps to SIRI REPLACEMENT_DEPARTURE or GTFS-RT NEW/ADDED.
 * <p>
 * Unlike other update types, {@link #tripCreationInfo()} is always non-null — the type
 * system enforces that ADD_NEW_TRIP always carries creation info.
 */
public final class AddTrip implements TripUpdateCommand {

  private final TripReference tripReference;

  @Nullable
  private final LocalDate serviceDate;

  @Nullable
  private final ZonedDateTime aimedDepartureTime;

  private final List<ParsedStopTimeUpdate> stopTimeUpdates;
  private final TripCreationInfo tripCreationInfo;
  private final FormatPolicy formatPolicy;

  @Nullable
  private final String dataSource;

  private final VehicleDescription vehicleDescription;

  @Nullable
  private final I18NString tripHeadsign;

  private final boolean cancellation;

  AddTrip(
    TripReference tripReference,
    @Nullable LocalDate serviceDate,
    @Nullable ZonedDateTime aimedDepartureTime,
    List<ParsedStopTimeUpdate> stopTimeUpdates,
    TripCreationInfo tripCreationInfo,
    FormatPolicy formatPolicy,
    @Nullable String dataSource,
    VehicleDescription vehicleDescription,
    @Nullable I18NString tripHeadsign,
    boolean cancellation
  ) {
    this.tripReference = Objects.requireNonNull(tripReference);
    this.serviceDate = serviceDate;
    this.aimedDepartureTime = aimedDepartureTime;
    this.stopTimeUpdates = stopTimeUpdates != null ? List.copyOf(stopTimeUpdates) : List.of();
    this.tripCreationInfo = Objects.requireNonNull(
      tripCreationInfo,
      "tripCreationInfo is required for ADD_NEW_TRIP"
    );
    this.formatPolicy = Objects.requireNonNull(formatPolicy);
    this.dataSource = dataSource;
    this.vehicleDescription = Objects.requireNonNull(vehicleDescription);
    this.tripHeadsign = tripHeadsign;
    this.cancellation = cancellation;
    validate();
  }

  /**
   * An added trip cannot borrow its service date from an existing dated trip - a dated service
   * journey reference on an addition names the dated trip being created, not one to look up. The
   * message has to state its day outright or imply it through an aimed departure time, a stricter
   * rule than {@link TripUpdateCommand#validateServiceDateAvailable} applies to the other commands.
   *
   * @throws UpdateException if the message says nothing about its service date
   */
  private void validate() {
    if (serviceDate == null && aimedDepartureTime == null) {
      throw UpdateException.of(tripCreationInfo.tripId(), UpdateErrorType.NO_START_DATE);
    }
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

  /** Whether this added (extra) journey is cancelled, i.e. added in cancelled state. */
  public boolean cancellation() {
    return cancellation;
  }

  /**
   * Whether this message, arriving for a trip an earlier message already added, revises that trip on
   * the pattern it was added to - rather than rebuilding both from the calls it carries.
   */
  public boolean revisesAnAlreadyAddedTrip() {
    return formatPolicy.repeatedAddition().revisesInPlace();
  }

  @Override
  public String toString() {
    return ("AddTrip{" + "tripReference=" + tripReference + ", serviceDate=" + serviceDate + '}');
  }

  public static class Builder {

    private final TripReference tripReference;

    @Nullable
    private final LocalDate serviceDate;

    private final TripCreationInfo tripCreationInfo;

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

    public AddTrip build() {
      return new AddTrip(
        tripReference,
        serviceDate,
        aimedDepartureTime,
        stopTimeUpdates,
        tripCreationInfo,
        formatPolicy,
        dataSource,
        vehicleDescription,
        tripHeadsign,
        cancellation
      );
    }
  }
}
