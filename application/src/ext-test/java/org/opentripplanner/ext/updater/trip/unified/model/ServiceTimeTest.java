package org.opentripplanner.ext.updater.trip.unified.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opentripplanner.utils.time.ServiceDateUtils;

class ServiceTimeTest {

  @ParameterizedTest
  @CsvSource(
    {
      "00:00:00, 0",
      "08:30:00, 30600",
      "8:30:00, 30600",
      "08:30, 30600",
      "23:59:59, 86399",
      "24:00:00, 86400",
      "25:15:00, 90900",
      "119:59:59, 431999",
    }
  )
  void parsesTheGtfsTimeForms(String time, int expectedSeconds) {
    assertEquals(expectedSeconds, ServiceTime.parse(time).secondsPastMidnight());
  }

  @ParameterizedTest
  @ValueSource(
    strings = { "", "junk", "8:30 AM", "10:99", "10:15:99", "-1:00", "1000:00:00", "10", "10:5" }
  )
  void rejectsAnythingElse(String time) {
    assertThrows(IllegalArgumentException.class, () -> ServiceTime.parse(time));
  }

  /**
   * The origin OTP measures from is noon minus twelve hours, which is later than midnight on a
   * day where the clock is set back for daylight saving, so a stored time can be negative. Only
   * {@link ServiceTime#parse} validates - it is the entry point feed input arrives through,
   * and the GTFS string form cannot express a sign.
   */
  @Test
  void acceptsNegativeSeconds() {
    assertEquals(-1800, ServiceTime.ofSecondsPastMidnight(-1800).secondsPastMidnight());
    assertEquals("23:30-1d", ServiceTime.ofSecondsPastMidnight(-1800).toString());
  }

  @Test
  void measuresAZonedTimeFromTheStartOfService() {
    var zone = ZoneId.of("Europe/Oslo");
    var date = LocalDate.of(2025, 6, 15);
    var startOfService = ServiceDateUtils.asStartOfService(date, zone);
    var halfPastEight = ZonedDateTime.of(date, LocalTime.of(8, 30), zone);
    assertEquals(ServiceTime.parse("8:30"), ServiceTime.of(startOfService, halfPastEight));
    assertEquals(ServiceTime.parse("8:30"), ServiceTime.ofNullable(startOfService, halfPastEight));
    assertNull(ServiceTime.ofNullable(startOfService, null));
  }

  /**
   * On the day the clock is set back, the start of service (noon minus twelve hours) falls at
   * 01:00 local time, so a time of day before that measures negative - and a nominal time of day
   * after the transition still maps to its GTFS reading, not to the elapsed duration.
   */
  @Test
  void measuresFromNoonMinusTwelveHoursOnADstChangeDate() {
    var zone = ZoneId.of("Europe/Oslo");
    var setBackDate = LocalDate.of(2025, 10, 26);
    var startOfService = ServiceDateUtils.asStartOfService(setBackDate, zone);
    var halfPastMidnight = ZonedDateTime.of(setBackDate, LocalTime.of(0, 30), zone);
    assertEquals(-1800, ServiceTime.of(startOfService, halfPastMidnight).secondsPastMidnight());
    var halfPastEight = ZonedDateTime.of(setBackDate, LocalTime.of(8, 30), zone);
    assertEquals(ServiceTime.parse("8:30"), ServiceTime.of(startOfService, halfPastEight));
  }

  @Test
  void equalsByValue() {
    assertEquals(ServiceTime.parse("25:15:00"), ServiceTime.ofSecondsPastMidnight(90900));
    assertEquals(
      ServiceTime.parse("25:15:00").hashCode(),
      ServiceTime.ofSecondsPastMidnight(90900).hashCode()
    );
    assertNotEquals(ServiceTime.parse("25:15:00"), ServiceTime.parse("25:15:01"));
  }

  @Test
  void ordersByTime() {
    assertTrue(ServiceTime.parse("23:59:59").compareTo(ServiceTime.parse("24:00")) < 0);
  }

  @Test
  void printsTheCompactForm() {
    assertEquals("8:30", ServiceTime.parse("08:30:00").toString());
  }

  @Test
  void shiftsBySecondsAndServiceDays() {
    assertEquals(ServiceTime.parse("8:31"), ServiceTime.parse("8:30").plusSeconds(60));
    assertEquals(ServiceTime.parse("8:29"), ServiceTime.parse("8:30").plusSeconds(-60));
    assertEquals(ServiceTime.parse("32:30"), ServiceTime.parse("8:30").plusDays(1));
  }

  @ParameterizedTest
  @CsvSource({ "0, 0", "86399, 0", "86400, 1", "90900, 1", "-1, -1", "-1800, -1", "-86400, -1" })
  void countsTheDaysPastTheServiceDate(int seconds, int expectedDayOffset) {
    assertEquals(expectedDayOffset, ServiceTime.ofSecondsPastMidnight(seconds).dayOffset());
  }
}
