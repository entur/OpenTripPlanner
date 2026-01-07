package org.opentripplanner.street.model.linking;

import org.opentripplanner.street.model.edge.Edge;

/**
 * This class is used to keep track of temporary edges added to the graph, so that they can be
 * removed from the graph when no longer needed.
 */
public interface EdgeDisposable {
  void addEdge(Edge edge);
}
