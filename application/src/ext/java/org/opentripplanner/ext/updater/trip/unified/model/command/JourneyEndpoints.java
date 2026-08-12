package org.opentripplanner.ext.updater.trip.unified.model.command;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.ext.updater.trip.unified.model.ServiceTime;
import org.opentripplanner.utils.time.ServiceDateUtils;

/**
 * Where a journey starts and where it ends, as the aimed times and stops the message states for
 * those two calls. A SIRI-ET journey is identified by them when the ids it carries name no trip,
 * so a command that names its trip only this way carries them for the fuzzy trip matcher to work
 * on.
 * <p>
 * A removal carries no calls, so the endpoints are the whole of what it says about the journey's
 * itinerary. Only the aimed times are kept: the real-time times a producer reports say when the
 * journey is running, not which journey it is, and the aimed times are the ones the scheduled
 * timetable can be searched by.
 */
public final class JourneyEndpoints {

  private final StopReference origin;

  @Nullable
  private final ZonedDateTime aimedDeparture;

  private final StopReference destination;

  @Nullable
  private final ZonedDateTime aimedArrival;

  /**
   * @param origin The stop the journey starts from
   * @param aimedDeparture The time the journey is scheduled to leave the origin, or null if the
   *                       message states none
   * @param destination The stop the journey ends at
   * @param aimedArrival The time the journey is scheduled to reach the destination, or null if the
   *                     message states none
   */
  public JourneyEndpoints(
    StopReference origin,
    @Nullable ZonedDateTime aimedDeparture,
    StopReference destination,
    @Nullable ZonedDateTime aimedArrival
  ) {
    this.origin = Objects.requireNonNull(origin, "origin must not be null");
    this.aimedDeparture = aimedDeparture;
    this.destination = Objects.requireNonNull(destination, "destination must not be null");
    this.aimedArrival = aimedArrival;
  }

  public StopReference origin() {
    return origin;
  }

  public StopReference destination() {
    return destination;
  }

  /**
   * The aimed departure from the origin, measured against the given service date, or null if the
   * message states none.
   */
  @Nullable
  public ServiceTime aimedDeparture(LocalDate serviceDate, ZoneId timeZone) {
    return ServiceTime.ofNullable(startOfService(serviceDate, timeZone), aimedDeparture);
  }

  /**
   * The aimed arrival at the destination, measured against the given service date, or null if the
   * message states none.
   */
  @Nullable
  public ServiceTime aimedArrival(LocalDate serviceDate, ZoneId timeZone) {
    return ServiceTime.ofNullable(startOfService(serviceDate, timeZone), aimedArrival);
  }

  private static ZonedDateTime startOfService(LocalDate serviceDate, ZoneId timeZone) {
    return ServiceDateUtils.asStartOfService(serviceDate, timeZone);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    JourneyEndpoints that = (JourneyEndpoints) o;
    return (
      Objects.equals(origin, that.origin) &&
      Objects.equals(aimedDeparture, that.aimedDeparture) &&
      Objects.equals(destination, that.destination) &&
      Objects.equals(aimedArrival, that.aimedArrival)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(origin, aimedDeparture, destination, aimedArrival);
  }

  @Override
  public String toString() {
    return (
      "JourneyEndpoints{" +
      "origin=" +
      origin +
      ", aimedDeparture=" +
      aimedDeparture +
      ", destination=" +
      destination +
      ", aimedArrival=" +
      aimedArrival +
      '}'
    );
  }
}
