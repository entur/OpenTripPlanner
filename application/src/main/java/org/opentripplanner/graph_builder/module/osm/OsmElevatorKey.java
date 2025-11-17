package org.opentripplanner.graph_builder.module.osm;

import org.opentripplanner.osm.model.OsmEntityType;
import org.opentripplanner.osm.model.OsmLevel;

public record OsmElevatorKey(
  OsmLevel level,
  long nodeId,
  OsmEntityType osmEntityType,
  long entityId
) {}
