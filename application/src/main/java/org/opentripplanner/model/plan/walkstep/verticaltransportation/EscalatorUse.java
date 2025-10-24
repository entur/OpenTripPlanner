package org.opentripplanner.model.plan.walkstep.verticaltransportation;

import javax.annotation.Nullable;
import org.opentripplanner.service.streetdetails.model.Level;

/**
 * Represents information about an escalator stored in
 * {@link org.opentripplanner.model.plan.walkstep.WalkStep}.
 */
public class EscalatorUse extends InclinedVerticalTransportationUse {

  public EscalatorUse(
    @Nullable Level from,
    VerticalDirection verticalDirection,
    @Nullable Level to
  ) {
    super(from, verticalDirection, to);
  }
}
