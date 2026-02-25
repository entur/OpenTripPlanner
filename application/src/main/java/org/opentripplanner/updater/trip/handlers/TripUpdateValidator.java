package org.opentripplanner.updater.trip.handlers;

import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ResolvedExistingTrip;
import org.opentripplanner.updater.trip.model.ResolvedNewTrip;

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
    Result<Void, UpdateError> validate(ResolvedExistingTrip resolvedUpdate);
  }

  /**
   * Validator for adding new trips.
   * Used for ADD_NEW_TRIP update type.
   */
  @FunctionalInterface
  public interface ForNewTrip {
    Result<Void, UpdateError> validate(ResolvedNewTrip resolvedUpdate);
  }
}
