package org.opentripplanner.ext.updater.trip.unified.model.change;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.ext.updater.trip.unified.model.command.ParsedStopTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.StopReference;
import org.opentripplanner.ext.updater.trip.unified.model.command.TimeUpdate;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.OccupancyStatus;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;

/**
 * A stop time update with pre-resolved {@link TimeUpdate} values.
 * <p>
 * Created by the change factories once the service date is known, so that the change side
 * always works with fully-resolved time data.
 */
public final class ResolvedStopTimeUpdate {

  private final StopReference stopReference;

  @Nullable
  private final Integer stopSequence;

  private final ParsedStopTimeUpdate.StopUpdateStatus status;

  @Nullable
  private final PickDrop pickup;

  @Nullable
  private final PickDrop dropoff;

  @Nullable
  private final I18NString stopHeadsign;

  @Nullable
  private final OccupancyStatus occupancy;

  private final boolean pickupCancelled;
  private final boolean dropoffCancelled;
  private final boolean isExtraCall;
  private final boolean predictionInaccurate;
  private final boolean hasArrived;
  private final boolean hasDeparted;

  @Nullable
  private final TimeUpdate arrivalUpdate;

  @Nullable
  private final TimeUpdate departureUpdate;

  private final ResolvedStopReference resolvedStopReference;

  private ResolvedStopTimeUpdate(
    StopReference stopReference,
    @Nullable Integer stopSequence,
    ParsedStopTimeUpdate.StopUpdateStatus status,
    @Nullable PickDrop pickup,
    @Nullable PickDrop dropoff,
    @Nullable I18NString stopHeadsign,
    @Nullable OccupancyStatus occupancy,
    boolean pickupCancelled,
    boolean dropoffCancelled,
    boolean isExtraCall,
    boolean predictionInaccurate,
    boolean hasArrived,
    boolean hasDeparted,
    @Nullable TimeUpdate arrivalUpdate,
    @Nullable TimeUpdate departureUpdate,
    ResolvedStopReference resolvedStopReference
  ) {
    this.stopReference = Objects.requireNonNull(stopReference, "stopReference must not be null");
    this.stopSequence = stopSequence;
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.pickup = pickup;
    this.dropoff = dropoff;
    this.stopHeadsign = stopHeadsign;
    this.occupancy = occupancy;
    this.pickupCancelled = pickupCancelled;
    this.dropoffCancelled = dropoffCancelled;
    this.isExtraCall = isExtraCall;
    this.predictionInaccurate = predictionInaccurate;
    this.hasArrived = hasArrived;
    this.hasDeparted = hasDeparted;
    this.arrivalUpdate = arrivalUpdate;
    this.departureUpdate = departureUpdate;
    this.resolvedStopReference = Objects.requireNonNull(
      resolvedStopReference,
      "resolvedStopReference must not be null"
    );
  }

  /**
   * Create a resolved stop time update from a {@link ParsedStopTimeUpdate} by converting its
   * {@link org.opentripplanner.ext.updater.trip.unified.model.command.ParsedTimeUpdate ParsedTimeUpdate}
   * values to {@link TimeUpdate}. This is a pure conversion - resolving the stop reference
   * against the transit model is the change factories' job, so the resolved stops are taken as
   * given.
   *
   * @param resolvedStops the stops the call's {@link StopReference} resolved to
   */
  public static ResolvedStopTimeUpdate of(
    ParsedStopTimeUpdate parsed,
    LocalDate serviceDate,
    ZoneId timeZone,
    ResolvedStopReference resolvedStops
  ) {
    var arrival = parsed.arrivalUpdate() != null
      ? parsed.arrivalUpdate().resolve(serviceDate, timeZone)
      : null;
    var departure = parsed.departureUpdate() != null
      ? parsed.departureUpdate().resolve(serviceDate, timeZone)
      : null;
    return new ResolvedStopTimeUpdate(
      parsed.stopReference(),
      parsed.stopSequence(),
      parsed.status(),
      parsed.pickup(),
      parsed.dropoff(),
      parsed.stopHeadsign(),
      parsed.occupancy(),
      parsed.pickupCancelled(),
      parsed.dropoffCancelled(),
      parsed.isExtraCall(),
      parsed.predictionInaccurate(),
      parsed.hasArrived(),
      parsed.hasDeparted(),
      arrival,
      departure,
      resolvedStops
    );
  }

  @Nullable
  public TimeUpdate arrivalUpdate() {
    return arrivalUpdate;
  }

  @Nullable
  public TimeUpdate departureUpdate() {
    return departureUpdate;
  }

  /**
   * The stop the call reports it is at - the stop that identifies which scheduled call this update
   * is about - or null if the message names none or names one the transit model does not know.
   */
  @Nullable
  public StopLocation referencedStop() {
    return resolvedStopReference.referencedStop();
  }

  /**
   * The stop assigned in place of the scheduled one, or null if the call assigns none or assigns
   * one the transit model does not know. A stop pattern is only modified for a non-null value.
   */
  @Nullable
  public StopLocation assignedStop() {
    return resolvedStopReference.assignedStop();
  }

  public StopReference stopReference() {
    return stopReference;
  }

  /**
   * The number the call carries in the static feed (GTFS {@code stop_sequence}), or null if the
   * message does not number its calls. It is only required to increase along the trip, so it is
   * <em>not</em> a position in the pattern: resolve it through
   * {@link org.opentripplanner.transit.model.timetable.TripTimes#stopPositionForGtfsSequence(int)}.
   */
  @Nullable
  public Integer stopSequence() {
    return stopSequence;
  }

  public ParsedStopTimeUpdate.StopUpdateStatus status() {
    return status;
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

  public boolean hasArrivalUpdate() {
    return arrivalUpdate != null;
  }

  public boolean hasDepartureUpdate() {
    return departureUpdate != null;
  }

  /**
   * Whether the message forbids boarding at this stop because it cancels the departure - be it the
   * departure end alone (SIRI-ET {@code DepartureStatus=cancelled}) or the whole call, which cancels
   * both of its ends.
   */
  public boolean isPickupCancelled() {
    return isSkipped() || pickupCancelled;
  }

  /** Whether the message forbids alighting at this stop, see {@link #isPickupCancelled()}. */
  public boolean isDropoffCancelled() {
    return isSkipped() || dropoffCancelled;
  }

  public boolean isSkipped() {
    return (
      status == ParsedStopTimeUpdate.StopUpdateStatus.SKIPPED ||
      status == ParsedStopTimeUpdate.StopUpdateStatus.CANCELLED
    );
  }

  /**
   * Apply this stop time update's real-time data to a trip times builder at the given stop index.
   * This sets arrival/departure times, headsign, recorded flag, cancellation, prediction
   * accuracy, extra call flag, and occupancy status.
   */
  public void applyTo(RealTimeTripTimesBuilder builder, int stopIndex) {
    if (hasArrivalUpdate()) {
      builder.withArrivalTime(
        stopIndex,
        arrivalUpdate.resolveTime(builder.getScheduledArrivalTime(stopIndex))
      );
    }
    if (hasDepartureUpdate()) {
      builder.withDepartureTime(
        stopIndex,
        departureUpdate.resolveTime(builder.getScheduledDepartureTime(stopIndex))
      );
    }
    if (stopHeadsign != null) {
      builder.withStopHeadsign(stopIndex, stopHeadsign);
    }
    if (hasArrived) {
      builder.withHasArrived(stopIndex, true);
    }
    if (hasDeparted) {
      builder.withHasDeparted(stopIndex, true);
    }
    if (isSkipped()) {
      builder.withCanceled(stopIndex);
    }
    if (predictionInaccurate && !isSkipped()) {
      builder.withInaccuratePredictions(stopIndex);
    }
    if (isExtraCall) {
      builder.withExtraCall(stopIndex, true);
    }
    if (occupancy != null) {
      builder.withOccupancyStatus(stopIndex, occupancy);
    }
  }
}
