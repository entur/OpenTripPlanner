package org.opentripplanner.model.plan.walkstep.verticaltransportation;

import javax.annotation.Nullable;
import org.opentripplanner.service.streetdecorator.model.Level;

/**
 * Represents information about inclined vertical transportation equipment stored in
 * {@WalkStep}.
 */
public abstract class InclinedVerticalTransportationUse implements VerticalTransportationUse {

  @Nullable
  private final Level from;

  private final VerticalDirection verticalDirection;

  @Nullable
  private final Level to;

  public InclinedVerticalTransportationUse(
    @Nullable Level from,
    VerticalDirection verticalDirection,
    @Nullable Level to
  ) {
    this.from = from;
    this.verticalDirection = verticalDirection;
    this.to = to;
  }

  public Level from() {
    return this.from;
  }

  public VerticalDirection verticalDirection() {
    return this.verticalDirection;
  }

  public Level to() {
    return this.to;
  }
}
