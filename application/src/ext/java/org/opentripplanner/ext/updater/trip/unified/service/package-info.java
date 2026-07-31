/**
 * The domain services that carry changes out. Each service supplies the shared resources of the
 * domain (deduplicator, trip-pattern cache, removal semantics), drives the change's
 * {@code apply()} and translates domain validation failures into update errors.
 */
package org.opentripplanner.ext.updater.trip.unified.service;
