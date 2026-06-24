package org.opentripplanner.transit.model.framework.fmap.densearray;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedIdForTestFactory;
import org.opentripplanner.transit.model.framework.fmap.FmapTestEntity;
import org.opentripplanner.transit.model.framework.fmap.MutableEntityMapContractTest;

class DenseArrayEntityMapTest
  extends MutableEntityMapContractTest<DenseArrayEntityMap<FmapTestEntity>> {

  @Override
  protected DenseArrayEntityMap<FmapTestEntity> newMap() {
    return new DenseArrayEntityMap<>();
  }

  /** Regression test: insert past the initial backing array capacity, forcing a resize. */
  @Test
  void growingPastInitialCapacityKeepsAllEntriesReadable() {
    DenseArrayEntityMap<FmapTestEntity> map = newMap();
    // well past the initial capacity of 16
    int n = 100;
    for (int i = 0; i < n; i++) {
      map.add(new FmapTestEntity(FeedScopedIdForTestFactory.id(i), i));
    }
    assertEquals(n, map.size());
    for (int i = 0; i < n; i++) {
      assertEquals(i, map.get(FeedScopedIdForTestFactory.id(i)).version());
    }
  }
}
