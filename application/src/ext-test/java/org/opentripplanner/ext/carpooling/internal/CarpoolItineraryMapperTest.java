package org.opentripplanner.ext.carpooling.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
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

  @Test
  void nullContact_returnsNull() {
    assertNull(CarpoolItineraryMapper.toBookingInfo(null, TRIP_START));
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

    assertNull(CarpoolItineraryMapper.toBookingInfo(contact, TRIP_START));
  }

  @Test
  void phoneOnly_addsCallOfficeAndPreservesContactUnchanged() {
    var contact = ContactInfo.of().withPhoneNumber("+4712345678").build();

    var info = CarpoolItineraryMapper.toBookingInfo(contact, TRIP_START);

    assertNotNull(info);
    assertEquals(EnumSet.of(BookingMethod.CALL_OFFICE), info.bookingMethods());
    assertEquals(contact, info.getContactInfo());
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

    var info = CarpoolItineraryMapper.toBookingInfo(contact, TRIP_START);

    assertNotNull(info);
    var latest = info.getLatestBookingTime();
    assertNotNull(latest);
    assertEquals(LocalTime.of(8, 30), latest.getTime());
    assertEquals(0, latest.getDaysPrior());
  }
}
