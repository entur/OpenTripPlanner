package org.opentripplanner.graph_builder.module.osm.edgelevelinfo;

import java.util.Optional;
import javax.annotation.Nullable;
import org.opentripplanner.graph_builder.module.osm.OsmDatabase;
import org.opentripplanner.osm.model.OsmWay;
import org.opentripplanner.service.streetdetails.model.EdgeLevelInfo;
import org.opentripplanner.street.model.edge.Edge;

public class NoopEdgeLevelInfoProcessor implements EdgeLevelInfoProcessor {

  @Override
  public Optional<EdgeLevelInfo> getEdgeLevelInfo(OsmDatabase osmdb, OsmWay way) {
    return Optional.empty();
  }

  @Override
  public void storeLevelInfoForEdge(
    @Nullable Edge forwardEdge,
    @Nullable Edge backwardEdge,
    Optional<EdgeLevelInfo> edgeLevelInfoOptional,
    OsmWay way
  ) {}
}
