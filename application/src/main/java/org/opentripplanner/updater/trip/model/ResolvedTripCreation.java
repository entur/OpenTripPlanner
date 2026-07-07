package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Resolved data for creating a brand new trip that does not exist in the transit model,
 * neither in the scheduled data nor as a previously added real-time trip.
 * <p>
 * Used by {@link org.opentripplanner.updater.trip.handlers.AddNewTripHandler}.
 */
public final class ResolvedTripCreation extends ResolvedNewTrip {

  public ResolvedTripCreation(
    ParsedAddNewTrip parsedUpdate,
    LocalDate serviceDate,
    List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates
  ) {
    super(parsedUpdate, serviceDate, resolvedStopTimeUpdates);
  }

  @Override
  public String toString() {
    return (
      "ResolvedTripCreation{" +
      "tripId=" +
      tripCreationInfo().tripId() +
      ", serviceDate=" +
      serviceDate() +
      '}'
    );
  }
}
