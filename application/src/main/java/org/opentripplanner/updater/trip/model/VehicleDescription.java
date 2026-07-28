package org.opentripplanner.updater.trip.model;

import javax.annotation.Nullable;
import org.opentripplanner.core.model.accessibility.Accessibility;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;

/**
 * What a real-time message says about the vehicle serving a trip: the GTFS-RT vehicle descriptor or
 * the SIRI VehicleRef.
 * <p>
 * Every update type may carry a vehicle description, and every message restates it in full - what a
 * message leaves out is unknown rather than unchanged, so nothing is carried over from the previous
 * message.
 */
public record VehicleDescription(
  @Nullable String vehicleId,
  @Nullable Accessibility wheelchairAccessibility
) {
  private static final VehicleDescription UNKNOWN = new VehicleDescription(null, null);

  /** The message says nothing about the vehicle. */
  public static VehicleDescription unknown() {
    return UNKNOWN;
  }

  public static VehicleDescription of(
    @Nullable String vehicleId,
    @Nullable Accessibility wheelchairAccessibility
  ) {
    if (vehicleId == null && wheelchairAccessibility == null) {
      return UNKNOWN;
    }
    return new VehicleDescription(vehicleId, wheelchairAccessibility);
  }

  /**
   * Apply what the message says about the vehicle to the real-time trip times being built. An
   * accessibility the message does not state is left to the trip it belongs to, so that an update
   * never overwrites the accessibility of a scheduled trip with "no information".
   */
  public void applyTo(RealTimeTripTimesBuilder builder) {
    builder.withVehicleId(vehicleId);
    if (wheelchairAccessibility != null) {
      builder.withWheelchairAccessibility(wheelchairAccessibility);
    }
  }
}
