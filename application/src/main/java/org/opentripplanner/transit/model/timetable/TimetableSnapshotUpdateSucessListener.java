package org.opentripplanner.transit.model.timetable;

import java.util.Collection;
import java.util.Map;
import java.util.SortedSet;
import org.opentripplanner.transit.model.network.TripPattern;

/**
 * When the {@link TimetableSnapshot} is updated and the commit method has completed this
 * event is fiered. It allow the implementer to update its own state using the new
 * updated timetables.
 */
public interface TimetableSnapshotUpdateSucessListener {
  void update(
    Collection<Timetable> updatedTimetables,
    Map<TripPattern, SortedSet<Timetable>> timetables
  );
}
