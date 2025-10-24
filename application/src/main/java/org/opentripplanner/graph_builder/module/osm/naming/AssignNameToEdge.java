package org.opentripplanner.graph_builder.module.osm.naming;

import org.locationtech.jts.geom.Geometry;

/**
 * Helper interface with method to assign a name to an edge.
 */
@FunctionalInterface
interface AssignNameToEdge {
  boolean assignNameToEdge(EdgeOnLevel crosswalkOnLevel, Geometry buffer);
}
