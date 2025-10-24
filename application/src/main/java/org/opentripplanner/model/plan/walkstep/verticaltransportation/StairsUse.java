package org.opentripplanner.model.plan.walkstep.verticaltransportation;

import javax.annotation.Nullable;
import org.opentripplanner.service.streetdetails.model.Level;

/**
 * Represents information about a single use of a set of stairs related to
 * {@link org.opentripplanner.model.plan.walkstep.WalkStep}.
 */
public class StairsUse extends InclinedVerticalTransportationUse {

  public StairsUse(@Nullable Level from, VerticalDirection verticalDirection, @Nullable Level to) {
    super(from, verticalDirection, to);
  }
}
