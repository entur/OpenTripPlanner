package org.opentripplanner.updater.trip.siri;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import uk.org.siri.siri21.EstimatedVehicleJourney;

public class ServiceDateParser {

  private final EstimatedVehicleJourney journey;
  private final String feedId;
  private FeedScopedId tripOnServiceDateId;

  public ServiceDateParser(EstimatedVehicleJourney journey, String feedId) {
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
    // journey.getDatedVehicleJourneyRef contains a TripOnServiceDate ID, not a Trip ID
    if (journey.getDatedVehicleJourneyRef() != null) {
      return new FeedScopedId(feedId, journey.getDatedVehicleJourneyRef().getValue());
    }
    return null;
  }

  private ParsedServiceDate resolveServiceDate() {
    if (journey.getFramedVehicleJourneyRef() != null) {
      var dataFrameRef = journey.getFramedVehicleJourneyRef().getDataFrameRef();
      if (dataFrameRef != null) {
        try {
          return new ParsedServiceDate(
            LocalDate.parse(dataFrameRef.getValue()),
            tripOnServiceDateId,
            null
          );
        } catch (Exception ignored) {}
      }
    }

    // if a DSJ id is present, this can be used to infer the service date in the applier stage.
    if (tripOnServiceDateId != null) {
      return new ParsedServiceDate(null, tripOnServiceDateId, null);
    }

    // Store the aimed departure time for deferred resolution in the applier.
    // The applier can calculate the correct service date using the Trip's scheduled
    // departure time to handle overnight trips correctly.
    var aimedDepartureTime = CallWrapper.of(journey)
      .stream()
      .findFirst()
      .map(CallWrapper::getAimedDepartureTime)
      .orElse(null);

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
