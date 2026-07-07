package org.opentripplanner.updater.trip.handlers;

import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ResolvedExistingTrip;
import org.opentripplanner.updater.trip.model.ResolvedTripCreation;

/**
 * Validator interfaces for different types of trip updates.
 * <p>
 * Validators run between resolution and handling in
 * {@link org.opentripplanner.updater.trip.DefaultTripUpdateApplier#apply},
 * checking preconditions on the resolved data before mutation begins.
 */
public final class TripUpdateValidator {

  private TripUpdateValidator() {}

  /**
   * Validator for updates to existing scheduled trips.
   * Used for UPDATE_EXISTING and MODIFY_TRIP update types.
   */
  @FunctionalInterface
  public interface ForExistingTrip {
    void validate(ResolvedExistingTrip resolvedUpdate) throws UpdateException;
  }

  /**
   * Validator for creating new trips.
   * Used for ADD_NEW_TRIP updates resolved to a trip creation. Updates to previously added
   * trips are not validated here, matching the legacy behaviour.
   */
  @FunctionalInterface
  public interface ForNewTrip {
    void validate(ResolvedTripCreation resolvedUpdate) throws UpdateException;
  }
}
