package org.opentripplanner.transit.model.framework.fmap;

import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.TransitEntity;

/**
 * A minimal {@link TransitEntity} used to test the collection strategies in this package without
 * pulling in the full domain-entity builder machinery.
 */
public record FmapTestEntity(FeedScopedId id, int version) implements TransitEntity {
  public FmapTestEntity(FeedScopedId id) {
    this(id, 0);
  }

  @Override
  public FeedScopedId getId() {
    return id;
  }
}
