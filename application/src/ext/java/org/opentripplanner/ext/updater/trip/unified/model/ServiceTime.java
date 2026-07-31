package org.opentripplanner.ext.updater.trip.unified.model;

import java.util.regex.Pattern;
import org.opentripplanner.utils.time.TimeUtils;

/**
 * A GTFS time: seconds relative to midnight of a service date. Unlike a time of day it is bounded
 * in neither direction: GTFS times a trip that starts after midnight past 24 hours on the previous
 * service date, so 25:15:00 names 01:15 on the day after the service date, and a value derived
 * relative to the start of the service day can be negative, because that origin is noon minus
 * twelve hours - later than midnight on a day where the clock is set back for daylight saving.
 * This is the convention the rest of OTP stores scheduled times in, which makes the wrapped value
 * directly comparable to them.
 */
public final class ServiceTime implements Comparable<ServiceTime> {

  /** Hours unbounded, as GTFS requires; minutes and seconds 00-59; seconds optional. */
  private static final Pattern GTFS_TIME = Pattern.compile("(\\d{1,3}):([0-5]\\d)(?::([0-5]\\d))?");

  private static final int SECONDS_IN_DAY = 24 * 60 * 60;

  private final int secondsPastMidnight;

  private ServiceTime(int secondsPastMidnight) {
    this.secondsPastMidnight = secondsPastMidnight;
  }

  /**
   * Parse the GTFS {@code H:MM:SS} form, with unbounded hours and the seconds optional.
   *
   * @throws IllegalArgumentException for any other form - the caller decides what a malformed
   *                                  time means, this type only decides what a time is.
   */
  public static ServiceTime parse(String time) {
    var matcher = GTFS_TIME.matcher(time);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Not a valid GTFS time (H:MM:SS): '" + time + "'");
    }
    int hours = Integer.parseInt(matcher.group(1));
    int minutes = Integer.parseInt(matcher.group(2));
    int seconds = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
    return new ServiceTime(60 * (60 * hours + minutes) + seconds);
  }

  /**
   * Wrap a value already measured in OTP's convention, negative included - see the class doc.
   * Feed input arrives through {@link #parse}, which is where a GTFS time is validated.
   */
  public static ServiceTime ofSecondsPastMidnight(int secondsPastMidnight) {
    return new ServiceTime(secondsPastMidnight);
  }

  public int secondsPastMidnight() {
    return secondsPastMidnight;
  }

  /** This time shifted the given number of seconds, negative to shift backwards. */
  public ServiceTime plusSeconds(int seconds) {
    return new ServiceTime(secondsPastMidnight + seconds);
  }

  /**
   * The same time of day the given number of service days later - a service day is a fixed 24
   * hours in this convention, so 8:30 one day later is 32:30.
   */
  public ServiceTime plusDays(int days) {
    return new ServiceTime(secondsPastMidnight + days * SECONDS_IN_DAY);
  }

  /**
   * How many calendar days past the service date this time falls: 0 within the service date, 1
   * past 24:00:00 (a 25:15:00 departure happens on the day after), -1 before midnight.
   */
  public int dayOffset() {
    return Math.floorDiv(secondsPastMidnight, SECONDS_IN_DAY);
  }

  @Override
  public int compareTo(ServiceTime other) {
    return Integer.compare(secondsPastMidnight, other.secondsPastMidnight);
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof ServiceTime other && secondsPastMidnight == other.secondsPastMidnight);
  }

  @Override
  public int hashCode() {
    return Integer.hashCode(secondsPastMidnight);
  }

  @Override
  public String toString() {
    return TimeUtils.timeToStrCompact(secondsPastMidnight);
  }
}
