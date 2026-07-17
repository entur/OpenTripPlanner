package org.opentripplanner.ext.flexbooking;

import static org.opentripplanner.core.model.id.FeedScopedIdForTestFactory.id;
import static org.opentripplanner.model.FlexStopTimesFactory.area;
import static org.opentripplanner.model.FlexStopTimesFactory.regularStop;
import static org.opentripplanner.transit.model._data.TimetableRepositoryForTest.trip;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.opentripplanner.ext.flex.trip.ScheduledDeviatedTrip;
import org.opentripplanner.ext.flex.trip.UnscheduledTrip;
import org.opentripplanner.model.calendar.CalendarServiceData;
import org.opentripplanner.transit.service.TimetableRepository;

/**
 * Shared transit model for the flex booking mapper and updater tests: an unscheduled flex trip
 * and a scheduled-deviated flex trip, both active on {@link #SERVICE_DATE}.
 */
public final class FlexBookingTestData {

  public static final String FEED_ID = "F";
  public static final String UNSCHEDULED_TRIP_ID = "T1";
  public static final String SCHEDULED_DEVIATED_TRIP_ID = "T2";
  public static final LocalDate SERVICE_DATE = LocalDate.of(2026, 6, 5);
  public static final ZonedDateTime START = SERVICE_DATE.atTime(10, 0).atZone(
    ZoneId.of("Europe/Oslo")
  );

  private FlexBookingTestData() {}

  public static TimetableRepository timetableRepository() {
    var repo = new TimetableRepository();
    var serviceId = id("S1");

    var unscheduled = UnscheduledTrip.of(id(UNSCHEDULED_TRIP_ID))
      .withTrip(trip(UNSCHEDULED_TRIP_ID).withServiceId(serviceId).build())
      .withStopTimes(List.of(area("10:00", "14:00"), area("10:00", "14:00")))
      .build();
    repo.addFlexTrip(unscheduled.getId(), unscheduled);

    var scheduledDeviated = ScheduledDeviatedTrip.of(id(SCHEDULED_DEVIATED_TRIP_ID))
      .withTrip(trip(SCHEDULED_DEVIATED_TRIP_ID).withServiceId(serviceId).build())
      .withStopTimes(List.of(area("10:10", "10:15"), regularStop("10:40", "10:45")))
      .build();
    repo.addFlexTrip(scheduledDeviated.getId(), scheduledDeviated);

    var calendarData = new CalendarServiceData();
    calendarData.putServiceDatesForServiceId(serviceId, List.of(SERVICE_DATE));
    repo.updateCalendarServiceData(calendarData);

    return repo;
  }
}
