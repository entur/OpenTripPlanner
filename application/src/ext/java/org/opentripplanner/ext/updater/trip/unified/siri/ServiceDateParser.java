package org.opentripplanner.ext.updater.trip.unified.siri;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.TripUpdateType;
import org.opentripplanner.updater.trip.siri.CallWrapper;
import org.opentripplanner.updater.trip.siri.EstimatedVehicleJourneyWrapper;
import org.opentripplanner.updater.trip.siri.VehicleJourneyIdAndServiceDate;

public class ServiceDateParser {

  private final EstimatedVehicleJourneyWrapper journey;
  private final String feedId;
  private FeedScopedId tripOnServiceDateId;

  public ServiceDateParser(EstimatedVehicleJourneyWrapper journey, String feedId) {
    this.journey = journey;
    this.feedId = feedId;
  }

  public ParsedServiceDate parse() {
    tripOnServiceDateId = resolveTripOnServiceDateId();
    return resolveServiceDate();
  }

  /**
   * Resolve the TripOnServiceDate ID (dated service journey id) from the EstimatedVehicleJourney.
   * This is used when the SIRI message references a trip by its dated service journey id
   * rather than the underlying service journey id.
   */
  @Nullable
  private FeedScopedId resolveTripOnServiceDateId() {
    // The dated vehicle journey ref contains a TripOnServiceDate ID, not a Trip ID
    return journey
      .datedVehicleJourneyRef()
      .map(ref -> new FeedScopedId(feedId, ref))
      .orElse(null);
  }

  private ParsedServiceDate resolveServiceDate() {
    var serviceDate = journey
      .vehicleJourneyIdAndServiceDate()
      .map(VehicleJourneyIdAndServiceDate::serviceDate)
      .orElse(null);
    if (serviceDate != null) {
      return new ParsedServiceDate(serviceDate, tripOnServiceDateId, null);
    }

    // Always extract aimedDepartureTime as a fallback for service date resolution.
    // This is needed even when tripOnServiceDateId is present, because the ID may not
    // resolve to a valid NeTEx DatedServiceJourney (e.g. BNR numeric IDs).
    ZonedDateTime aimedDepartureTime = journey
      .calls()
      .stream()
      .findFirst()
      .map(CallWrapper::getAimedDepartureTime)
      .orElse(null);

    if (tripOnServiceDateId != null) {
      return new ParsedServiceDate(null, tripOnServiceDateId, aimedDepartureTime);
    }

    return new ParsedServiceDate(null, null, aimedDepartureTime);
  }

  /**
   * Result of parsing service date information from a SIRI message.
   * <p>
   * The service date can be determined in three ways:
   * <ol>
   *   <li>Explicitly from FramedVehicleJourneyRef.DataFrameRef</li>
   *   <li>By looking up TripOnServiceDate using tripOnServiceDateId (done in the change factory)</li>
   *   <li>By calculating from aimedDepartureTime using Trip's scheduled departure offset (done in the change factory)</li>
   * </ol>
   *
   * @param serviceDate The resolved service date, or null if deferred resolution is needed
   * @param tripOnServiceDateId The TripOnServiceDate ID for lookup, or null
   * @param aimedDepartureTime The aimed departure time for deferred resolution, or null
   */
  public record ParsedServiceDate(
    @Nullable LocalDate serviceDate,
    @Nullable FeedScopedId tripOnServiceDateId,
    @Nullable ZonedDateTime aimedDepartureTime
  ) {
    /**
     * Whether any of the three ways above is open to an update of the given type, so that a service
     * date can still be arrived at.
     * <p>
     * Way (2) is not open to an extra journey: its {@code DatedVehicleJourneyRef} names the dated
     * service journey being created rather than an existing one, so the lookup has nothing to find.
     * An extra journey therefore has to state its day outright or imply it through an aimed
     * departure time.
     */
    public boolean isResolvableFor(TripUpdateType updateType) {
      if (serviceDate != null || aimedDepartureTime != null) {
        return true;
      }
      return tripOnServiceDateId != null && updateType != TripUpdateType.ADD_NEW_TRIP;
    }
  }
}
