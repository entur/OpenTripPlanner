package org.opentripplanner.graph_builder.module.osm.edgelevelinfo;

import java.util.Optional;
import javax.annotation.Nullable;
import org.opentripplanner.graph_builder.module.osm.OsmDatabase;
import org.opentripplanner.osm.model.OsmWay;
import org.opentripplanner.service.streetdetails.StreetDetailsRepository;
import org.opentripplanner.service.streetdetails.model.EdgeLevelInfo;
import org.opentripplanner.street.model.edge.Edge;

/**
 * Contains logic for storing edge level info in the
 * {@link StreetDetailsRepository}.
 */
public interface EdgeLevelInfoProcessor {
  EdgeLevelInfoProcessor NOOP = new NoopEdgeLevelInfoProcessor();

  public Optional<EdgeLevelInfo> getEdgeLevelInfo(OsmDatabase osmdb, OsmWay way);

  public void storeLevelInfoForEdge(
    @Nullable Edge forwardEdge,
    @Nullable Edge backwardEdge,
    Optional<EdgeLevelInfo> edgeLevelInfoOptional,
    OsmWay way
  );
}
