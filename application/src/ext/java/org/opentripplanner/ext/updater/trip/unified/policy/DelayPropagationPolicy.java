package org.opentripplanner.ext.updater.trip.unified.policy;

import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayInterpolator;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayInterpolator;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;

/**
 * Adapter over the (already polymorphic) forwards/backwards delay interpolators: how to fill in the
 * times of the stops the message did not report on, once the ones it did report on are applied.
 * Which times are there to be filled in is decided by {@link UnreportedTimePolicy}, and a stop no
 * interpolator reaches is what makes an incomplete update fail to build.
 * <p>
 * Modelled as a record so two policies built from the same propagation types compare equal (used
 * by {@link FormatPolicy} equality).
 */
public record DelayPropagationPolicy(
  ForwardsDelayPropagationType forwards,
  BackwardsDelayPropagationType backwards
) {
  public static DelayPropagationPolicy of(
    ForwardsDelayPropagationType forwards,
    BackwardsDelayPropagationType backwards
  ) {
    return new DelayPropagationPolicy(forwards, backwards);
  }

  /** Apply forwards then backwards delay propagation to the builder. */
  public void propagate(RealTimeTripTimesBuilder builder) {
    ForwardsDelayInterpolator.getInstance(forwards).interpolateDelay(builder);
    BackwardsDelayInterpolator.getInstance(backwards).propagateBackwards(builder);
  }
}
