package org.opentripplanner.service.vehiclerental.street;

import java.util.Map;
import org.opentripplanner.street.model.RentalRestrictionExtension;
import org.opentripplanner.street.model.edge.StreetEdge;

/**
 * Result of applying geofencing zones to the street graph. Contains both the per-edge extensions
 * (for backward-compatible interior enforcement) and boundary extensions (for state-based tracking),
 * plus the spatial index for zone containment queries.
 *
 * @param modifiedEdges edges with interior zone extensions (GeofencingZoneExtension or
 *     BusinessAreaBorder), used for cleanup on subsequent updater runs
 * @param boundaryEdges edges with boundary-crossing extensions, tracked separately for cleanup
 * @param zoneIndex spatial index of all zones for containment queries
 */
public record GeofencingZoneApplierResult(
  Map<StreetEdge, RentalRestrictionExtension> modifiedEdges,
  Map<StreetEdge, GeofencingBoundaryExtension> boundaryEdges,
  GeofencingZoneIndex zoneIndex
) {}
