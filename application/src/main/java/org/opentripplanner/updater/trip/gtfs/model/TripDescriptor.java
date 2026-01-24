package org.opentripplanner.updater.trip.gtfs.model;

import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.updater.spi.UpdateError.UpdateErrorType;
import org.opentripplanner.utils.lang.StringUtils;
import org.opentripplanner.utils.time.ServiceDateUtils;
import org.opentripplanner.utils.time.TimeUtils;

/**
 * Specify which trip a real-time update applies and how it should be applied.
 */
public class TripDescriptor {

  private final GtfsRealtime.TripDescriptor tripDescriptor;
  private final Supplier<LocalDate> localDateNow;
  private final FeedScopedId tripId;
  private LocalDate serviceDate;

  public TripDescriptor(
    GtfsRealtime.TripDescriptor tripDescriptor,
    String feedId,
    Supplier<LocalDate> localDateNow
  ) {
    this.tripDescriptor = tripDescriptor;
    this.localDateNow = localDateNow;
    this.tripId = tripIdOpt()
      .map(id -> new FeedScopedId(feedId, id))
      .orElse(null);
  }

  public FeedScopedId tripId() {
    return tripId;
  }

  public Optional<String> routeId() {
    return tripDescriptor.hasRouteId()
      ? Optional.of(tripDescriptor.getRouteId())
      : Optional.empty();
  }

  public OptionalInt startTime() {
    return tripDescriptor.hasStartTime()
      ? OptionalInt.of(TimeUtils.time(tripDescriptor.getStartTime()))
      : OptionalInt.empty();
  }

  public Optional<LocalDate> startDate() throws ParseException {
    return tripDescriptor.hasStartDate()
      ? Optional.of(ServiceDateUtils.parseString(tripDescriptor.getStartDate()))
      : Optional.empty();
  }

  public ScheduleRelationship scheduleRelationship() {
    return tripDescriptor.hasScheduleRelationship()
      ? tripDescriptor.getScheduleRelationship()
      : ScheduleRelationship.SCHEDULED;
  }

  public Optional<UpdateErrorType> validate() {
    try {
      startDate();
    } catch (ParseException e) {
      return Optional.of(UpdateErrorType.INVALID_INPUT_STRUCTURE);
    }

    if (tripId == null) {
      return Optional.of(UpdateErrorType.INVALID_INPUT_STRUCTURE);
    }
    return Optional.empty();
  }

  LocalDate serviceDate() {
    if (serviceDate != null) {
      return serviceDate;
    }
    try {
      // TODO: figure out the correct service date. For the special case that a trip
      // starts for example at 40:00, yesterday would probably be a better guess.
      serviceDate = startDate().orElse(localDateNow.get());
      return serviceDate;
    } catch (ParseException e) {
      throw new RuntimeException(
        "TripDescription does not have a valid startDate: call validate() first."
      );
    }
  }

  private Optional<String> tripIdOpt() {
    return tripDescriptor.hasTripId()
      ? Optional.of(tripDescriptor.getTripId()).filter(StringUtils::hasValue)
      : Optional.empty();
  }

  GtfsRealtime.TripDescriptor original() {
    return tripDescriptor;
  }
}
