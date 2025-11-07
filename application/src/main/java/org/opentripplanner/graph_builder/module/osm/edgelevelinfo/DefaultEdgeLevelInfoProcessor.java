package org.opentripplanner.graph_builder.module.osm.edgelevelinfo;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.graph_builder.issues.ContradictoryLevelAndInclineInfoForWay;
import org.opentripplanner.graph_builder.issues.CouldNotApplyMultiLevelInfoToWay;
import org.opentripplanner.graph_builder.module.osm.OsmDatabase;
import org.opentripplanner.osm.model.OsmLevel;
import org.opentripplanner.osm.model.OsmWay;
import org.opentripplanner.service.streetdetails.StreetDetailsRepository;
import org.opentripplanner.service.streetdetails.model.EdgeLevelInfo;
import org.opentripplanner.service.streetdetails.model.Level;
import org.opentripplanner.service.streetdetails.model.VertexLevelInfo;
import org.opentripplanner.street.model.edge.Edge;

public class DefaultEdgeLevelInfoProcessor implements EdgeLevelInfoProcessor {

  private final DataImportIssueStore issueStore;
  private final StreetDetailsRepository streetDetailsRepository;

  public DefaultEdgeLevelInfoProcessor(
    DataImportIssueStore issueStore,
    StreetDetailsRepository streetDetailsRepository
  ) {
    this.issueStore = issueStore;
    this.streetDetailsRepository = streetDetailsRepository;
  }

  @Override
  public Optional<EdgeLevelInfo> findEdgeLevelInfo(OsmDatabase osmdb, OsmWay way) {
    var nodeRefs = way.getNodeRefs();
    long firstNodeRef = nodeRefs.get(0);
    long lastNodeRef = nodeRefs.get(nodeRefs.size() - 1);

    EdgeLevelInfo levelInfo = findLevelInfo(osmdb, way, firstNodeRef, lastNodeRef);
    EdgeLevelInfo inclineInfo = findInclineInfo(osmdb, way, firstNodeRef, lastNodeRef);

    if (
      levelInfo != null &&
      inclineInfo != null &&
      levelInfo.lowerVertexInfo() != inclineInfo.lowerVertexInfo()
    ) {
      issueStore.add(new ContradictoryLevelAndInclineInfoForWay(way));
      // Default to level info in case of contradictory information. Ideally this should be from
      // the tag that is more reliable.
      return Optional.of(levelInfo);
    } else if (levelInfo != null) {
      return Optional.of(levelInfo);
    } else if (inclineInfo != null) {
      return Optional.of(inclineInfo);
    }
    return Optional.empty();
  }

  private EdgeLevelInfo findLevelInfo(
    OsmDatabase osmdb,
    OsmWay way,
    long firstNodeRef,
    long lastNodeRef
  ) {
    List<OsmLevel> levels = osmdb.getLevelsForEntity(way);
    if (levels.size() == 2) {
      OsmLevel firstVertexOsmLevel = levels.get(0);
      OsmLevel lastVertexOsmLevel = levels.get(1);
      if (firstVertexOsmLevel.level() < lastVertexOsmLevel.level()) {
        return new EdgeLevelInfo(
          new VertexLevelInfo(
            new Level(firstVertexOsmLevel.level(), firstVertexOsmLevel.name()),
            firstNodeRef
          ),
          new VertexLevelInfo(
            new Level(lastVertexOsmLevel.level(), lastVertexOsmLevel.name()),
            lastNodeRef
          )
        );
      } else if (firstVertexOsmLevel.level() > lastVertexOsmLevel.level()) {
        return new EdgeLevelInfo(
          new VertexLevelInfo(
            new Level(lastVertexOsmLevel.level(), lastVertexOsmLevel.name()),
            lastNodeRef
          ),
          new VertexLevelInfo(
            new Level(firstVertexOsmLevel.level(), firstVertexOsmLevel.name()),
            firstNodeRef
          )
        );
      }
    }
    return null;
  }

  private EdgeLevelInfo findInclineInfo(
    OsmDatabase osmdb,
    OsmWay way,
    long firstNodeRef,
    long lastNodeRef
  ) {
    if (way.isInclineUp()) {
      return new EdgeLevelInfo(
        new VertexLevelInfo(null, firstNodeRef),
        new VertexLevelInfo(null, lastNodeRef)
      );
    } else if (way.isInclineDown()) {
      return new EdgeLevelInfo(
        new VertexLevelInfo(null, lastNodeRef),
        new VertexLevelInfo(null, firstNodeRef)
      );
    }
    return null;
  }

  @Override
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
    if (edge != null && edgeLevelInfo.canBeAppliedToEdge(edge)) {
      if (forwardEdge != null) {
        streetDetailsRepository.addEdgeLevelInformation(forwardEdge, edgeLevelInfo);
      }
      if (backwardEdge != null) {
        streetDetailsRepository.addEdgeLevelInformation(backwardEdge, edgeLevelInfo);
      }
    } else {
      issueStore.add(new CouldNotApplyMultiLevelInfoToWay(way, way.getNodeRefs().size()));
    }
  }
}
