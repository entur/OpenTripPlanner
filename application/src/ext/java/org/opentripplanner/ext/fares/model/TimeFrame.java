package org.opentripplanner.ext.fares.model;

import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import java.time.LocalTime;
import org.opentripplanner.transit.model.framework.FeedScopedId;

public class TimeFrame implements Serializable {

  private final FeedScopedId serviceId;
  private final LocalTime start;
  private final LocalTime end;

  public TimeFrame(TimeFrameBuilder timeFrameBuilder) {
    this.serviceId = requireNonNull(timeFrameBuilder.serviceId);
    this.start = requireNonNull(timeFrameBuilder.start);
    this.end = requireNonNull(timeFrameBuilder.end);
  }
}
