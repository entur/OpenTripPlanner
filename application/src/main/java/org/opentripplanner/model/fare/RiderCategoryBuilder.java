package org.opentripplanner.model.fare;

import org.opentripplanner.transit.model.framework.FeedScopedId;

public class RiderCategoryBuilder {

  final FeedScopedId id;
  String name;

  RiderCategoryBuilder(FeedScopedId id) {
    this.id = id;
  }

  public RiderCategoryBuilder withName(String name) {
    this.name = name;
    return this;
  }

  public RiderCategory build() {
    return new RiderCategory(this);
  }
}
