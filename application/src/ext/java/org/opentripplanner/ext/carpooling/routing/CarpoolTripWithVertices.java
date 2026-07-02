package org.opentripplanner.ext.carpooling.routing;

import java.util.List;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.opentripplanner.street.model.vertex.Vertex;

/**
 * Pairs a {@link CarpoolTrip} with the street graph vertices its route points resolve to, one
 * vertex per route point, in route order.
 * <p>
 * The vertices are permanent graph vertices, resolved once per trip at ingest by
 * {@link CarpoolTripVertexResolver}, so an instance is valid for the trip's whole lifetime.
 * CarpoolTrip itself is an immutable domain entity and stays free of graph-level concerns; this
 * record provides that association for routing.
 */
public record CarpoolTripWithVertices(CarpoolTrip trip, List<Vertex> vertices) {
  public CarpoolTripWithVertices {
    if (vertices.size() != trip.stops().size()) {
      throw new IllegalArgumentException(
        "Number of vertices (%d) does not match number of stops (%d)".formatted(
          vertices.size(),
          trip.stops().size()
        )
      );
    }
    vertices = List.copyOf(vertices);
  }
}
