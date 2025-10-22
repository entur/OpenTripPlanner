package org.opentripplanner.service.streetdecorator.model;

import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.OsmVertex;

/**
 * Represents level information for an edge. The information is represented as two
 * {@link VertexLevelInfo} objects for the first and last vertices of an edge. The lower vertex
 * is represented by lowerVertexInfo and the higher one by upperVertexInfo.
 */
public record EdgeLevelInfo(VertexLevelInfo lowerVertexInfo, VertexLevelInfo upperVertexInfo) {
  public EdgeLevelInfo {
    if (lowerVertexInfo == null) {
      throw new IllegalArgumentException("lowerVertexInfo can not be null");
    }
    if (upperVertexInfo == null) {
      throw new IllegalArgumentException("upperVertexInfo can not be null");
    }
  }

  /**
   * Checks if the vertices of the edge match the nodeIds found in the {@link VertexLevelInfo}
   * objects. In other words this function checks if the edge level information in this object
   * can be used for the given edge.
   */
  public boolean canBeAppliedToEdge(Edge edge) {
    return (
      edge.getToVertex() instanceof OsmVertex toVertex &&
      edge.getFromVertex() instanceof OsmVertex fromVertex &&
      ((lowerVertexInfo.osmNodeId() == fromVertex.nodeId() &&
          upperVertexInfo.osmNodeId() == toVertex.nodeId()) ||
        (lowerVertexInfo.osmNodeId() == toVertex.nodeId() &&
          upperVertexInfo.osmNodeId() == fromVertex.nodeId()))
    );
  }
}
