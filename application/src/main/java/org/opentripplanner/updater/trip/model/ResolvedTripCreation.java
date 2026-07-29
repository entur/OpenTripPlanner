package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.util.List;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;

/**
 * Resolved data for creating a brand new trip that does not exist in the transit model,
 * neither in the scheduled data nor as a previously added real-time trip.
 * <p>
 * Used by {@link org.opentripplanner.updater.trip.TripCreator}.
 */
public final class ResolvedTripCreation extends ResolvedNewTrip {

  public ResolvedTripCreation(
    TripAddition parsedUpdate,
    LocalDate serviceDate,
    List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates
  ) {
    super(parsedUpdate, serviceDate, resolvedStopTimeUpdates);
    validate();
  }

  /**
   * A trip can only be created from a journey that calls at least twice, and - in FAIL mode - only
   * from calls at stops the transit model knows.
   * <p>
   * IGNORE-mode filtering and the minimum-stop check on the filtered calls stay in the
   * {@link org.opentripplanner.updater.trip.TripCreator}: they judge the outcome of a
   * transformation, not the message as it arrived.
   *
   * @throws UpdateException if the message cannot describe a trip
   */
  private void validate() {
    var calls = stopTimeUpdates();

    if (formatPolicy().unknownStop().failOnUnknownStop()) {
      for (int i = 0; i < calls.size(); i++) {
        if (calls.get(i).stop() == null) {
          throw UpdateException.of(tripId(), UpdateErrorType.UNKNOWN_STOP, i);
        }
      }
    }

    if (calls.size() < 2) {
      throw UpdateException.of(tripId(), UpdateErrorType.TOO_FEW_STOPS);
    }
  }

  @Override
  public String toString() {
    return (
      "ResolvedTripCreation{" + "tripId=" + tripId() + ", serviceDate=" + serviceDate() + '}'
    );
  }
}
