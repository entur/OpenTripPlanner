package org.opentripplanner.updater.trip.gtfs.model;

import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.opentripplanner.transit.model.framework.ImmutableEntityById;
import org.opentripplanner.transit.model.framework.TransitEntity;
import org.opentripplanner.utils.lang.StringUtils;
import org.opentripplanner.utils.time.ServiceDateUtils;

/**
 * Specify which trip a real-time update applies and how it should be applied.
 */
public class TripDescriptor {

  public static final DateTimeFormatter GTFS_LOCAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
  private final GtfsRealtime.TripDescriptor tripDescriptor;

  TripDescriptor(GtfsRealtime.TripDescriptor tripDescriptor) {
    this.tripDescriptor = tripDescriptor;
  }

  Optional<String> routeId() {
    return tripDescriptor.hasRouteId()
      ? Optional.of(tripDescriptor.getRouteId())
      : Optional.empty();
  }

  Optional<LocalDate> startDate() throws ParseException {
    return tripDescriptor.hasStartDate()
      ? Optional.of(ServiceDateUtils.parseString(tripDescriptor.getStartDate()))
      : Optional.empty();
  }

  Optional<LocalTime> startTime() {
    if(tripDescriptor.hasStartTime()) {
      return Optional.of(LocalTime.parse(tripDescriptor.getStartTime(), GTFS_LOCAL_TIME_FORMATTER));
    }
    return Optional.empty();
  }

  ScheduleRelationship scheduleRelationship() {
    return tripDescriptor.hasScheduleRelationship()
      ? tripDescriptor.getScheduleRelationship()
      : ScheduleRelationship.SCHEDULED;
  }

  Optional<String> tripId() {
    return tripDescriptor.hasTripId()
      ? Optional.of(tripDescriptor.getTripId()).filter(StringUtils::hasValue)
      : Optional.empty();
  }

  GtfsRealtime.TripDescriptor original() {
    return tripDescriptor;
  }
}
