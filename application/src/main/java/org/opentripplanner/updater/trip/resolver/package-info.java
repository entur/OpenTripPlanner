/**
 * Turns a reference into the entity it denotes: a trip id into a trip, a stop reference into a
 * stop location, message fields into a service date - including fuzzy trip matching when a direct
 * lookup fails. Resolver = reference to entity; the factories in
 * {@code org.opentripplanner.updater.trip.factory} cover command to change.
 */
package org.opentripplanner.updater.trip.resolver;
