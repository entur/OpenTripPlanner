package org.opentripplanner.ext.updater.trip.unified.model.command;

import java.time.LocalDate;
import java.util.Objects;
import org.opentripplanner.core.model.id.FeedScopedId;

/**
 * A reference to a dated trip that a created trip replaces. SIRI names a replaced dated vehicle
 * journey in one of two forms, and each form is looked up differently in the transit model:
 * <ul>
 *   <li>{@link DatedTripRef} - by the id of the dated trip itself: the primary VehicleJourneyRef
 *       carries a DatedServiceJourney id</li>
 *   <li>{@link TripOnDateRef} - by the pair (trip id, service date): an
 *       AdditionalVehicleJourneyRef is a framed ref, naming the ServiceJourney and the date it
 *       runs</li>
 * </ul>
 */
public sealed interface ReplacedTripReference {
  /**
   * Reference by the id of the dated trip itself (SIRI: a DatedServiceJourney id).
   */
  record DatedTripRef(FeedScopedId tripOnServiceDateId) implements ReplacedTripReference {
    public DatedTripRef {
      Objects.requireNonNull(tripOnServiceDateId);
    }
  }

  /**
   * Reference by the trip and the service date it runs (SIRI: a framed vehicle journey ref).
   */
  record TripOnDateRef(FeedScopedId tripId, LocalDate serviceDate) implements
    ReplacedTripReference {
    public TripOnDateRef {
      Objects.requireNonNull(tripId);
      Objects.requireNonNull(serviceDate);
    }
  }
}
