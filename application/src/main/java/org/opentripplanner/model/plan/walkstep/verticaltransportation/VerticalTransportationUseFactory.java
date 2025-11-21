package org.opentripplanner.model.plan.walkstep.verticaltransportation;

import java.util.Optional;
import org.opentripplanner.service.streetdetails.StreetDetailsService;
import org.opentripplanner.service.streetdetails.model.InclinedEdgeLevelInfo;
import org.opentripplanner.service.streetdetails.model.Level;
import org.opentripplanner.service.streetdetails.model.VertexLevelInfo;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.edge.ElevatorAlightEdge;
import org.opentripplanner.street.model.edge.ElevatorBoardEdge;
import org.opentripplanner.street.model.edge.EscalatorEdge;
import org.opentripplanner.street.model.edge.StreetEdge;
import org.opentripplanner.street.model.vertex.OsmVertex;
import org.opentripplanner.street.search.state.State;

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
    Optional<InclinedEdgeLevelInfo> inclinedEdgeLevelInfoOptional =
      streetDetailsService.findInclinedEdgeLevelInfo(edge);
    if (inclinedEdgeLevelInfoOptional.isEmpty()) {
      return null;
    }
    InclinedEdgeLevelInfo inclinedEdgeLevelInfo = inclinedEdgeLevelInfoOptional.get();

    VerticalDirection verticalDirection = edge.getFromVertex() instanceof OsmVertex fromVertex &&
      fromVertex.nodeId() == inclinedEdgeLevelInfo.lowerVertexInfo().osmNodeId()
      ? VerticalDirection.UP
      : VerticalDirection.DOWN;
    VertexLevelInfo fromVertexInfo = verticalDirection == VerticalDirection.UP
      ? inclinedEdgeLevelInfo.lowerVertexInfo()
      : inclinedEdgeLevelInfo.upperVertexInfo();
    VertexLevelInfo toVertexInfo = verticalDirection == VerticalDirection.UP
      ? inclinedEdgeLevelInfo.upperVertexInfo()
      : inclinedEdgeLevelInfo.lowerVertexInfo();

    if (edge instanceof EscalatorEdge) {
      return new EscalatorUse(fromVertexInfo.level(), toVertexInfo.level(), verticalDirection);
    }
    if (edge instanceof StreetEdge streetEdge && streetEdge.isStairs()) {
      return new StairsUse(fromVertexInfo.level(), toVertexInfo.level(), verticalDirection);
    }
    return null;
  }

  public VerticalTransportationUse createElevatorUse(
    State backState,
    ElevatorAlightEdge elevatorAlightEdge
  ) {
    ElevatorBoardEdge elevatorBoardEdge = (ElevatorBoardEdge) backState
      .getBackState()
      .getBackEdge();

    Optional<Level> boardEdgeLevelOptional = streetDetailsService.findHorizontalEdgeLevelInfo(
      elevatorBoardEdge
    );
    Optional<Level> alightEdgeLevelOptional = streetDetailsService.findHorizontalEdgeLevelInfo(
      elevatorAlightEdge
    );

    if (boardEdgeLevelOptional.isEmpty() || alightEdgeLevelOptional.isEmpty()) {
      return null;
    }

    Level boardEdgeLevel = boardEdgeLevelOptional.get();
    Level alightEdgeLevel = alightEdgeLevelOptional.get();

    VerticalDirection verticalDirection = VerticalDirection.UNKNOWN;
    if (boardEdgeLevel.level() > alightEdgeLevel.level()) {
      verticalDirection = VerticalDirection.DOWN;
    } else if (boardEdgeLevel.level() < alightEdgeLevel.level()) {
      verticalDirection = VerticalDirection.UP;
    }

    return new ElevatorUse(boardEdgeLevel, alightEdgeLevel, verticalDirection);
  }
}
