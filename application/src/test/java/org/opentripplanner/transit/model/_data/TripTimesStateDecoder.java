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
    if (tripTimes.isModified()) {
      stringBuilder.append("MODIFIED ");
    }
    if (tripTimes.isDeleted()) {
      stringBuilder.append("DELETED ");
    }
    if (tripTimes.isScheduled()) {
      stringBuilder.append("SCHEDULED ");
    } else {
      stringBuilder.append("UPDATED ");
    }
    return stringBuilder.toString().trim();
  }
}
