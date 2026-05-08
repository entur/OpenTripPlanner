package org.opentripplanner.service.vehiclerental.street;

import org.opentripplanner.service.vehiclerental.model.GeofencingZone;

/**
 * Marks a vertex on the boundary of a geofencing zone. This is a data marker used by state-based
 * zone tracking in the routing algorithm — it does not enforce any restrictions itself. Applied to
 * both vertices of a boundary-crossing edge with opposite entering flags.
 *
 * @param zone the geofencing zone whose boundary this vertex lies on
 * @param entering true if traversing away from this vertex enters the zone; false if it exits
 */
public record GeofencingBoundaryExtension(GeofencingZone zone, boolean entering) {}
