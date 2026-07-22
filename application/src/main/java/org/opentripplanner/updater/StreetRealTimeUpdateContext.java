package org.opentripplanner.updater;

import org.opentripplanner.street.graph.Graph;

/**
 * Give access to the street model in the context of a real-time update task in the street write
 * domain. The street model must be mutated only from the street domain's writer thread. Timetable
 * data is deliberately absent: it is owned by the transit write domain (see
 * {@link TransitRealTimeUpdateContext}), and reading its mutable state from the street writer
 * thread would race with the transit writer thread.
 */
public interface StreetRealTimeUpdateContext {
  /**
   * Return the street model (graph).
   */
  Graph graph();
}
