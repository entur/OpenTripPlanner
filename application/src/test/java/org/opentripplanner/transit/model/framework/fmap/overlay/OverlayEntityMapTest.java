package org.opentripplanner.transit.model.framework.fmap.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.core.model.id.FeedScopedIdForTestFactory;
import org.opentripplanner.transit.model.framework.fmap.EntityMap;
import org.opentripplanner.transit.model.framework.fmap.FmapTestEntity;
import org.opentripplanner.transit.model.framework.fmap.MutableEntityMapContractTest;

class OverlayEntityMapTest extends MutableEntityMapContractTest<OverlayEntityMap<FmapTestEntity>> {

  @Override
  protected OverlayEntityMap<FmapTestEntity> newMap() {
    // A large threshold keeps the shared contract tests exercising the "thin overlay" path.
    return new OverlayEntityMap<>(1000);
  }

  @Test
  void compactionProducesACorrectFreshBaseAndResetsTheOverlay() {
    OverlayEntityMap<FmapTestEntity> map = new OverlayEntityMap<>(3);
    FeedScopedId idA = FeedScopedIdForTestFactory.id("a");
    FeedScopedId idB = FeedScopedIdForTestFactory.id("b");
    FeedScopedId idC = FeedScopedIdForTestFactory.id("c");

    map.add(new FmapTestEntity(idA, 1));
    map.add(new FmapTestEntity(idB, 1));
    EntityMap<FmapTestEntity> belowThreshold = map.snapshot();
    assertEquals(2, belowThreshold.size());

    // This third change reaches the compaction threshold (3).
    map.remove(idA);
    EntityMap<FmapTestEntity> compacted = map.snapshot();

    assertFalse(compacted.containsKey(idA));
    assertTrue(compacted.containsKey(idB));
    assertEquals(1, compacted.size());
    // the earlier, below-threshold snapshot must be unaffected by the later compaction
    assertTrue(belowThreshold.containsKey(idA));

    // further mutations after compaction must still behave correctly against the new base
    map.add(new FmapTestEntity(idC, 1));
    EntityMap<FmapTestEntity> afterCompaction = map.snapshot();
    assertEquals(2, afterCompaction.size());
    assertTrue(afterCompaction.containsKey(idB));
    assertTrue(afterCompaction.containsKey(idC));
  }
}
