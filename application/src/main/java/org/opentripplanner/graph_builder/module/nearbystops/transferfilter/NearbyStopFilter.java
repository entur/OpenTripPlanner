package org.opentripplanner.graph_builder.module.nearbystops.transferfilter;

import java.util.Collection;
import org.opentripplanner.routing.graphfinder.NearbyStop;

interface NearbyStopFilter {
  /**
   * Find all unique nearby stops that are the closest stop on some trip pattern or flex trip. Note
   * that the result will include the origin vertex if it is an instance of StopVertex. This is
   * intentional: we don't want to return the next stop down the line for trip patterns that pass
   * through the origin vertex. Taking the patterns into account reduces the number of transfers
   * significantly compared to simple traverse-duration-constrained all-to-all stop linkage.
   */
  Collection<NearbyStop> filterToStops(
    Collection<NearbyStop> nearbyStops,
    boolean reverseDirection
  );
}
