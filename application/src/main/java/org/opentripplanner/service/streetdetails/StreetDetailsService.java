package org.opentripplanner.service.streetdetails;

import java.util.Optional;
import org.opentripplanner.service.streetdetails.model.EdgeLevelInfo;
import org.opentripplanner.street.model.edge.Edge;

/**
 * The responsibility of this service is to provide details that can not be found in the street
 * graph, like level and incline information. The information available from this service is not
 * in the street graph because it is not relevant to routing.
 *
 * THIS SERVICE IS AVAILABLE WHEN THE USER MAKES A REQUEST.
 */
public interface StreetDetailsService {
  /**
   * Find level or incline information for a given edge.
   */
  Optional<EdgeLevelInfo> findEdgeInformation(Edge edge);
}
