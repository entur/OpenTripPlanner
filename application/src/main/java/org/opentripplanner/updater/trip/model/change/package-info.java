/**
 * The change model: what will happen. A change is a command joined with the current state of the
 * transit model - the revision, modification, addition, removal or duplication that will happen.
 * A change validates itself on construction (an invalid change cannot exist) and applies itself
 * through {@code apply()}.
 * <p>
 * Changes are produced by the factories in {@code org.opentripplanner.updater.trip.factory} and
 * carried out by the domain services in {@code org.opentripplanner.updater.trip.service}. This
 * package depends only on the command model - never on the resolvers, the factories or the
 * domain services.
 */
package org.opentripplanner.updater.trip.model.change;
