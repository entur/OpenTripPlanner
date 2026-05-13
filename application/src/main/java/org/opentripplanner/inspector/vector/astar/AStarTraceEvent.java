package org.opentripplanner.inspector.vector.astar;

import javax.annotation.Nullable;
import org.locationtech.jts.geom.LineString;

/**
 * Events captured during A* search execution. Each event has a monotonically
 * increasing sequence number for ordering.
 */
public sealed interface AStarTraceEvent {
  int seq();

  record DequeueEvent(
    int seq,
    String vertexId,
    double lat,
    double lon,
    double weight,
    long timeSeconds,
    String mode,
    @Nullable String rentalState,
    @Nullable String network,
    @Nullable String parentVertexId,
    double parentLat,
    double parentLon
  ) implements AStarTraceEvent {}

  record TraverseEvent(
    int seq,
    String fromVertexId,
    String toVertexId,
    double fromLat,
    double fromLon,
    double toLat,
    double toLon,
    @Nullable LineString geometry,
    String edgeClass,
    int resultCount,
    @Nullable String resultSummary
  ) implements AStarTraceEvent {}

  record EnqueueEvent(
    int seq,
    String vertexId,
    double lat,
    double lon,
    double weight,
    double estimate,
    String mode,
    @Nullable String rentalState,
    @Nullable String network
  ) implements AStarTraceEvent {}

  record RejectEvent(int seq, String vertexId, double lat, double lon, double weight) implements
    AStarTraceEvent {}

  record GoalEvent(int seq, String vertexId, double lat, double lon, double weight) implements
    AStarTraceEvent {}
}
