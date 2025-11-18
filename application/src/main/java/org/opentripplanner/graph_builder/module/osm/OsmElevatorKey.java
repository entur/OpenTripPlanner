package org.opentripplanner.graph_builder.module.osm;

import org.opentripplanner.osm.model.OsmEntityType;

public record OsmElevatorKey(long nodeId, OsmEntityType osmEntityType, long entityId) {}
