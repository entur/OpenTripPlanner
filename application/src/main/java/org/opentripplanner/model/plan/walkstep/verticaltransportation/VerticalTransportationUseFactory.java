package org.opentripplanner.model.plan.walkstep.verticaltransportation;

import java.util.Optional;
import org.opentripplanner.service.streetdetails.StreetDetailsService;
import org.opentripplanner.service.streetdetails.model.EdgeLevelInfo;
import org.opentripplanner.service.streetdetails.model.VertexLevelInfo;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.edge.EscalatorEdge;
import org.opentripplanner.street.model.edge.StreetEdge;
import org.opentripplanner.street.model.vertex.OsmVertex;

/**
 * Represents information about a single use of a set of stairs related to
 * {@link org.opentripplanner.model.plan.walkstep.WalkStep}.
 */
public class VerticalTransportationUseFactory {

  private final StreetDetailsService streetDetailsService;

  public VerticalTransportationUseFactory(StreetDetailsService streetDetailsService) {
    this.streetDetailsService = streetDetailsService;
  }

  public VerticalTransportationUse createInclinedVerticalTransportationUse(Edge edge) {
    Optional<EdgeLevelInfo> edgeLevelInfoOptional = streetDetailsService.findEdgeInformation(edge);
    if (edgeLevelInfoOptional.isEmpty()) {
      return null;
    }
    EdgeLevelInfo edgeLevelInfo = edgeLevelInfoOptional.get();

    VerticalDirection verticalDirection = edge.getFromVertex() instanceof OsmVertex fromVertex &&
      fromVertex.nodeId() == edgeLevelInfo.lowerVertexInfo().osmNodeId()
      ? VerticalDirection.UP
      : VerticalDirection.DOWN;
    VertexLevelInfo fromVertexInfo = verticalDirection == VerticalDirection.UP
      ? edgeLevelInfo.lowerVertexInfo()
      : edgeLevelInfo.upperVertexInfo();
    VertexLevelInfo toVertexInfo = verticalDirection == VerticalDirection.UP
      ? edgeLevelInfo.upperVertexInfo()
      : edgeLevelInfo.lowerVertexInfo();

    if (edge instanceof EscalatorEdge) {
      return new EscalatorUse(fromVertexInfo.level(), toVertexInfo.level(), verticalDirection);
    }
    if (edge instanceof StreetEdge streetEdge && streetEdge.isStairs()) {
      return new StairsUse(fromVertexInfo.level(), toVertexInfo.level(), verticalDirection);
    }
    return null;
  }
}
