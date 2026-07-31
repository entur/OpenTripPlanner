package org.opentripplanner.ext.updater.trip.unified.policy;

import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.transit.model.timetable.TripTimes;

/**
 * What the real-time times of an existing trip start out as, before the calls of the message are
 * applied on top of them. Each format binds the matching policy constant once at the boundary (see
 * {@link FormatPolicy}).
 * <p>
 * This is also how an incomplete update is rejected. A time left unset is not an error in itself -
 * the delay interpolators of {@link DelayPropagationPolicy} are given their chance to fill it in -
 * but whatever is still unset when the trip times are built makes the build fail with
 * {@code MISSING_ARRIVAL_TIME}, which is what both propagation types promise when they are switched
 * off. There is deliberately no explicit check for completeness anywhere: this is the mechanism the
 * legacy updaters have always used, and reproducing it is safer than re-deriving it.
 */
public sealed interface UnreportedTimePolicy
  permits UnreportedTimePolicy.AimedTime, UnreportedTimePolicy.NoTime {
  /**
   * The builder the real-time times of the trip are accumulated into.
   *
   * @param scheduledTripTimes the times the trip is scheduled to run at today
   */
  RealTimeTripTimesBuilder seedTimes(TripTimes scheduledTripTimes);

  /** SIRI-ET: a call the message does not time runs to plan. */
  UnreportedTimePolicy AIMED_TIME = new AimedTime();

  /** GTFS-RT: a call the message does not time has no time until an interpolator gives it one. */
  UnreportedTimePolicy NO_TIME = new NoTime();

  /**
   * SIRI-ET: the trip starts out running to plan, and the message moves the calls it reports on.
   * A SIRI-ET message describes a journey rather than a set of corrections, so a call it leaves
   * alone is a call that keeps its aimed times. Legacy seeds the same way, in
   * {@code ModifiedTripBuilder}.
   */
  final class AimedTime implements UnreportedTimePolicy {

    @Override
    public RealTimeTripTimesBuilder seedTimes(TripTimes scheduledTripTimes) {
      return scheduledTripTimes.createRealTimeFromScheduledTimes();
    }
  }

  /**
   * GTFS-RT: the trip starts out with no times at all, whatever the configured delay propagation.
   * Every stop must get its time from the message or from an interpolator, and a feed that leaves a
   * gap the configuration does not allow anyone to fill has sent an update OTP cannot publish.
   * Legacy seeds the same way, in {@code TripTimesUpdater}.
   */
  final class NoTime implements UnreportedTimePolicy {

    @Override
    public RealTimeTripTimesBuilder seedTimes(TripTimes scheduledTripTimes) {
      return scheduledTripTimes.createRealTimeWithoutScheduledTimes();
    }
  }
}
