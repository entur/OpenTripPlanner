package org.opentripplanner.updater.trip.gtfs.model;

import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Supplier;
import org.opentripplanner.utils.lang.StringUtils;
import org.opentripplanner.utils.time.ServiceDateUtils;

/**
 * Specify which trip a real-time update applies and how it should be applied.
 */
public class TripDescriptor {

  private final GtfsRealtime.TripDescriptor tripDescriptor;
  private final Supplier<LocalDate> localDateNow;
  private LocalDate serviceDate;

  TripDescriptor(GtfsRealtime.TripDescriptor tripDescriptor, Supplier<LocalDate> localDateNow) {
    this.tripDescriptor = tripDescriptor;
    this.localDateNow = localDateNow;
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

  ScheduleRelationship scheduleRelationship() {
    return tripDescriptor.hasScheduleRelationship()
      ? tripDescriptor.getScheduleRelationship()
      : ScheduleRelationship.SCHEDULED;
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

  Optional<String> tripId() {
    return tripDescriptor.hasTripId()
      ? Optional.of(tripDescriptor.getTripId()).filter(StringUtils::hasValue)
      : Optional.empty();
  }

  GtfsRealtime.TripDescriptor original() {
    return tripDescriptor;
  }
}
