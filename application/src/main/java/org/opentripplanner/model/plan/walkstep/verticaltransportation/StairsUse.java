package org.opentripplanner.model.plan.walkstep.verticaltransportation;

import javax.annotation.Nullable;
import org.opentripplanner.service.streetdecorator.model.Level;

/**
 * Represents information about a set of stairs stored in
 * {@link org.opentripplanner.model.plan.walkstep.WalkStep}.
 */
public class StairsUse extends InclinedVerticalTransportationUse {

  public StairsUse(@Nullable Level from, VerticalDirection verticalDirection, @Nullable Level to) {
    super(from, verticalDirection, to);
  }
}
