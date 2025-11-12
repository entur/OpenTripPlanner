package org.opentripplanner.model.plan.walkstep.verticaltransportation;

import javax.annotation.Nullable;
import org.opentripplanner.service.streetdetails.model.Level;

/**
 * Represents information about a single use of vertical transportation equipment stored in
 * {@link org.opentripplanner.model.plan.walkstep.WalkStep}.
 */
public abstract sealed class VerticalTransportationUse
  permits ElevatorUse, EscalatorUse, StairsUse {

  @Nullable
  private final Level from;

  @Nullable
  private final Level to;

  private final VerticalDirection verticalDirection;

  public VerticalTransportationUse(
    @Nullable Level from,
    @Nullable Level to,
    VerticalDirection verticalDirection
  ) {
    this.from = from;
    this.to = to;
    this.verticalDirection = verticalDirection;
  }

  public Level from() {
    return this.from;
  }

  public Level to() {
    return this.to;
  }

  public VerticalDirection verticalDirection() {
    return this.verticalDirection;
  }
}
