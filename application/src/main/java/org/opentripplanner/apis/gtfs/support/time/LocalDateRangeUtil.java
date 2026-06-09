package org.opentripplanner.apis.gtfs.support.time;

import org.opentripplanner.apis.gtfs.generated.GraphQLTypes;
import org.opentripplanner.core.model.time.LocalDateRange;

public class LocalDateRangeUtil {

  /**
   * Checks if a service date filter input has at least one filter set. If both start and end are
   * null then no filtering is necessary and this method returns false.
   */
  public static boolean hasServiceDateFilter(GraphQLTypes.GraphQLLocalDateRangeInput dateRange) {
    return (
      dateRange != null &&
      !LocalDateRange.ofExclusiveEnd(
        dateRange.getGraphQLStart(),
        dateRange.getGraphQLEnd()
      ).isUnbounded()
    );
  }
}
