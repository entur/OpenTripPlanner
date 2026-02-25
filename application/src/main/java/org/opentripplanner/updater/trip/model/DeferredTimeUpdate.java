package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.utils.time.ServiceDateUtils;

/**
 * A time update that stores raw ZonedDateTime values for deferred resolution.
 * Used by SIRI parser when service date is not known at parse time (e.g., when
 * tripOnServiceDateId is present but no explicit service date).
 * <p>
 * The actual time conversion to seconds-since-start-of-service happens in the
 * applier stage when the service date has been resolved.
 */
public final class DeferredTimeUpdate implements ParsedTimeUpdate {

  private final ZonedDateTime actualTime;

  @Nullable
  private final ZonedDateTime scheduledTime;

  private DeferredTimeUpdate(ZonedDateTime actualTime, @Nullable ZonedDateTime scheduledTime) {
    this.actualTime = Objects.requireNonNull(actualTime, "actualTime must not be null");
    this.scheduledTime = scheduledTime;
  }

  /**
   * Create a deferred time update with the actual time and optional scheduled time.
   *
   * @param actualTime the actual/expected time from the real-time feed
   * @param scheduledTime the scheduled/aimed time (optional, for delay calculation)
   * @return a new DeferredTimeUpdate
   */
  public static DeferredTimeUpdate of(
    ZonedDateTime actualTime,
    @Nullable ZonedDateTime scheduledTime
  ) {
    return new DeferredTimeUpdate(actualTime, scheduledTime);
  }

  @Override
  public TimeUpdate resolve(LocalDate serviceDate, ZoneId timeZone) {
    ZonedDateTime startOfService = ServiceDateUtils.asStartOfService(serviceDate, timeZone);
    int absoluteSeconds = ServiceDateUtils.secondsSinceStartOfService(startOfService, actualTime);
    Integer scheduledSeconds = scheduledTime != null
      ? ServiceDateUtils.secondsSinceStartOfService(startOfService, scheduledTime)
      : null;
    return TimeUpdate.ofAbsolute(absoluteSeconds, scheduledSeconds);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DeferredTimeUpdate that = (DeferredTimeUpdate) o;
    return (
      Objects.equals(actualTime, that.actualTime) &&
      Objects.equals(scheduledTime, that.scheduledTime)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(actualTime, scheduledTime);
  }

  @Override
  public String toString() {
    return (
      "DeferredTimeUpdate{" + "actualTime=" + actualTime + ", scheduledTime=" + scheduledTime + '}'
    );
  }
}
