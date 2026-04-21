package org.opentripplanner.street.graph;

import java.util.Optional;
import org.opentripplanner.osm.model.OsmNode;
import org.opentripplanner.street.model.vertex.OsmVertex;

public class GraphFetcher {

  private final Graph graph;

  public GraphFetcher(Graph graph) {
    this.graph = graph;
  }

  public Optional<OsmVertex> getVertexForOsmNode(OsmNode node) {
    return graph.getVerticesOfType(OsmVertex.class)
      .stream()
      .filter(v -> v.nodeId() == node.getId())
      .findFirst();
  }

  public Graph graph() {
    return graph;
  }
}
