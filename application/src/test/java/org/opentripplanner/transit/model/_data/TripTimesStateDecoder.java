package org.opentripplanner.transit.model._data;

import org.opentripplanner.transit.model.timetable.TripTimes;

public class TripTimesStateDecoder {

  public static String summarizeFromTripTimes(TripTimes tripTimes) {
    StringBuilder stringBuilder = new StringBuilder();
    if (tripTimes.isAdded()) {
      stringBuilder.append("ADDED ");
    }
    if (tripTimes.isCanceled()) {
      stringBuilder.append("CANCELED ");
    }
    if (tripTimes.isTripPatternModified()) {
      stringBuilder.append("PATTERN_MODIFIED ");
    }
    if (tripTimes.isDeleted()) {
      stringBuilder.append("DELETED ");
    }
    if (tripTimes.hasAnyUpdates()) {
      stringBuilder.append("UPDATED ");
    } else {
      stringBuilder.append("SCHEDULED ");
    }
    return stringBuilder.toString().trim();
  }
}
