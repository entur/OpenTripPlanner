package org.opentripplanner.updater.trip.siri;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.utils.time.ServiceDateUtils;
import uk.org.siri.siri21.EstimatedVehicleJourney;

public class ServiceDateParser {

  private final EstimatedVehicleJourney journey;
  private final ZoneId timeZone;
  private final String feedId;
  private FeedScopedId tripOnServiceDateId;
  private LocalDate serviceDate;

  public ServiceDateParser(EstimatedVehicleJourney journey, ZoneId timeZone, String feedId) {
    this.journey = journey;
    this.timeZone = timeZone;
    this.feedId = feedId;
  }

  public ParsedServiceDate parse() {
    tripOnServiceDateId = resolveTripOnServiceDateId();
    serviceDate = resolveServiceDate();
    return new ParsedServiceDate(serviceDate, tripOnServiceDateId);
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

  @Nullable
  private LocalDate resolveServiceDate() {
    if (journey.getFramedVehicleJourneyRef() != null) {
      var dataFrameRef = journey.getFramedVehicleJourneyRef().getDataFrameRef();
      if (dataFrameRef != null) {
        try {
          return LocalDate.parse(dataFrameRef.getValue());
        } catch (Exception ignored) {}
      }
    }

    // if a DSJ id is present, this can be used to infer the service date in the applier stage.
    if (tripOnServiceDateId != null) {
      return null;
    }

    // as a last resort, infer the service date from the first call's aimed departure time
    // this may be incorrect for trips starting after midnight and registered on the previous
    // service date
    var datetime = CallWrapper.of(journey)
      .stream()
      .findFirst()
      .map(CallWrapper::getAimedDepartureTime)
      .orElse(null);

    if (datetime != null) {
      ZonedDateTime startOfService = ServiceDateUtils.asStartOfService(
        datetime.toInstant(),
        timeZone
      );
      return ServiceDateUtils.asServiceDay(startOfService);
    }

    return null;
  }

  public record ParsedServiceDate(LocalDate serviceDate, FeedScopedId tripOnServiceDateId) {
    public boolean isEmpty() {
      return serviceDate == null && tripOnServiceDateId == null;
    }
  }
}
