package org.opentripplanner.transit.model.calendar;

import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.model.calendar.CalendarServiceData;

public class DefaultTripCalendars implements TripCalendars {

  private final CalendarServiceData calendarServiceData;
  private final Map<LocalDate, TIntSet> serviceCodesRunningForDate = new HashMap<>();
  private final Map<FeedScopedId, Integer> serviceCodes = new HashMap<>();

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

  /**
   * Map from GTFS ServiceIds to integers close to 0. Allows using BitSets instead of
   * {@code Set<Object>}. An empty Map is created before the Graph is built to allow registering IDs
   * from multiple feeds.
   */
  public Map<FeedScopedId, Integer> getServiceCodes() {
    return serviceCodes;
  }

  /**
   * For all dates in the system get the service codes that run on it.
   */
  public Map<LocalDate, TIntSet> getServiceCodesRunningForDate() {
    return Collections.unmodifiableMap(serviceCodesRunningForDate);
  }

  /**
   * Get or create a serviceId for a given date. This method is used when a new trip is added from a
   * realtime data update. It makes sure the date is in the existing transit service period.
   *
   * @param serviceDate service date for the added service id
   * @return service-id for date if it exists or is created. If the given service date is outside the
   * service period {@code null} is returned.
   */
  @Nullable
  public FeedScopedId getOrCreateServiceIdForDate(LocalDate serviceDate) {
    var serviceId = calendarServiceData.getOrCreateServiceIdForDate(serviceDate);

    if (!serviceCodes.containsKey(serviceId)) {
      // Calculating new unique serviceCode based on size (!)
      final int serviceCode = serviceCodes.size();
      serviceCodes.put(serviceId, serviceCode);

      serviceCodesRunningForDate
        .computeIfAbsent(serviceDate, _ -> new TIntHashSet())
        .add(serviceCode);
    }
    return serviceId;
  }

  public void merge(CalendarServiceData data) {
    calendarServiceData.add(data);
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

  public void initializeServiceCodes() {
    for (FeedScopedId serviceId : listServiceIds()) {
      Integer code = serviceCodes.get(serviceId);
      if (code == null) {
        continue;
      }
      for (LocalDate serviceDate : listServiceDates(serviceId)) {
        serviceCodesRunningForDate
          .computeIfAbsent(serviceDate, ignored -> new TIntHashSet())
          .add(code);
      }
    }
  }
}
