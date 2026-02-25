package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model.timetable.OccupancyStatus;

/**
 * A parsed stop time update from a real-time feed (SIRI-ET or GTFS-RT).
 * Contains all the information needed to update the arrival/departure times
 * and other attributes for a single stop visit.
 */
public final class ParsedStopTimeUpdate {

  private final StopReference stopReference;

  @Nullable
  private final Integer stopSequence;

  private final StopUpdateStatus status;

  @Nullable
  private final ParsedTimeUpdate arrivalUpdate;

  @Nullable
  private final ParsedTimeUpdate departureUpdate;

  @Nullable
  private final PickDrop pickup;

  @Nullable
  private final PickDrop dropoff;

  @Nullable
  private final I18NString stopHeadsign;

  @Nullable
  private final OccupancyStatus occupancy;

  private final boolean isExtraCall;
  private final boolean predictionInaccurate;
  private final boolean hasArrived;
  private final boolean hasDeparted;

  /**
   * @param stopReference Reference to the stop (by ID or stop point ref)
   * @param stopSequence The stop sequence number (0-indexed position in the trip)
   * @param status The status of this stop (scheduled, skipped, cancelled, etc.)
   * @param arrivalUpdate The arrival time update
   * @param departureUpdate The departure time update
   * @param pickup The pickup type at this stop
   * @param dropoff The dropoff type at this stop
   * @param stopHeadsign The headsign to display at this stop
   * @param occupancy The occupancy status of the vehicle at this stop
   * @param isExtraCall True if this is an extra call (not in the scheduled pattern)
   * @param predictionInaccurate True if the prediction is marked as inaccurate
   * @param hasArrived True if the vehicle has arrived at this stop
   * @param hasDeparted True if the vehicle has departed from this stop
   */
  public ParsedStopTimeUpdate(
    StopReference stopReference,
    @Nullable Integer stopSequence,
    StopUpdateStatus status,
    @Nullable ParsedTimeUpdate arrivalUpdate,
    @Nullable ParsedTimeUpdate departureUpdate,
    @Nullable PickDrop pickup,
    @Nullable PickDrop dropoff,
    @Nullable I18NString stopHeadsign,
    @Nullable OccupancyStatus occupancy,
    boolean isExtraCall,
    boolean predictionInaccurate,
    boolean hasArrived,
    boolean hasDeparted
  ) {
    this.stopReference = Objects.requireNonNull(stopReference, "stopReference must not be null");
    this.stopSequence = stopSequence;
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.arrivalUpdate = arrivalUpdate;
    this.departureUpdate = departureUpdate;
    this.pickup = pickup;
    this.dropoff = dropoff;
    this.stopHeadsign = stopHeadsign;
    this.occupancy = occupancy;
    this.isExtraCall = isExtraCall;
    this.predictionInaccurate = predictionInaccurate;
    this.hasArrived = hasArrived;
    this.hasDeparted = hasDeparted;
  }

  /**
   * The status of a stop in a trip update.
   */
  public enum StopUpdateStatus {
    /**
     * Normal scheduled stop.
     */
    SCHEDULED,

    /**
     * Stop is skipped (vehicle passes through without stopping).
     */
    SKIPPED,

    /**
     * Stop is cancelled (no service at this stop).
     */
    CANCELLED,

    /**
     * No prediction data available for this stop.
     */
    NO_DATA,

    /**
     * Extra call - this stop is not in the scheduled pattern.
     */
    ADDED,
  }

  /**
   * Create a builder for a stop time update.
   */
  public static Builder builder(StopReference stopReference) {
    return new Builder(stopReference);
  }

  public StopReference stopReference() {
    return stopReference;
  }

  @Nullable
  public Integer stopSequence() {
    return stopSequence;
  }

  public StopUpdateStatus status() {
    return status;
  }

  @Nullable
  public ParsedTimeUpdate arrivalUpdate() {
    return arrivalUpdate;
  }

  @Nullable
  public ParsedTimeUpdate departureUpdate() {
    return departureUpdate;
  }

  @Nullable
  public PickDrop pickup() {
    return pickup;
  }

  @Nullable
  public PickDrop dropoff() {
    return dropoff;
  }

  @Nullable
  public I18NString stopHeadsign() {
    return stopHeadsign;
  }

  @Nullable
  public OccupancyStatus occupancy() {
    return occupancy;
  }

  public boolean isExtraCall() {
    return isExtraCall;
  }

  public boolean predictionInaccurate() {
    return predictionInaccurate;
  }

  public boolean hasArrived() {
    return hasArrived;
  }

  public boolean hasDeparted() {
    return hasDeparted;
  }

  /**
   * Returns true if this update has arrival time information.
   */
  public boolean hasArrivalUpdate() {
    return arrivalUpdate != null;
  }

  /**
   * Returns true if this update has departure time information.
   */
  public boolean hasDepartureUpdate() {
    return departureUpdate != null;
  }

  /**
   * Returns true if this stop is skipped or cancelled (no service).
   */
  public boolean isSkipped() {
    return status == StopUpdateStatus.SKIPPED || status == StopUpdateStatus.CANCELLED;
  }

  /**
   * Resolve the scheduled arrival time in seconds since midnight, or null if no arrival update.
   */
  @Nullable
  public Integer resolveScheduledArrivalSeconds(LocalDate serviceDate, ZoneId timeZone) {
    if (!hasArrivalUpdate()) {
      return null;
    }
    TimeUpdate resolved = arrivalUpdate.resolve(serviceDate, timeZone);
    return resolved.scheduledTimeSecondsSinceMidnight();
  }

  /**
   * Resolve the scheduled departure time in seconds since midnight, or null if no departure update.
   */
  @Nullable
  public Integer resolveScheduledDepartureSeconds(LocalDate serviceDate, ZoneId timeZone) {
    if (!hasDepartureUpdate()) {
      return null;
    }
    TimeUpdate resolved = departureUpdate.resolve(serviceDate, timeZone);
    return resolved.scheduledTimeSecondsSinceMidnight();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ParsedStopTimeUpdate that = (ParsedStopTimeUpdate) o;
    return (
      isExtraCall == that.isExtraCall &&
      predictionInaccurate == that.predictionInaccurate &&
      hasArrived == that.hasArrived &&
      hasDeparted == that.hasDeparted &&
      Objects.equals(stopReference, that.stopReference) &&
      Objects.equals(stopSequence, that.stopSequence) &&
      status == that.status &&
      Objects.equals(arrivalUpdate, that.arrivalUpdate) &&
      Objects.equals(departureUpdate, that.departureUpdate) &&
      pickup == that.pickup &&
      dropoff == that.dropoff &&
      Objects.equals(stopHeadsign, that.stopHeadsign) &&
      occupancy == that.occupancy
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      stopReference,
      stopSequence,
      status,
      arrivalUpdate,
      departureUpdate,
      pickup,
      dropoff,
      stopHeadsign,
      occupancy,
      isExtraCall,
      predictionInaccurate,
      hasArrived,
      hasDeparted
    );
  }

  @Override
  public String toString() {
    return (
      "ParsedStopTimeUpdate{" +
      "stopReference=" +
      stopReference +
      ", stopSequence=" +
      stopSequence +
      ", status=" +
      status +
      ", arrivalUpdate=" +
      arrivalUpdate +
      ", departureUpdate=" +
      departureUpdate +
      ", pickup=" +
      pickup +
      ", dropoff=" +
      dropoff +
      ", stopHeadsign=" +
      stopHeadsign +
      ", occupancy=" +
      occupancy +
      ", isExtraCall=" +
      isExtraCall +
      ", predictionInaccurate=" +
      predictionInaccurate +
      ", hasArrived=" +
      hasArrived +
      ", hasDeparted=" +
      hasDeparted +
      '}'
    );
  }

  /**
   * Builder for ParsedStopTimeUpdate.
   */
  public static class Builder {

    private final StopReference stopReference;
    private Integer stopSequence;
    private StopUpdateStatus status = StopUpdateStatus.SCHEDULED;
    private ParsedTimeUpdate arrivalUpdate;
    private ParsedTimeUpdate departureUpdate;
    private PickDrop pickup;
    private PickDrop dropoff;
    private I18NString stopHeadsign;
    private OccupancyStatus occupancy;
    private boolean isExtraCall;
    private boolean predictionInaccurate;
    private boolean hasArrived;
    private boolean hasDeparted;

    private Builder(StopReference stopReference) {
      this.stopReference = Objects.requireNonNull(stopReference);
    }

    public Builder withStopSequence(Integer stopSequence) {
      this.stopSequence = stopSequence;
      return this;
    }

    public Builder withStatus(StopUpdateStatus status) {
      this.status = status;
      return this;
    }

    public Builder withArrivalUpdate(ParsedTimeUpdate arrivalUpdate) {
      this.arrivalUpdate = arrivalUpdate;
      return this;
    }

    public Builder withDepartureUpdate(ParsedTimeUpdate departureUpdate) {
      this.departureUpdate = departureUpdate;
      return this;
    }

    public Builder withPickup(PickDrop pickup) {
      this.pickup = pickup;
      return this;
    }

    public Builder withDropoff(PickDrop dropoff) {
      this.dropoff = dropoff;
      return this;
    }

    public Builder withStopHeadsign(I18NString stopHeadsign) {
      this.stopHeadsign = stopHeadsign;
      return this;
    }

    public Builder withOccupancy(OccupancyStatus occupancy) {
      this.occupancy = occupancy;
      return this;
    }

    public Builder withIsExtraCall(boolean isExtraCall) {
      this.isExtraCall = isExtraCall;
      return this;
    }

    public Builder withPredictionInaccurate(boolean predictionInaccurate) {
      this.predictionInaccurate = predictionInaccurate;
      return this;
    }

    public Builder withHasArrived(boolean hasArrived) {
      this.hasArrived = hasArrived;
      return this;
    }

    public Builder withHasDeparted(boolean hasDeparted) {
      this.hasDeparted = hasDeparted;
      return this;
    }

    public ParsedStopTimeUpdate build() {
      return new ParsedStopTimeUpdate(
        stopReference,
        stopSequence,
        status,
        arrivalUpdate,
        departureUpdate,
        pickup,
        dropoff,
        stopHeadsign,
        occupancy,
        isExtraCall,
        predictionInaccurate,
        hasArrived,
        hasDeparted
      );
    }
  }
}
