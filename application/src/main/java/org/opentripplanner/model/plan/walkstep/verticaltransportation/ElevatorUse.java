package org.opentripplanner.model.plan.walkstep.verticaltransportation;

import javax.annotation.Nullable;

/**
 * Represents information about an elevator stored in
 * {@link org.opentripplanner.model.plan.walkstep.WalkStep}.
 */
public record ElevatorUse(@Nullable String toLevelName) implements VerticalTransportationUse {}
