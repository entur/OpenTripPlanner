package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import javax.annotation.Nullable;

/**
 * Format-independent representation of a trip update parsed from either SIRI-ET or GTFS-RT.
 * This sealed interface is the common model that both parsers produce and the applier consumes.
 * <p>
 * The type hierarchy mirrors the resolver structure:
 * <ul>
 *   <li>{@link ParsedExistingTripUpdate} → ExistingTripResolver (UPDATE_EXISTING, MODIFY_TRIP)</li>
 *   <li>{@link ParsedAddNewTrip} → NewTripResolver (ADD_NEW_TRIP)</li>
 *   <li>{@link ParsedTripRemoval} → TripRemovalResolver (CANCEL_TRIP, DELETE_TRIP)</li>
 * </ul>
 */
public sealed interface ParsedTripUpdate
  permits ParsedExistingTripUpdate, ParsedAddNewTrip, ParsedTripRemoval {
  TripReference tripReference();

  @Nullable
  LocalDate serviceDate();

  @Nullable
  ZonedDateTime aimedDepartureTime();

  @Nullable
  String dataSource();

  /**
   * Returns true if the service date needs to be calculated using the Trip's
   * scheduled departure time offset (for overnight trip handling).
   */
  default boolean needsDeferredServiceDateResolution() {
    return (
      serviceDate() == null &&
      !tripReference().hasTripOnServiceDateId() &&
      aimedDepartureTime() != null
    );
  }

  /**
   * Validate that a service date can be resolved from the available fields.
   * Call from constructors to fail fast when no resolution strategy is available.
   */
  static void validateServiceDateAvailable(
    TripReference tripReference,
    @Nullable LocalDate serviceDate,
    @Nullable ZonedDateTime aimedDepartureTime
  ) {
    if (
      serviceDate == null && !tripReference.hasTripOnServiceDateId() && aimedDepartureTime == null
    ) {
      throw new IllegalArgumentException(
        "serviceDate must not be null when neither tripOnServiceDateId nor aimedDepartureTime is provided for deferred resolution"
      );
    }
  }
}
