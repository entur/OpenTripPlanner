package org.opentripplanner.service.streetdetails;

import java.io.Serializable;
import java.util.Optional;
import org.opentripplanner.service.streetdetails.model.InclinedEdgeLevelInfo;
import org.opentripplanner.service.streetdetails.model.Level;
import org.opentripplanner.street.model.edge.Edge;

/**
 * Store OSM level and incline data used for returning responses to requests.
 * <p>
 * This is a repository to support the {@link StreetDetailsService}.
 */
public interface StreetDetailsRepository extends Serializable {
  /**
   * Associate the inclined edge with level information.
   */
  void addInclinedEdgeLevelInfo(Edge edge, InclinedEdgeLevelInfo edgeLevelInfo);

  /**
   * Find level or incline information for a given inclined edge.
   */
  Optional<InclinedEdgeLevelInfo> findInclinedEdgeLevelInfo(Edge edge);

  /**
   * Associate the edge with level information.
   */
  void addEdgeLevelInfo(Edge edge, Level level);

  /**
   * Find level information for a given edge.
   */
  Optional<Level> findEdgeLevelInfo(Edge edge);
}
