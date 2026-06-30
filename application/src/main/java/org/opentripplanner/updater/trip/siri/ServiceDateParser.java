package org.opentripplanner.updater.trip.siri;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;

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
    if (journey.datedVehicleJourneyRef() != null) {
      return new FeedScopedId(feedId, journey.datedVehicleJourneyRef());
    }
    return null;
  }

  private ParsedServiceDate resolveServiceDate() {
    var vehicleJourneyIdAndServiceDate = journey.vehicleJourneyIdAndServiceDate();
    if (
      vehicleJourneyIdAndServiceDate != null && vehicleJourneyIdAndServiceDate.serviceDate() != null
    ) {
      try {
        return new ParsedServiceDate(
          LocalDate.parse(vehicleJourneyIdAndServiceDate.serviceDate()),
          tripOnServiceDateId,
          null
        );
      } catch (Exception ignored) {}
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
   *   <li>By looking up TripOnServiceDate using tripOnServiceDateId (done in applier)</li>
   *   <li>By calculating from aimedDepartureTime using Trip's scheduled departure offset (done in applier)</li>
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
    public boolean isEmpty() {
      return serviceDate == null && tripOnServiceDateId == null && aimedDepartureTime == null;
    }
  }
}
