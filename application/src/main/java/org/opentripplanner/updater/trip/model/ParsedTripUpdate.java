package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.i18n.I18NString;

/**
 * Format-independent representation of a trip update parsed from either SIRI-ET or GTFS-RT.
 * This sealed interface is the common model that both parsers produce and the apply stage consumes.
 * <p>
 * The type hierarchy mirrors the resolver structure:
 * <ul>
 *   <li>{@link ExistingTripUpdate} → ExistingTripResolver (UPDATE_EXISTING, MODIFY_TRIP)</li>
 *   <li>{@link TripAddition} → NewTripResolver (ADD_NEW_TRIP)</li>
 *   <li>{@link TripRemoval} → TripRemovalResolver (CANCEL_TRIP, DELETE_TRIP)</li>
 *   <li>{@link TripDuplication} → DuplicateTripResolver (DUPLICATE_TRIP)</li>
 * </ul>
 */
public sealed interface ParsedTripUpdate
  permits ExistingTripUpdate, TripAddition, TripRemoval, TripDuplication {
  TripReference tripReference();

  @Nullable
  LocalDate serviceDate();

  @Nullable
  ZonedDateTime aimedDepartureTime();

  @Nullable
  String dataSource();

  /**
   * What the update says about the vehicle operating the trip. Propagated to the real-time trip
   * times.
   */
  default VehicleDescription vehicleDescription() {
    return VehicleDescription.unknown();
  }

  /**
   * The headsign the trip displays today, if the update states one (GTFS-RT
   * {@code TripProperties.trip_headsign}). Propagated to the real-time trip times, whatever the
   * update type - it is not creation data, and an update that leaves it out keeps the headsign of
   * the trip it updates.
   */
  @Nullable
  default I18NString tripHeadsign() {
    return null;
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
