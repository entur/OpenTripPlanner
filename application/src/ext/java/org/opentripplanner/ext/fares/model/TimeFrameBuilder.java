package org.opentripplanner.ext.fares.model;

import java.time.LocalTime;
import org.opentripplanner.transit.model.framework.FeedScopedId;

public class TimeFrameBuilder {

  FeedScopedId serviceId;
  LocalTime start = LocalTime.MIN;
  LocalTime end = LocalTime.MAX;

  public TimeFrameBuilder withServiceId(FeedScopedId serviceId) {
    this.serviceId = serviceId;
    return this;
  }

  public TimeFrameBuilder withStart(LocalTime start) {
    this.start = start;
    return this;
  }

  public TimeFrameBuilder setEnd(LocalTime end) {
    this.end = end;
    return this;
  }

  public TimeFrame build() {
    return new TimeFrame(this);
  }
}
