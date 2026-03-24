package org.opentripplanner.ext.carpooling.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_EAST;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_NORTH;
import static org.opentripplanner.ext.carpooling.CarpoolTripTestData.createSimpleTrip;
import static org.opentripplanner.ext.carpooling.CarpoolTripTestData.createStopAt;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;

public class CarpoolTripBuilderTest {

  @Test
  void buildFromValues_fromId_buildToCorrectValues() {
    var startTime = ZonedDateTime.now();
    var endTime = ZonedDateTime.now().plusMinutes(45);
    var stop = createStopAt(1, OSLO_EAST);

    var builder = new CarpoolTripBuilder(new FeedScopedId("feed", "id"));
    var trip = builder
      .withAvailableSeats(2)
      .withProvider("UNIT")
      .withDeviationBudget(Duration.of(8, ChronoUnit.MINUTES))
      .withStartTime(startTime)
      .withEndTime(endTime)
      .withStops(List.of(stop))
      .buildFromValues();

    assertEquals(2, trip.availableSeats());
    assertEquals("UNIT", trip.provider());
    assertEquals(Duration.of(8, ChronoUnit.MINUTES), trip.deviationBudget());
    assertEquals(startTime, trip.startTime());
    assertEquals(endTime, trip.endTime());
    assertEquals(stop, trip.stops().getFirst());
  }

  @Test
  void buildFromValues_fromOriginal_buildToCorrectValues() {
    var original = createSimpleTrip(OSLO_EAST, OSLO_NORTH);

    var builder = new CarpoolTripBuilder(original);
    var trip = builder.buildFromValues();

    assertEquals(original.availableSeats(), trip.availableSeats());
    assertEquals(original.provider(), trip.provider());
    assertEquals(original.deviationBudget(), trip.deviationBudget());
    assertEquals(original.startTime(), trip.startTime());
    assertEquals(original.endTime(), trip.endTime());
    assertEquals(original.stops().getFirst(), trip.stops().getFirst());
    assertEquals(original.stops().getLast(), trip.stops().getLast());
  }

  @Test
  void buildFromValues_withPublicContactInformation_storesContactInfo() {
    var contact = new SimpleContactStructure("+4712345678", "https://example.com/book");
    var stop = createStopAt(1, OSLO_EAST);

    var trip = new CarpoolTripBuilder(new FeedScopedId("feed", "contact-test"))
      .withStartTime(ZonedDateTime.now())
      .withEndTime(ZonedDateTime.now().plusMinutes(30))
      .withStops(List.of(stop))
      .withPublicContactInformation(contact)
      .buildFromValues();

    assertNotNull(trip.publicContactInformation());
    assertEquals("+4712345678", trip.publicContactInformation().phoneNumber());
    assertEquals("https://example.com/book", trip.publicContactInformation().url());
  }

  @Test
  void buildFromValues_withNullableContactFields_allowsNulls() {
    var contactPhoneOnly = new SimpleContactStructure("+4712345678", null);
    var contactUrlOnly = new SimpleContactStructure(null, "https://example.com/book");
    var stop = createStopAt(1, OSLO_EAST);

    var tripWithPhone = new CarpoolTripBuilder(new FeedScopedId("feed", "phone-only"))
      .withStartTime(ZonedDateTime.now())
      .withEndTime(ZonedDateTime.now().plusMinutes(30))
      .withStops(List.of(stop))
      .withPublicContactInformation(contactPhoneOnly)
      .buildFromValues();

    assertEquals("+4712345678", tripWithPhone.publicContactInformation().phoneNumber());
    assertNull(tripWithPhone.publicContactInformation().url());

    var tripWithUrl = new CarpoolTripBuilder(new FeedScopedId("feed", "url-only"))
      .withStartTime(ZonedDateTime.now())
      .withEndTime(ZonedDateTime.now().plusMinutes(30))
      .withStops(List.of(stop))
      .withPublicContactInformation(contactUrlOnly)
      .buildFromValues();

    assertNull(tripWithUrl.publicContactInformation().phoneNumber());
    assertEquals("https://example.com/book", tripWithUrl.publicContactInformation().url());
  }

  @Test
  void buildFromValues_withoutPublicContactInformation_defaultsToNull() {
    var stop = createStopAt(1, OSLO_EAST);

    var trip = new CarpoolTripBuilder(new FeedScopedId("feed", "no-contact"))
      .withStartTime(ZonedDateTime.now())
      .withEndTime(ZonedDateTime.now().plusMinutes(30))
      .withStops(List.of(stop))
      .buildFromValues();

    assertNull(trip.publicContactInformation());
  }
}
