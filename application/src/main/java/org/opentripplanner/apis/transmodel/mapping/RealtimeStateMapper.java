package org.opentripplanner.apis.transmodel.mapping;

import org.opentripplanner.apis.transmodel.model.TransmodelRealTimeState;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimes;
import org.opentripplanner.transit.model.timetable.ScheduledTripTimes;
import org.opentripplanner.transit.model.timetable.TripTimes;

/**
 * Maps from the internal model to the Transmodel API. See
 * {@link org.opentripplanner.apis.gtfs.mapping.RealtimeStateMapper} for GTFS (same implementation)
 */
public class RealtimeStateMapper {

  public static TransmodelRealTimeState map(TripTimes tripTimes) {
    if (tripTimes == null) {
      return null;
    }

    return switch (tripTimes) {
      case RealTimeTripTimes realTimeTripTimes -> map(realTimeTripTimes);
      case ScheduledTripTimes _ -> TransmodelRealTimeState.SCHEDULED;
    };
  }

  private static TransmodelRealTimeState map(RealTimeTripTimes realTimeTripTimes) {
    boolean canceled = realTimeTripTimes.isCanceled();
    boolean added = realTimeTripTimes.isAdded();
    boolean modified = realTimeTripTimes.isModified();
    boolean deleted = realTimeTripTimes.isDeleted();
    boolean scheduled = realTimeTripTimes.isScheduled();

    if (canceled || deleted) {
      return TransmodelRealTimeState.CANCELED;
    }
    if (added) {
      return TransmodelRealTimeState.ADDED;
    }
    if (modified) {
      return TransmodelRealTimeState.MODIFIED;
    }
    if (scheduled) {
      return TransmodelRealTimeState.SCHEDULED;
    }
    return TransmodelRealTimeState.UPDATED;
  }
}
