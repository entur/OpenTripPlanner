package org.opentripplanner.transit.repository;

import java.time.LocalDate;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;

public interface MutableTimetableSnapshot {
  void update(RealTimeTripUpdate realTimeTripUpdate);

  ReadOnlyTimetableSnapshot createReadOnlySnapshot();

  void clear(String feedId);

  boolean revertTripToScheduledTripPattern(FeedScopedId tripId, LocalDate serviceDate);

  boolean purgeExpiredData(LocalDate serviceDate);

  void clearForBuffer();
}
