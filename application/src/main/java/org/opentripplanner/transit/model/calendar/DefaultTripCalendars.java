package org.opentripplanner.transit.model.calendar;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.model.calendar.CalendarServiceData;

public class DefaultTripCalendars implements TripCalendars {

  private final CalendarServiceData calendarServiceData;

  public DefaultTripCalendars() {
    this.calendarServiceData = new CalendarServiceData();
  }

  @Override
  public Set<FeedScopedId> listServiceIds() {
    return calendarServiceData.getServiceIds();
  }

  @Override
  public Set<LocalDate> listServiceDates(FeedScopedId serviceId) {
    Set<LocalDate> dates = new HashSet<>();
    List<LocalDate> serviceDates = calendarServiceData.getServiceDatesForServiceId(serviceId);
    if (serviceDates != null) {
      dates.addAll(serviceDates);
    }
    return dates;
  }

  @Override
  public Set<FeedScopedId> listServiceIdsOnServiceDate(LocalDate serviceDate) {
    return calendarServiceData.getServiceIdsForDate(serviceDate);
  }

  public void merge(CalendarServiceData data) {
    calendarServiceData.add(data);
  }

  public FeedScopedId getOrCreateServiceIdForDate(LocalDate serviceDate) {
    return calendarServiceData.getOrCreateServiceIdForDate(serviceDate);
  }

  public Optional<LocalDate> startDate() {
    return calendarServiceData.getFirstDate();
  }

  public Optional<LocalDate> endDate() {
    return calendarServiceData.getLastDate();
  }

  public boolean isEmpty() {
    return calendarServiceData.getServiceIds().isEmpty();
  }
}
