package org.opentripplanner.service.vehiclerental.street;

import java.util.Map;
import java.util.Set;
import org.opentripplanner.street.model.edge.StreetEdge;
import org.opentripplanner.street.model.vertex.Vertex;

/**
 * Result of applying geofencing zones to the street graph.
 *
 * @param businessAreaEdges edges with BusinessAreaBorder (edge -> network), tracked for cleanup
 * @param boundaryVertices vertices with boundary-crossing extensions, tracked for cleanup
 * @param zoneIndex spatial index of all zones for containment queries
 */
public record GeofencingZoneApplierResult(
  Map<StreetEdge, String> businessAreaEdges,
  Set<Vertex> boundaryVertices,
  GeofencingZoneIndex zoneIndex
) {}
