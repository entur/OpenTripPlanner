package org.opentripplanner.ext.carpooling.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.opentripplanner.ext.carpooling.CarpoolBookingUrlTestData.expectedAugmentedUrl;

import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.transit.model.organization.ContactInfo;
import org.opentripplanner.transit.model.timetable.booking.BookingMethod;

/**
 * Unit tests for {@link CarpoolItineraryMapper}.
 */
class CarpoolItineraryMapperTest {

  private static final ZonedDateTime TRIP_START = ZonedDateTime.of(
    2026,
    5,
    13,
    8,
    30,
    0,
    0,
    ZoneOffset.UTC
  );

  private static final WgsCoordinate PICKUP = new WgsCoordinate(59.910000, 10.750000);
  private static final WgsCoordinate DROPOFF = new WgsCoordinate(59.920000, 10.760000);

  @Test
  void nullContact_returnsNull() {
    assertNull(CarpoolItineraryMapper.toBookingInfo(null, TRIP_START, PICKUP, DROPOFF));
  }

  /**
   * A contact that carries neither a phone number nor a booking URL has no actionable booking
   * method, so {@code toBookingInfo} must return {@code null} rather than a {@code BookingInfo}
   * with an empty {@code bookingMethods} set — otherwise the non-null return would
   * misleadingly suggest the leg is bookable. Consumers therefore may treat a non-null return
   * as "this leg has at least one booking method."
   */
  @Test
  void contactWithNeitherPhoneNorUrl_returnsNull() {
    var contact = ContactInfo.of().withContactPerson("Alice").build();

    assertNull(CarpoolItineraryMapper.toBookingInfo(contact, TRIP_START, PICKUP, DROPOFF));
  }

  @Test
  void phoneOnly_addsCallOfficeAndPreservesContactUnchanged() {
    var contact = ContactInfo.of().withPhoneNumber("+4712345678").build();

    var info = CarpoolItineraryMapper.toBookingInfo(contact, TRIP_START, PICKUP, DROPOFF);

    assertNotNull(info);
    assertEquals(EnumSet.of(BookingMethod.CALL_OFFICE), info.bookingMethods());
    assertEquals(contact, info.getContactInfo());
  }

  @Test
  void urlOnly_addsOnlineAndAppendsPickupAndDropoffCoordinatesToUrl() {
    var contact = ContactInfo.of().withBookingUrl("https://book.example.com").build();

    var info = CarpoolItineraryMapper.toBookingInfo(contact, TRIP_START, PICKUP, DROPOFF);

    assertNotNull(info);
    assertEquals(EnumSet.of(BookingMethod.ONLINE), info.bookingMethods());
    assertEquals(
      expectedAugmentedUrl("https://book.example.com", PICKUP, DROPOFF),
      info.getContactInfo().getBookingUrl()
    );
  }

  /**
   * Guards against the easy bug of unconditionally appending {@code ?from_coordinate=…}, which
   * would yield an invalid double-{@code ?} URL when the provider's booking URL already carries
   * its own query string (e.g. tracking parameters). The coordinate parameters must be appended
   * with {@code &} in that case.
   */
  @Test
  void urlWithExistingQueryString_usesAmpersandSeparator() {
    var contact = ContactInfo.of().withBookingUrl("https://book.example.com/?ref=foo").build();

    var info = CarpoolItineraryMapper.toBookingInfo(contact, TRIP_START, PICKUP, DROPOFF);

    assertNotNull(info);
    var expected = String.format(
      Locale.ROOT,
      "https://book.example.com/?ref=foo&from_coordinate=%.6f,%.6f&to_coordinate=%.6f,%.6f",
      PICKUP.latitude(),
      PICKUP.longitude(),
      DROPOFF.latitude(),
      DROPOFF.longitude()
    );
    assertEquals(expected, info.getContactInfo().getBookingUrl());
  }

  @Test
  void phoneAndUrl_addsBothMethodsAndOnlyRewritesUrl() {
    var contact = ContactInfo.of()
      .withPhoneNumber("+4712345678")
      .withBookingUrl("https://book.example.com")
      .build();

    var info = CarpoolItineraryMapper.toBookingInfo(contact, TRIP_START, PICKUP, DROPOFF);

    assertNotNull(info);
    assertEquals(
      EnumSet.of(BookingMethod.CALL_OFFICE, BookingMethod.ONLINE),
      info.bookingMethods()
    );
    var augmented = info.getContactInfo();
    assertEquals("+4712345678", augmented.getPhoneNumber());
    assertEquals(
      expectedAugmentedUrl("https://book.example.com", PICKUP, DROPOFF),
      augmented.getBookingUrl()
    );
  }

  /**
   * Pins down the URL with a {@code #fragment}: the appended coordinate parameters must end up
   * in the query (before the fragment), not inside the fragment. The string-level
   * {@code url.contains("?")} predicate doesn't catch this — switching to {@link java.net.URI}
   * parsing does, because the fragment is split off the URL before it can swallow the params.
   */
  @Test
  void urlWithFragment_appendsParamsBeforeFragment() {
    var contact = ContactInfo.of().withBookingUrl("https://book.example.com/page#bookform").build();

    var info = CarpoolItineraryMapper.toBookingInfo(contact, TRIP_START, PICKUP, DROPOFF);

    assertNotNull(info);
    var expected = String.format(
      Locale.ROOT,
      "https://book.example.com/page?from_coordinate=%.6f,%.6f&to_coordinate=%.6f,%.6f#bookform",
      PICKUP.latitude(),
      PICKUP.longitude(),
      DROPOFF.latitude(),
      DROPOFF.longitude()
    );
    assertEquals(expected, info.getContactInfo().getBookingUrl());
  }

  /**
   * When the booking URL is unparseable, the URL is dropped from the contact and
   * {@link BookingMethod#ONLINE} is removed from the booking methods rather than left in place
   * pointing nowhere. If the contact carried only the (now-dropped) URL, no actionable booking
   * method remains and {@code toBookingInfo} returns {@code null}, matching the
   * "non-null return ⇒ at least one usable method" contract.
   */
  @Test
  void malformedUrlOnly_returnsNull() {
    var contact = ContactInfo.of().withBookingUrl("https://book.example.com/has space").build();

    var info = CarpoolItineraryMapper.toBookingInfo(contact, TRIP_START, PICKUP, DROPOFF);

    assertNull(info);
  }

  /**
   * Same malformed-URL handling as {@link #malformedUrlOnly_returnsNull()} but with a phone
   * number also present: the call-office method survives, the dropped URL is reflected as
   * {@code null} on the returned contact, and {@code ONLINE} is absent from the booking methods.
   */
  @Test
  void malformedUrlWithPhone_keepsCallOfficeAndDropsUrl() {
    var contact = ContactInfo.of()
      .withPhoneNumber("+4712345678")
      .withBookingUrl("https://book.example.com/has space")
      .build();

    var info = CarpoolItineraryMapper.toBookingInfo(contact, TRIP_START, PICKUP, DROPOFF);

    assertNotNull(info);
    assertEquals(EnumSet.of(BookingMethod.CALL_OFFICE), info.bookingMethods());
    assertEquals("+4712345678", info.getContactInfo().getPhoneNumber());
    assertNull(info.getContactInfo().getBookingUrl());
  }

  /**
   * Pins down the placeholder behaviour documented on {@code toBookingInfo}: {@code
   * latestBookingTime} must be non-null with {@code daysPrior == 0} and the time-of-day taken
   * from the driver's trip start. This shape is load-bearing for the Transmodel
   * {@code BookingArrangement.bookWhen} mapping, which collapses to {@code "timeOfTravelOnly"}
   * if {@code latestBookingTime} is null.
   */
  @Test
  void latestBookingTime_isTripStartTimeOfDayAtDaysPriorZero() {
    var contact = ContactInfo.of().withPhoneNumber("+4712345678").build();

    var info = CarpoolItineraryMapper.toBookingInfo(contact, TRIP_START, PICKUP, DROPOFF);

    assertNotNull(info);
    var latest = info.getLatestBookingTime();
    assertNotNull(latest);
    assertEquals(LocalTime.of(8, 30), latest.getTime());
    assertEquals(0, latest.getDaysPrior());
  }
}
