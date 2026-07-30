package org.opentripplanner.updater.trip.policy;

import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayInterpolator;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayInterpolator;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;

/**
 * Adapter over the (already polymorphic) forwards/backwards delay interpolators. Owns the two
 * format-divergent decisions: how to seed the builder (empty for propagating feeds, pre-filled
 * otherwise) and how to propagate delays after stop times are applied.
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

  /** Whether this configuration propagates delays (forward or backward). */
  public boolean propagatesDelays() {
    return (
      forwards != ForwardsDelayPropagationType.NONE ||
      backwards != BackwardsDelayPropagationType.NONE
    );
  }

  /**
   * Seed the real-time builder. When delay propagation is enabled, start with empty times so the
   * interpolators can fill them in; otherwise pre-fill with scheduled times (SIRI-style: all stops
   * have explicit times).
   */
  public RealTimeTripTimesBuilder initialBuilder(TripTimes scheduledTripTimes) {
    return propagatesDelays()
      ? scheduledTripTimes.createRealTimeWithoutScheduledTimes()
      : scheduledTripTimes.createRealTimeFromScheduledTimes();
  }

  /**
   * Apply forwards then backwards delay propagation to the builder.
   *
   * @return true if forwards interpolation changed any times (i.e. the trip has time updates).
   */
  public boolean propagate(RealTimeTripTimesBuilder builder) {
    boolean forwardsChanged = ForwardsDelayInterpolator.getInstance(forwards).interpolateDelay(
      builder
    );
    BackwardsDelayInterpolator.getInstance(backwards).propagateBackwards(builder);
    return forwardsChanged;
  }
}
