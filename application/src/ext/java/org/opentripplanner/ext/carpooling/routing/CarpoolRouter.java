package org.opentripplanner.ext.carpooling.routing;

import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.astar.model.GraphPath;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.state.State;

/**
 * Functional interface for street routing.
 */
@FunctionalInterface
public interface CarpoolRouter {
  GraphPath<State, Edge, Vertex> route(Vertex from, Vertex to);

  /**
   * Routes each consecutive leg between {@code waypoints} (waypoint to waypoint, in order) and
   * returns the routed legs — one entry per leg, so {@code waypoints.size() - 1} entries — or
   * {@code null} as soon as any leg cannot be routed.
   */
  @SuppressWarnings("unchecked")
  @Nullable
  default GraphPath<State, Edge, Vertex>[] routeLegs(List<Vertex> waypoints) {
    GraphPath<State, Edge, Vertex>[] legs = new GraphPath[waypoints.size() - 1];
    for (int i = 0; i < legs.length; i++) {
      var leg = route(waypoints.get(i), waypoints.get(i + 1));
      if (leg == null) {
        return null;
      }
      legs[i] = leg;
    }
    return legs;
  }
}
