package org.opentripplanner.transit.model.timetable;

import java.util.Collection;
import java.util.SortedSet;
import java.util.function.Function;
import org.opentripplanner.transit.model.framework.FeedScopedId;

/**
 * When the {@link TimetableSnapshot} is updated and the commit method has completed this
 * event is fiered. It allow the implementer to update its own state using the new
 * updated timetables.
 */
public interface TimetableSnapshotUpdateSucessListener {
  void update(
    Collection<Timetable> updatedTimetables,
    Function<FeedScopedId, SortedSet<Timetable>> timetables
  );
}
