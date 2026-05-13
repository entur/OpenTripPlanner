package org.opentripplanner.inspector.vector.astar;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.astar.spi.AStarTrace;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.edge.StreetEdge;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.state.State;

/**
 * Captures A* search events into an in-memory list for debug visualization.
 * Not thread-safe — each search creates its own instance.
 */
public class InMemoryAStarTrace implements AStarTrace<State, Edge, Vertex> {

  private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

  private final List<AStarTraceEvent> events = new ArrayList<>();
  private final boolean arriveBy;
  private int seq = 0;

  public InMemoryAStarTrace(boolean arriveBy) {
    this.arriveBy = arriveBy;
  }

  @Override
  public void dequeue(State state) {
    var vertex = state.getVertex();
    var coord = vertex.getCoordinate();
    var parent = state.getBackState();
    String parentVertexId = null;
    double parentLat = 0;
    double parentLon = 0;
    if (parent != null) {
      var parentVertex = parent.getVertex();
      var parentCoord = parentVertex.getCoordinate();
      parentVertexId = parentVertex.getLabelString();
      parentLat = parentCoord.y;
      parentLon = parentCoord.x;
    }
    events.add(
      new AStarTraceEvent.DequeueEvent(
        seq++,
        vertex.getLabelString(),
        coord.y,
        coord.x,
        state.getWeight(),
        state.getTimeSeconds(),
        state.currentMode().toString(),
        state.getVehicleRentalState() != null ? state.getVehicleRentalState().toString() : null,
        state.getVehicleRentalNetwork(),
        parentVertexId,
        parentLat,
        parentLon
      )
    );
  }

  @Override
  public void skipAlreadyDominated(State state) {
    // Not recorded — these are high-volume and low-value for visualization
  }

  @Override
  public void traverse(State fromState, Edge edge, State[] results) {
    var from = edge.getFromVertex();
    var to = edge.getToVertex();
    var fromCoord = from.getCoordinate();
    var toCoord = to.getCoordinate();

    LineString geometry = null;
    if (edge instanceof StreetEdge streetEdge) {
      geometry = streetEdge.getGeometry();
    }
    if (geometry == null) {
      geometry = GEOMETRY_FACTORY.createLineString(new Coordinate[] { fromCoord, toCoord });
    }

    String resultSummary = null;
    if (results.length > 0) {
      var sb = new StringBuilder();
      for (int i = 0; i < results.length; i++) {
        if (i > 0) {
          sb.append(" | ");
        }
        var r = results[i];
        sb.append(r.currentMode());
        if (r.getVehicleRentalState() != null) {
          sb.append("/").append(r.getVehicleRentalState());
        }
        sb.append(" w=").append(String.format("%.1f", r.getWeight()));
      }
      resultSummary = sb.toString();
    }

    events.add(
      new AStarTraceEvent.TraverseEvent(
        seq++,
        from.getLabelString(),
        to.getLabelString(),
        fromCoord.y,
        fromCoord.x,
        toCoord.y,
        toCoord.x,
        geometry,
        edge.getClass().getSimpleName(),
        results.length,
        resultSummary
      )
    );
  }

  @Override
  public void enqueue(State state, double estimate) {
    var vertex = state.getVertex();
    var coord = vertex.getCoordinate();
    events.add(
      new AStarTraceEvent.EnqueueEvent(
        seq++,
        vertex.getLabelString(),
        coord.y,
        coord.x,
        state.getWeight(),
        estimate,
        state.currentMode().toString(),
        state.getVehicleRentalState() != null ? state.getVehicleRentalState().toString() : null,
        state.getVehicleRentalNetwork()
      )
    );
  }

  @Override
  public void reject(State state) {
    var vertex = state.getVertex();
    var coord = vertex.getCoordinate();
    events.add(
      new AStarTraceEvent.RejectEvent(
        seq++,
        vertex.getLabelString(),
        coord.y,
        coord.x,
        state.getWeight()
      )
    );
  }

  @Override
  public void goal(State state) {
    var vertex = state.getVertex();
    var coord = vertex.getCoordinate();
    events.add(
      new AStarTraceEvent.GoalEvent(
        seq++,
        vertex.getLabelString(),
        coord.y,
        coord.x,
        state.getWeight()
      )
    );
  }

  @Override
  public void searchComplete(int nVisited) {
    // No-op — the trace is finalized when toTraceData() is called
  }

  public AStarTraceData toTraceData(String description) {
    return new AStarTraceData(
      UUID.randomUUID().toString(),
      Instant.now(),
      description,
      arriveBy,
      events
    );
  }
}
