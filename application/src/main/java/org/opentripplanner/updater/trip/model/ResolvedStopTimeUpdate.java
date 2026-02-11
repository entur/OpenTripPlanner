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
import org.opentripplanner.updater.trip.StopResolver;

/**
 * A stop time update with pre-resolved {@link TimeUpdate} values.
 * <p>
 * Created by resolvers after the service date is known, so that handlers
 * always work with fully-resolved time data.
 */
public final class ResolvedStopTimeUpdate {

  private final ParsedStopTimeUpdate parsed;

  @Nullable
  private final TimeUpdate arrivalUpdate;

  @Nullable
  private final TimeUpdate departureUpdate;

  @Nullable
  private final StopLocation stop;

  private ResolvedStopTimeUpdate(
    ParsedStopTimeUpdate parsed,
    @Nullable TimeUpdate arrivalUpdate,
    @Nullable TimeUpdate departureUpdate,
    @Nullable StopLocation stop
  ) {
    this.parsed = Objects.requireNonNull(parsed, "parsed must not be null");
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
    return new ResolvedStopTimeUpdate(parsed, arrival, departure, stop);
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

  /**
   * The underlying parsed stop time update.
   */
  public ParsedStopTimeUpdate parsed() {
    return parsed;
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

  // ========== Delegated accessors ==========

  public StopReference stopReference() {
    return parsed.stopReference();
  }

  @Nullable
  public Integer stopSequence() {
    return parsed.stopSequence();
  }

  public ParsedStopTimeUpdate.StopUpdateStatus status() {
    return parsed.status();
  }

  @Nullable
  public PickDrop pickup() {
    return parsed.pickup();
  }

  @Nullable
  public PickDrop dropoff() {
    return parsed.dropoff();
  }

  @Nullable
  public I18NString stopHeadsign() {
    return parsed.stopHeadsign();
  }

  @Nullable
  public OccupancyStatus occupancy() {
    return parsed.occupancy();
  }

  public boolean isExtraCall() {
    return parsed.isExtraCall();
  }

  public boolean predictionInaccurate() {
    return parsed.predictionInaccurate();
  }

  public boolean recorded() {
    return parsed.recorded();
  }

  public boolean hasArrivalUpdate() {
    return arrivalUpdate != null;
  }

  public boolean hasDepartureUpdate() {
    return departureUpdate != null;
  }

  public boolean isSkipped() {
    return parsed.isSkipped();
  }
}
