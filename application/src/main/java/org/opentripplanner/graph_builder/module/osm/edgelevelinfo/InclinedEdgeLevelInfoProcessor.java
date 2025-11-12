package org.opentripplanner.graph_builder.module.osm.edgelevelinfo;

import java.util.Optional;
import javax.annotation.Nullable;
import org.opentripplanner.graph_builder.module.osm.OsmDatabase;
import org.opentripplanner.osm.model.OsmWay;
import org.opentripplanner.service.streetdetails.StreetDetailsRepository;
import org.opentripplanner.service.streetdetails.model.InclinedEdgeLevelInfo;
import org.opentripplanner.street.model.edge.Edge;

/**
 * Contains logic for storing edge level info in the
 * {@link StreetDetailsRepository}.
 */
public interface InclinedEdgeLevelInfoProcessor {
  InclinedEdgeLevelInfoProcessor NOOP = new NoopInclinedEdgeLevelInfoProcessor();

  public Optional<InclinedEdgeLevelInfo> findInclinedEdgeLevelInfo(OsmDatabase osmdb, OsmWay way);

  public void storeLevelInfoForEdge(
    @Nullable Edge forwardEdge,
    @Nullable Edge backwardEdge,
    Optional<InclinedEdgeLevelInfo> inclinedEdgeLevelInfoOptional,
    OsmWay way
  );
}
