package org.opentripplanner.transit.model.calendar;

import java.time.LocalDate;
import java.util.Set;
import org.opentripplanner.core.model.id.FeedScopedId;

/**
 *
 *
 * TODO - Convert return types to List not Set.
 */
public interface TripCalendars {
  /**
   * @return all service ids used in the data set. The list
   */
  Set<FeedScopedId> listServiceIds();

  /**
   * @param serviceId the target service id
   * @return the set of all service dates for which the specified service id is active
   */
  Set<LocalDate> listServiceDates(FeedScopedId serviceId);

  /**
   * Determine the set of service ids that are active on the specified service date.
   *
   * @param serviceDate the target service date
   * @return the set of service ids that are active on the specified service date
   */
  Set<FeedScopedId> listServiceIdsOnServiceDate(LocalDate serviceDate);
}
