package org.opentripplanner.ext.fares.model;

import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import java.time.LocalTime;
import org.opentripplanner.transit.model.framework.FeedScopedId;

public class Timeframe implements Serializable {

  private final FeedScopedId serviceId;
  private final LocalTime start;
  private final LocalTime end;

  public Timeframe(TimeFrameBuilder timeFrameBuilder) {
    this.serviceId = requireNonNull(timeFrameBuilder.serviceId);
    this.start = requireNonNull(timeFrameBuilder.start);
    this.end = requireNonNull(timeFrameBuilder.end);
  }

  public static TimeFrameBuilder of() {
    return new TimeFrameBuilder();
  }

  public FeedScopedId serviceId() {
    return serviceId;
  }

  public LocalTime startTime() {
    return start;
  }

  public LocalTime endTime() {
    return end;
  }
}
