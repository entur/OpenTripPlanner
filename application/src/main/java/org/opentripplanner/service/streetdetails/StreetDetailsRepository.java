package org.opentripplanner.service.streetdetails;

import java.io.Serializable;
import java.util.Optional;
import org.opentripplanner.service.streetdetails.model.EdgeLevelInfo;
import org.opentripplanner.street.model.edge.Edge;

/**
 * Store OSM level and incline data used for returning responses to requests.
 * <p>
 * This is a repository to support the {@link StreetDetailsService}.
 */
public interface StreetDetailsRepository extends Serializable {
  /**
   * Associate the edge with level information.
   */
  void addEdgeLevelInformation(Edge edge, EdgeLevelInfo edgeLevelInfo);

  /**
   * Find level or incline information for a given edge.
   */
  Optional<EdgeLevelInfo> findEdgeInformation(Edge edge);
}
