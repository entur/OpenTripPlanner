package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.OccupancyStatus;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.updater.trip.StopResolver;

/**
 * A stop time update with pre-resolved {@link TimeUpdate} values.
 * <p>
 * Created by resolvers after the service date is known, so that handlers
 * always work with fully-resolved time data.
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

  private final boolean isExtraCall;
  private final boolean predictionInaccurate;
  private final boolean recorded;

  @Nullable
  private final TimeUpdate arrivalUpdate;

  @Nullable
  private final TimeUpdate departureUpdate;

  @Nullable
  private final StopLocation stop;

  private ResolvedStopTimeUpdate(
    StopReference stopReference,
    @Nullable Integer stopSequence,
    ParsedStopTimeUpdate.StopUpdateStatus status,
    @Nullable PickDrop pickup,
    @Nullable PickDrop dropoff,
    @Nullable I18NString stopHeadsign,
    @Nullable OccupancyStatus occupancy,
    boolean isExtraCall,
    boolean predictionInaccurate,
    boolean recorded,
    @Nullable TimeUpdate arrivalUpdate,
    @Nullable TimeUpdate departureUpdate,
    @Nullable StopLocation stop
  ) {
    this.stopReference = Objects.requireNonNull(stopReference, "stopReference must not be null");
    this.stopSequence = stopSequence;
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.pickup = pickup;
    this.dropoff = dropoff;
    this.stopHeadsign = stopHeadsign;
    this.occupancy = occupancy;
    this.isExtraCall = isExtraCall;
    this.predictionInaccurate = predictionInaccurate;
    this.recorded = recorded;
    this.arrivalUpdate = arrivalUpdate;
    this.departureUpdate = departureUpdate;
    this.stop = stop;
  }

  /**
   * Resolve a single {@link ParsedStopTimeUpdate} by converting its
   * {@link ParsedTimeUpdate} values to {@link TimeUpdate} and resolving the stop.
   */
  public static ResolvedStopTimeUpdate resolve(
    ParsedStopTimeUpdate parsed,
    LocalDate serviceDate,
    ZoneId timeZone,
    StopResolver stopResolver
  ) {
    var arrival = parsed.arrivalUpdate() != null
      ? parsed.arrivalUpdate().resolve(serviceDate, timeZone)
      : null;
    var departure = parsed.departureUpdate() != null
      ? parsed.departureUpdate().resolve(serviceDate, timeZone)
      : null;
    var stop = stopResolver.resolve(parsed.stopReference());
    return new ResolvedStopTimeUpdate(
      parsed.stopReference(),
      parsed.stopSequence(),
      parsed.status(),
      parsed.pickup(),
      parsed.dropoff(),
      parsed.stopHeadsign(),
      parsed.occupancy(),
      parsed.isExtraCall(),
      parsed.predictionInaccurate(),
      parsed.recorded(),
      arrival,
      departure,
      stop
    );
  }

  /**
   * Resolve all stop time updates in a list.
   */
  public static List<ResolvedStopTimeUpdate> resolveAll(
    List<ParsedStopTimeUpdate> updates,
    LocalDate serviceDate,
    ZoneId timeZone,
    StopResolver stopResolver
  ) {
    return updates
      .stream()
      .map(u -> resolve(u, serviceDate, timeZone, stopResolver))
      .toList();
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
   * The resolved stop location, or null if the stop could not be resolved.
   */
  @Nullable
  public StopLocation stop() {
    return stop;
  }

  public StopReference stopReference() {
    return stopReference;
  }

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

  public boolean recorded() {
    return recorded;
  }

  public boolean hasArrivalUpdate() {
    return arrivalUpdate != null;
  }

  public boolean hasDepartureUpdate() {
    return departureUpdate != null;
  }

  public boolean isSkipped() {
    return (
      status == ParsedStopTimeUpdate.StopUpdateStatus.SKIPPED ||
      status == ParsedStopTimeUpdate.StopUpdateStatus.CANCELLED
    );
  }

  /**
   * Returns true if all updates in the list are skipped and the list is non-empty.
   */
  public static boolean allSkipped(List<ResolvedStopTimeUpdate> updates) {
    return !updates.isEmpty() && updates.stream().allMatch(ResolvedStopTimeUpdate::isSkipped);
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
    if (recorded) {
      builder.withRecorded(stopIndex);
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
