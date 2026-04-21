package org.opentripplanner.service.vehiclerental.street;

import java.util.Map;
import org.opentripplanner.street.model.edge.StreetEdge;

/**
 * Result of applying geofencing zones to the street graph.
 *
 * @param businessAreaEdges edges with BusinessAreaBorder, tracked for cleanup on subsequent runs
 * @param boundaryEdges edges with boundary-crossing extensions, tracked for cleanup
 * @param zoneIndex spatial index of all zones for containment queries
 */
public record GeofencingZoneApplierResult(
  Map<StreetEdge, BusinessAreaBorder> businessAreaEdges,
  Map<StreetEdge, GeofencingBoundaryExtension> boundaryEdges,
  GeofencingZoneIndex zoneIndex
) {}
