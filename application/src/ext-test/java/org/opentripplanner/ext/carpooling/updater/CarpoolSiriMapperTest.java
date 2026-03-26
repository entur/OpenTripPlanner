package org.opentripplanner.ext.carpooling.updater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.ext.carpooling.CarpoolEstimatedVehicleJourneyData.arrivalIsAfterDepartureTime;
import static org.opentripplanner.ext.carpooling.CarpoolEstimatedVehicleJourneyData.lessThanTwoStops;
import static org.opentripplanner.ext.carpooling.CarpoolEstimatedVehicleJourneyData.minimalCompleteJourney;
import static org.opentripplanner.ext.carpooling.CarpoolEstimatedVehicleJourneyData.minimalCompleteJourneyWithPolygon;
import static org.opentripplanner.ext.carpooling.CarpoolEstimatedVehicleJourneyData.stopTimesAreOutOfOrder;
import static org.opentripplanner.ext.carpooling.CarpoolEstimatedVehicleJourneyData.tripHasAimedTimesOnly;
import static org.opentripplanner.ext.carpooling.CarpoolEstimatedVehicleJourneyData.journeyWithBookingExtensions;
import static org.opentripplanner.ext.carpooling.CarpoolEstimatedVehicleJourneyData.journeyWithPublicContact;
import static org.opentripplanner.ext.carpooling.CarpoolEstimatedVehicleJourneyData.tripHasExpectedTimesOnly;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedVehicleJourney;

public class CarpoolSiriMapperTest {

  private final CarpoolSiriMapper mapper = new CarpoolSiriMapper();

  @Test
  void mapSiriToCarpoolTrip_arrivalIsAfterDepartureTime_trowsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () ->
      mapper.mapSiriToCarpoolTrip(arrivalIsAfterDepartureTime())
    );
  }

  @Test
  void mapSiriToCarpoolTrip_lessThanTwoStops_trowsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () ->
      mapper.mapSiriToCarpoolTrip(lessThanTwoStops())
    );
  }

  @Test
  void mapSiriToCarpoolTrip_minimalData_mapsOk() {
    var journey = minimalCompleteJourney();
    var mapped = mapper.mapSiriToCarpoolTrip(journey);

    var expectedStartTime = journey
      .getEstimatedCalls()
      .getEstimatedCalls()
      .stream()
      .findFirst()
      .map(EstimatedCall::getAimedDepartureTime)
      .orElseThrow();
    var expectedEndTime = journey
      .getEstimatedCalls()
      .getEstimatedCalls()
      .stream()
      .reduce((a, b) -> b)
      .map(EstimatedCall::getAimedArrivalTime)
      .orElseThrow();
    assertEquals(expectedStartTime, mapped.startTime());
    assertEquals(expectedEndTime, mapped.endTime());

    var startName = journey
      .getEstimatedCalls()
      .getEstimatedCalls()
      .getFirst()
      .getStopPointNames()
      .getFirst()
      .getValue();
    var endName = journey
      .getEstimatedCalls()
      .getEstimatedCalls()
      .getLast()
      .getStopPointNames()
      .getFirst()
      .getValue();
    assertEquals("First stop", startName);
    assertEquals("Last stop", endName);
  }

  @Test
  void mapSiriToCarpoolTrip_minimalDataUsingPolygonStops_mapsOk() {
    var journey = minimalCompleteJourneyWithPolygon();
    var mapped = mapper.mapSiriToCarpoolTrip(journey);

    var firstStop = mapped.stops().getFirst();
    var lastStop = mapped.stops().getLast();

    assertNotNull(firstStop.getCoordinate());
    assertNotNull(lastStop.getCoordinate());
  }

  @Test
  void mapSiriToCarpoolTrip_tripHasOnlyAimedTimes_mapsOk() {
    var journey = tripHasAimedTimesOnly();
    var mapped = mapper.mapSiriToCarpoolTrip(journey);

    assertNotNull(mapped.startTime());
    assertNotNull(mapped.endTime());
  }

  @Test
  void mapSiriToCarpoolTrip_tripHasOnlyExpectedTimes_mapsOk() {
    var journey = tripHasExpectedTimesOnly();
    var mapped = mapper.mapSiriToCarpoolTrip(journey);

    assertNotNull(mapped.startTime());
    assertNotNull(mapped.endTime());
  }

  @Test
  void mapSiriToCarpoolTrip_stopTimesAreOutOfOrder_trowsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () ->
      mapper.mapSiriToCarpoolTrip(stopTimesAreOutOfOrder())
    );
  }

  @Test
  void mapSiriToCarpoolTrip_withPublicContact_mapsContactInformation() {
    var journey = journeyWithPublicContact("+4712345678", "https://example.com/book");
    var mapped = mapper.mapSiriToCarpoolTrip(journey);

    assertNotNull(mapped.publicContactInformation());
    assertEquals("+4712345678", mapped.publicContactInformation().phoneNumber());
    assertEquals("https://example.com/book", mapped.publicContactInformation().url());
  }

  @Test
  void mapSiriToCarpoolTrip_withoutPublicContact_contactInformationIsNull() {
    var journey = minimalCompleteJourney();
    var mapped = mapper.mapSiriToCarpoolTrip(journey);

    assertNull(mapped.publicContactInformation());
  }

  @Test
  void mapSiriToCarpoolTrip_withBookingExtensions_mapsBookingInfo() {
    var journey = journeyWithBookingExtensions("PT30M", "Book at least 30 minutes in advance");
    var mapped = mapper.mapSiriToCarpoolTrip(journey);

    assertEquals(Duration.ofMinutes(30), mapped.minimumBookingNotice().orElse(null));
    assertEquals("Book at least 30 minutes in advance", mapped.bookingMessage());
  }

  @Test
  void mapSiriToCarpoolTrip_withOnlyMinimumBookingNotice_mapsNoticeOnly() {
    var journey = journeyWithBookingExtensions("PT1H", null);
    var mapped = mapper.mapSiriToCarpoolTrip(journey);

    assertEquals(Duration.ofHours(1), mapped.minimumBookingNotice().orElse(null));
    assertNull(mapped.bookingMessage());
  }

  @Test
  void mapSiriToCarpoolTrip_withOnlyBookingMessage_mapsMessageOnly() {
    var journey = journeyWithBookingExtensions(null, "Please book in advance");
    var mapped = mapper.mapSiriToCarpoolTrip(journey);

    assertTrue(mapped.minimumBookingNotice().isEmpty());
    assertEquals("Please book in advance", mapped.bookingMessage());
  }

  @Test
  void mapSiriToCarpoolTrip_withoutExtensions_bookingFieldsAreEmpty() {
    var journey = minimalCompleteJourney();
    var mapped = mapper.mapSiriToCarpoolTrip(journey);

    assertTrue(mapped.minimumBookingNotice().isEmpty());
    assertNull(mapped.bookingMessage());
  }

  private static String toXml(EstimatedVehicleJourney journey) throws Exception {
    var context = jakarta.xml.bind.JAXBContext.newInstance(EstimatedVehicleJourney.class);
    var marshaller = context.createMarshaller();
    marshaller.setProperty(jakarta.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT, true);
    var sw = new java.io.StringWriter();
    marshaller.marshal(journey, sw);
    return sw.toString();
  }
}
