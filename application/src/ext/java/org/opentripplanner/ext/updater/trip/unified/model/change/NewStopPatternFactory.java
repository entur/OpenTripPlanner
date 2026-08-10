package org.opentripplanner.ext.updater.trip.unified.model.change;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.ext.updater.trip.unified.model.ServiceTime;
import org.opentripplanner.ext.updater.trip.unified.model.command.AbsoluteTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.TimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.policy.PickDropPolicy;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.model.StopTime;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds a new {@link StopPattern} and the matching {@link StopTime}s from resolved stop time
 * updates, for added and modified trips.
 */
public final class NewStopPatternFactory {

  private static final Logger LOG = LoggerFactory.getLogger(NewStopPatternFactory.class);

  /** Result of building a new stop pattern. */
  public record StopTimesAndPattern(List<StopTime> stopTimes, StopPattern stopPattern) {}

  private NewStopPatternFactory() {}

  /**
   * Build a new stop pattern and stop times from resolved stop time updates.
   * This creates stop times with scheduled times from the updates.
   *
   * @param trip The trip being modified or created
   * @param stopTimeUpdates The resolved stop time updates (with pre-resolved stops)
   * @param pickDrop Policy deciding what a cancelled call does to boarding at its stop
   * @return stop times and pattern
   * @throws UpdateException if stops cannot be resolved
   */
  public static StopTimesAndPattern buildNewStopPattern(
    Trip trip,
    List<ResolvedStopTimeUpdate> stopTimeUpdates,
    PickDropPolicy pickDrop
  ) {
    var stopTimes = new ArrayList<StopTime>();

    for (int i = 0; i < stopTimeUpdates.size(); i++) {
      var stopUpdate = stopTimeUpdates.get(i);

      // Use the pre-resolved stop the call reports it is at
      StopLocation stop = stopUpdate.referencedStop();
      if (stop == null) {
        LOG.debug("Unknown stop in pattern: {}", stopUpdate.stopReference());
        throw UpdateException.of(trip.getId(), UpdateErrorType.UNKNOWN_STOP, i);
      }

      // Create stop time. A message that numbers its calls (GTFS-RT stop_sequence) keeps its own
      // numbering, so a later update can find the call again by the sequence it was created with.
      // A format that matches calls by position (SIRI-ET) numbers them by position.
      var stopTime = new StopTime();
      stopTime.setTrip(trip);
      stopTime.setStop(stop);
      var stopSequence = stopUpdate.stopSequence();
      stopTime.setStopSequence(stopSequence != null ? stopSequence.value() : i);

      // Resolve times
      boolean isFirstStop = (i == 0);
      boolean isLastStop = (i == stopTimeUpdates.size() - 1);

      // Get departure time first (needed for arrival fallback)
      ServiceTime departureTime = aimedTime(stopUpdate.departureUpdate());

      // Get arrival time - use scheduled time if available, otherwise fallback to departure
      // This matches StopTimesMapper: aimedArrivalTime ?? aimedDepartureTime
      ServiceTime arrivalTime = aimedTime(stopUpdate.arrivalUpdate());
      if (arrivalTime != null) {
        stopTime.setArrivalTime(arrivalTime.secondsPastMidnight());
      } else if (departureTime != null) {
        // Fallback: use departure time as arrival (matches old StopTimesMapper logic)
        stopTime.setArrivalTime(departureTime.secondsPastMidnight());
      } else if (!isFirstStop) {
        // Last resort: propagate from previous stop
        var prevStopTime = stopTimes.get(i - 1);
        stopTime.setArrivalTime(prevStopTime.getDepartureTime());
      }

      // Set departure time
      if (departureTime != null) {
        stopTime.setDepartureTime(departureTime.secondsPastMidnight());
      } else if (stopTime.isArrivalTimeSet()) {
        // Fallback: use arrival time as departure (matches old StopTimesMapper logic)
        stopTime.setDepartureTime(stopTime.getArrivalTime());
      }

      // Handle pickup/dropoff
      if (stopUpdate.pickup() != null) {
        stopTime.setPickupType(stopUpdate.pickup());
      } else {
        stopTime.setPickupType(isLastStop ? PickDrop.NONE : PickDrop.SCHEDULED);
      }

      if (stopUpdate.dropoff() != null) {
        stopTime.setDropOffType(stopUpdate.dropoff());
      } else {
        stopTime.setDropOffType(isFirstStop ? PickDrop.NONE : PickDrop.SCHEDULED);
      }

      // Handle headsign
      if (stopUpdate.stopHeadsign() != null) {
        stopTime.setStopHeadsign(stopUpdate.stopHeadsign());
      }

      // Handle a cancelled call, per end: a call cancelled as a whole cancels both of its ends, a
      // SIRI-ET arrival or departure status cancels one. A new pattern has no scheduled timetable, so
      // the value the call has been given so far - the first/last stop default, or what the call
      // itself reports - takes the place of the scheduled one, the way the legacy SIRI
      // StopTimesMapper reconciles it.
      if (stopUpdate.isPickupCancelled()) {
        var cancelledPickup = pickDrop.effectiveWhenCancelled(stopTime.getPickupType());
        if (cancelledPickup != null) {
          stopTime.setPickupType(cancelledPickup);
        }
      }
      if (stopUpdate.isDropoffCancelled()) {
        var cancelledDropoff = pickDrop.effectiveWhenCancelled(stopTime.getDropOffType());
        if (cancelledDropoff != null) {
          stopTime.setDropOffType(cancelledDropoff);
        }
      }

      stopTimes.add(stopTime);
    }

    var stopPattern = new StopPattern(stopTimes);
    return new StopTimesAndPattern(stopTimes, stopPattern);
  }

  /**
   * The time to place a stop of a new pattern at, or {@code null} if the update does not carry
   * one. A new pattern has no scheduled timetable, so a delay-based update - which is only
   * meaningful relative to such a timetable - contributes nothing here and the caller falls back
   * to the neighbouring times.
   */
  @Nullable
  private static ServiceTime aimedTime(@Nullable TimeUpdate update) {
    return update instanceof AbsoluteTimeUpdate absolute ? absolute.aimedTimeOrActual() : null;
  }
}
