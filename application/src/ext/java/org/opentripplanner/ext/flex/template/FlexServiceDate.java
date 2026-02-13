package org.opentripplanner.ext.flex.template;

import gnu.trove.set.TIntSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.timetable.booking.RoutingBookingInfo;
import org.opentripplanner.utils.time.ServiceDateUtils;

/**
 * This class contains information used in a flex router, and depends on the date the search was
 * made on.
 */
public class FlexServiceDate {

  /** The local date */
  private final LocalDate serviceDate;

  /**
   * How many seconds does this date's "midnight" (12 hours before noon) differ from the "midnight"
   * of the date for the search.
   */
  private final int secondsFromStartOfTime;

  /** Which services are running on the date. */
  private final TIntSet servicesRunning;

  /**
   * The requested booking time as seconds since the start of service for this date.
   * Calculated relative to this specific service date's start-of-service.
   */
  private final int requestedBookingTime;

  private FlexServiceDate(
    LocalDate serviceDate,
    int secondsFromStartOfTime,
    int requestedBookingTime,
    TIntSet servicesRunning
  ) {
    this.serviceDate = serviceDate;
    this.secondsFromStartOfTime = secondsFromStartOfTime;
    this.requestedBookingTime = requestedBookingTime;
    this.servicesRunning = servicesRunning;
  }

  public static FlexServiceDate of(
    LocalDate serviceDate,
    int secondsFromStartOfTime,
    @Nullable Instant requestedBookingTimeInstant,
    ZoneId timeZone,
    TIntSet servicesRunning
  ) {
    int requestedBookingTime;
    if (requestedBookingTimeInstant == null) {
      requestedBookingTime = RoutingBookingInfo.NOT_SET;
    } else {
      ZonedDateTime startOfService = ServiceDateUtils.asStartOfService(serviceDate, timeZone);
      requestedBookingTime = ServiceDateUtils.secondsSinceStartOfTime(
        startOfService,
        requestedBookingTimeInstant
      );
    }
    return new FlexServiceDate(
      serviceDate,
      secondsFromStartOfTime,
      requestedBookingTime,
      servicesRunning
    );
  }

  LocalDate serviceDate() {
    return serviceDate;
  }

  int secondsFromStartOfTime() {
    return secondsFromStartOfTime;
  }

  /**
   * Get the requested booking time as seconds since the start of service for this date.
   */
  int requestedBookingTime() {
    return requestedBookingTime;
  }

  /**
   * Return true if the given {@code serviceCode} is active and running.
   */
  public boolean isTripServiceRunning(int serviceCode) {
    return servicesRunning != null && servicesRunning.contains(serviceCode);
  }
}
