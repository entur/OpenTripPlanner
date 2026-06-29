package org.opentripplanner.updater.trip.siri;

import static java.lang.Boolean.TRUE;
import static org.opentripplanner.model.PickDrop.CANCELLED;
import static org.opentripplanner.model.PickDrop.NONE;
import static org.opentripplanner.model.PickDrop.SCHEDULED;

import java.util.Optional;
import org.opentripplanner.model.PickDrop;
import uk.org.siri.siri21.ArrivalBoardingActivityEnumeration;
import uk.org.siri.siri21.CallStatusEnumeration;
import uk.org.siri.siri21.DepartureBoardingActivityEnumeration;

/**
 * Maps the SIRI status and boarding activity of a call to an OTP {@link PickDrop}, relative to the
 * scheduled value.
 * <p>
 * This is package-private on purpose: the SIRI enumerations are an implementation detail of
 * {@link CallWrapper}, which exposes only the resulting {@link PickDrop}.
 */
final class CallPickDropMapper {

  private CallPickDropMapper() {}

  /**
   * Map a call to a drop-off type for the stop arrival.
   * <p>
   * The SIRI ArrivalBoardingActivity carries less information than the pick drop type, so the value
   * is only changed when routability changes.
   *
   * @param plannedValue the current pick drop value on a stopTime
   * @return the mapped {@link PickDrop} type, empty if routability is not changed
   */
  static Optional<PickDrop> mapDropOffType(
    PickDrop plannedValue,
    Boolean isCancellation,
    CallStatusEnumeration arrivalStatus,
    ArrivalBoardingActivityEnumeration arrivalBoardingActivity
  ) {
    if (shouldBeCancelled(plannedValue, isCancellation, arrivalStatus)) {
      return Optional.of(CANCELLED);
    }

    if (arrivalBoardingActivity == null) {
      return Optional.empty();
    }

    return switch (arrivalBoardingActivity) {
      case ALIGHTING -> plannedValue.isNotRoutable() ? Optional.of(SCHEDULED) : Optional.empty();
      case NO_ALIGHTING -> Optional.of(NONE);
      case PASS_THRU -> Optional.of(CANCELLED);
    };
  }

  /**
   * Map a call to a pick-up type for the stop departure.
   * <p>
   * The SIRI DepartureBoardingActivity carries less information than the planned data, so the value
   * is only changed when routability changes.
   *
   * @param plannedValue the current pick drop value on a stopTime
   * @return the mapped {@link PickDrop} type, empty if routability is not changed
   */
  static Optional<PickDrop> mapPickUpType(
    PickDrop plannedValue,
    Boolean isCancellation,
    CallStatusEnumeration departureStatus,
    DepartureBoardingActivityEnumeration departureBoardingActivity
  ) {
    if (shouldBeCancelled(plannedValue, isCancellation, departureStatus)) {
      return Optional.of(CANCELLED);
    }

    if (departureBoardingActivity == null) {
      return Optional.empty();
    }

    return switch (departureBoardingActivity) {
      case BOARDING -> plannedValue.isNotRoutable() ? Optional.of(SCHEDULED) : Optional.empty();
      case NO_BOARDING -> Optional.of(NONE);
      case PASS_THRU -> Optional.of(CANCELLED);
    };
  }

  /**
   * Considers if PickDrop should be set to CANCELLED. If the existing PickDrop is non-routable, the
   * value is not changed.
   */
  private static boolean shouldBeCancelled(
    PickDrop plannedValue,
    Boolean isCancellation,
    CallStatusEnumeration callStatus
  ) {
    if (plannedValue.isNotRoutable()) {
      return false;
    }
    return TRUE.equals(isCancellation) || callStatus == CallStatusEnumeration.CANCELLED;
  }
}
