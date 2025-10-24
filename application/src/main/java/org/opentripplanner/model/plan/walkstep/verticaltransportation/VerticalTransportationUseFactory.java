package org.opentripplanner.model.plan.walkstep.verticaltransportation;

import java.util.Optional;
import org.opentripplanner.service.streetdetails.StreetDetailsService;
import org.opentripplanner.service.streetdetails.model.EdgeLevelInfo;
import org.opentripplanner.service.streetdetails.model.VertexLevelInfo;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.edge.EscalatorEdge;
import org.opentripplanner.street.model.vertex.OsmVertex;

/**
 * Represents information about a single use of a set of stairs related to
 * {@link org.opentripplanner.model.plan.walkstep.WalkStep}.
 */
public class VerticalTransportationUseFactory {

  public static VerticalTransportationUse getInclinedVerticalTransportationUse(
    Edge edge,
    StreetDetailsService streetDetailsService
  ) {
    Optional<EdgeLevelInfo> edgeLevelInfoOptional = streetDetailsService.findEdgeInformation(edge);
    if (edgeLevelInfoOptional.isEmpty()) {
      return null;
    }

    EdgeLevelInfo edgeLevelInfo = edgeLevelInfoOptional.get();
    VertexLevelInfo fromVertexInfo = edgeLevelInfo.upperVertexInfo();
    VertexLevelInfo toVertexInfo = edgeLevelInfo.lowerVertexInfo();
    VerticalDirection verticalDirection = VerticalDirection.DOWN;
    if (
      edge.getFromVertex() instanceof OsmVertex fromVertex &&
      fromVertex.nodeId() == edgeLevelInfo.lowerVertexInfo().osmNodeId()
    ) {
      verticalDirection = VerticalDirection.UP;
      fromVertexInfo = edgeLevelInfo.lowerVertexInfo();
      toVertexInfo = edgeLevelInfo.upperVertexInfo();
    }

    if (edge instanceof EscalatorEdge) {
      return new EscalatorUse(fromVertexInfo.level(), verticalDirection, toVertexInfo.level());
    } else {
      return new StairsUse(fromVertexInfo.level(), verticalDirection, toVertexInfo.level());
    }
  }
}
