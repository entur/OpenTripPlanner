package org.opentripplanner.graph_builder.module.osm;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.graph_builder.issues.CouldNotApplyMultiLevelInfoToWay;
import org.opentripplanner.osm.model.OsmLevel;
import org.opentripplanner.osm.model.OsmWay;
import org.opentripplanner.service.streetdecorator.OsmStreetDecoratorRepository;
import org.opentripplanner.service.streetdecorator.model.EdgeLevelInfo;
import org.opentripplanner.service.streetdecorator.model.Level;
import org.opentripplanner.service.streetdecorator.model.VertexLevelInfo;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.OsmVertex;

/**
 * Contains logic for storing edge level info in the
 * {@link OsmStreetDecoratorRepository}.
 */
class EdgeLevelInfoProcessor {

  private final DataImportIssueStore issueStore;
  private final OsmStreetDecoratorRepository osmStreetDecoratorRepository;
  private final boolean includeEdgeLevelInfo;

  public EdgeLevelInfoProcessor(
    DataImportIssueStore issueStore,
    boolean includeEdgeLevelInfo,
    OsmStreetDecoratorRepository osmStreetDecoratorRepository
  ) {
    this.issueStore = issueStore;
    this.includeEdgeLevelInfo = includeEdgeLevelInfo;
    this.osmStreetDecoratorRepository = osmStreetDecoratorRepository;
  }

  public Optional<EdgeLevelInfo> getEdgeLevelInfo(OsmDatabase osmdb, OsmWay way) {
    if (!includeEdgeLevelInfo) {
      return Optional.empty();
    }

    List<OsmLevel> levels = osmdb.getLevelsForEntity(way);
    var nodeRefs = way.getNodeRefs();
    if (nodeRefs.size() > 1) {
      long firstNodeRef = nodeRefs.get(0);
      long lastNodeRef = nodeRefs.get(nodeRefs.size() - 1);
      if (levels.size() == 2) {
        OsmLevel firstVertexOsmLevel = levels.get(0);
        OsmLevel lastVertexOsmLevel = levels.get(1);
        if (firstVertexOsmLevel.level() < lastVertexOsmLevel.level()) {
          return Optional.of(
            new EdgeLevelInfo(
              new VertexLevelInfo(
                new Level(firstVertexOsmLevel.level(), firstVertexOsmLevel.name()),
                firstNodeRef
              ),
              new VertexLevelInfo(
                new Level(lastVertexOsmLevel.level(), lastVertexOsmLevel.name()),
                lastNodeRef
              )
            )
          );
        } else if (firstVertexOsmLevel.level() > lastVertexOsmLevel.level()) {
          return Optional.of(
            new EdgeLevelInfo(
              new VertexLevelInfo(
                new Level(lastVertexOsmLevel.level(), lastVertexOsmLevel.name()),
                lastNodeRef
              ),
              new VertexLevelInfo(
                new Level(firstVertexOsmLevel.level(), firstVertexOsmLevel.name()),
                firstNodeRef
              )
            )
          );
        }
      }

      if (way.isInclineUp()) {
        return Optional.of(
          new EdgeLevelInfo(
            new VertexLevelInfo(null, firstNodeRef),
            new VertexLevelInfo(null, lastNodeRef)
          )
        );
      } else if (way.isInclineDown()) {
        return Optional.of(
          new EdgeLevelInfo(
            new VertexLevelInfo(null, lastNodeRef),
            new VertexLevelInfo(null, firstNodeRef)
          )
        );
      }
    }
    return Optional.empty();
  }

  public void storeLevelInfoForEdge(
    @Nullable Edge forwardEdge,
    @Nullable Edge backwardEdge,
    Optional<EdgeLevelInfo> edgeLevelInfoOptional,
    OsmWay way
  ) {
    if (edgeLevelInfoOptional.isEmpty()) {
      return;
    }

    EdgeLevelInfo edgeLevelInfo = edgeLevelInfoOptional.get();
    Edge edge = forwardEdge != null ? forwardEdge : backwardEdge;
    if (
      edge != null &&
      edge.getToVertex() instanceof OsmVertex toVertex &&
      edge.getFromVertex() instanceof OsmVertex fromVertex &&
      edgeLevelInfo.matchesNodes(fromVertex.nodeId(), toVertex.nodeId())
    ) {
      if (forwardEdge != null) {
        osmStreetDecoratorRepository.addEdgeLevelInformation(forwardEdge, edgeLevelInfo);
      }
      if (backwardEdge != null) {
        osmStreetDecoratorRepository.addEdgeLevelInformation(backwardEdge, edgeLevelInfo);
      }
    } else {
      issueStore.add(new CouldNotApplyMultiLevelInfoToWay(way, way.getNodeRefs().size()));
    }
  }
}
