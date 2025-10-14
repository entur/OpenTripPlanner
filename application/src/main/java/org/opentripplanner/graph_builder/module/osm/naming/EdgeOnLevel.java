package org.opentripplanner.graph_builder.module.osm.naming;

import java.util.Set;
import org.opentripplanner.osm.model.OsmWay;
import org.opentripplanner.street.model.edge.StreetEdge;

record EdgeOnLevel(OsmWay way, StreetEdge edge, Set<String> levels) {}
