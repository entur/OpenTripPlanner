package org.opentripplanner.gtfs.mapping;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.onebusaway.gtfs.model.TripSegment;
import org.onebusaway.gtfs.services.GtfsRelationalDao;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.timetable.StopTimeKey;

/**
 * Maps a GTFS trip segment - a range of stops on a single trip - to the {@link StopTimeKey}s of
 * every stop within that range. This allows a notice assigned to a trip segment to be attached to
 * each of the individual stop times the segment covers.
 * <p>
 * The mapped result is stored keyed by {@code trip_segment_id} so that
 * {@link NoticeAssignmentMapper} can look it up when resolving notice assignments.
 */
class TripSegmentMapper {

  private final IdFactory idFactory;
  private final Map<FeedScopedId, List<StopTimeKey>> mappedTripSegments = new HashMap<>();

  TripSegmentMapper(IdFactory idFactory) {
    this.idFactory = idFactory;
  }

  void map(Collection<TripSegment> segments, GtfsRelationalDao data) {
    for (var segment : segments) {
      mappedTripSegments.put(
        idFactory.createId(segment.getId(), "trip_segment_id"),
        mapStopTimeKeys(segment, data)
      );
    }
  }

  /**
   * The {@link StopTimeKey}s for the stops covered by the given {@code trip_segment_id}, or an empty
   * list if no such trip segment was mapped.
   */
  List<StopTimeKey> getStopTimeKeys(FeedScopedId tripSegmentId) {
    return mappedTripSegments.getOrDefault(tripSegmentId, List.of());
  }

  /**
   * Returns a {@link StopTimeKey} for each of the trip's stop times whose stop sequence lies within
   * the segment's {@code [fromStopSequence, toStopSequence]} range.
   */
  private List<StopTimeKey> mapStopTimeKeys(TripSegment segment, GtfsRelationalDao data) {
    var trip = data.getTripForId(segment.getTripId());
    if (trip == null) {
      return List.of();
    }
    var tripId = idFactory.createId(segment.getTripId(), "trip_id in trip segment");
    return data
      .getStopTimesForTrip(trip)
      .stream()
      .filter(
        st ->
          st.getStopSequence() >= segment.getFromStopSequence() &&
          st.getStopSequence() <= segment.getToStopSequence()
      )
      .map(st -> StopTimeKey.of(tripId, st.getStopSequence()).build())
      .toList();
  }
}
