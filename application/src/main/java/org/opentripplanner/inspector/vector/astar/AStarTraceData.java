package org.opentripplanner.inspector.vector.astar;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;

/**
 * A completed A* search trace containing all captured events.
 */
public record AStarTraceData(
  String id,
  Instant timestamp,
  String description,
  boolean arriveBy,
  List<AStarTraceEvent> events
) {
  public AStarTraceData {
    events = List.copyOf(events);
  }

  public List<AStarTraceEvent.TraverseEvent> traverseEvents() {
    return events
      .stream()
      .filter(AStarTraceEvent.TraverseEvent.class::isInstance)
      .map(AStarTraceEvent.TraverseEvent.class::cast)
      .toList();
  }

  public List<AStarTraceEvent> vertexEvents() {
    return events
      .stream()
      .filter(
        e ->
          e instanceof AStarTraceEvent.DequeueEvent ||
          e instanceof AStarTraceEvent.EnqueueEvent ||
          e instanceof AStarTraceEvent.RejectEvent ||
          e instanceof AStarTraceEvent.GoalEvent
      )
      .toList();
  }

  public int eventCount() {
    return events.size();
  }

  public int maxSeq() {
    return events.isEmpty() ? 0 : events.getLast().seq();
  }

  /**
   * Walk the parent chain from a vertex back to the search origin.
   * Returns the list of coordinates forming the branch path.
   * Uses the lowest-weight dequeue event at each vertex (the explored state).
   */
  public List<Coordinate> branchToOrigin(String vertexId) {
    // Build map: vertexId -> best (lowest weight) dequeue event
    var bestDequeue = new HashMap<String, AStarTraceEvent.DequeueEvent>();
    for (var event : events) {
      if (event instanceof AStarTraceEvent.DequeueEvent dequeue) {
        var existing = bestDequeue.get(dequeue.vertexId());
        if (existing == null || dequeue.weight() < existing.weight()) {
          bestDequeue.put(dequeue.vertexId(), dequeue);
        }
      }
    }

    var coords = new ArrayList<Coordinate>();
    var visited = new HashSet<String>();
    var current = vertexId;

    while (current != null && !visited.contains(current)) {
      visited.add(current);
      var dequeue = bestDequeue.get(current);
      if (dequeue == null) {
        break;
      }

      coords.add(new Coordinate(dequeue.lon(), dequeue.lat()));
      current = dequeue.parentVertexId();
    }

    return coords;
  }

  public AStarTraceSummary toSummary() {
    return new AStarTraceSummary(id, timestamp.toString(), description, eventCount(), maxSeq());
  }
}
