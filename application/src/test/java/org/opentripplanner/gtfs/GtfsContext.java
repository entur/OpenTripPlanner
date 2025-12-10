package org.opentripplanner.gtfs;

import org.opentripplanner.model.TransitDataImport;
import org.opentripplanner.model.calendar.CalendarServiceData;

public interface GtfsContext {
  String getFeedId();

  TransitDataImport getTransitService();

  CalendarServiceData getCalendarServiceData();
}
