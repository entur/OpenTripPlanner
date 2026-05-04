package org.opentripplanner.service.vehiclerental.street;

import org.opentripplanner.service.vehiclerental.model.GeofencingZone;

/**
 * Marks a boundary-crossing edge for a geofencing zone. This is a data marker used by
 * state-based zone tracking in the routing algorithm — it does not enforce any restrictions
 * itself.
 *
 * @param zone the geofencing zone whose boundary this edge crosses
 * @param entering true if traversing the edge in its natural direction (fromv → tov) enters the
 *     zone; false if it exits
 */
public record GeofencingBoundaryExtension(GeofencingZone zone, boolean entering) {}
