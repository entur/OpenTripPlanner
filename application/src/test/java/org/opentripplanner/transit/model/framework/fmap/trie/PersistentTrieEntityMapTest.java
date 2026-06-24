package org.opentripplanner.transit.model.framework.fmap.trie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.core.model.id.FeedScopedIdForTestFactory;
import org.opentripplanner.transit.model.framework.fmap.EntityMap;
import org.opentripplanner.transit.model.framework.fmap.FmapTestEntity;
import org.opentripplanner.transit.model.framework.fmap.MutableEntityMapContractTest;

class PersistentTrieEntityMapTest
  extends MutableEntityMapContractTest<PersistentTrieEntityMap<FmapTestEntity>> {

  @Override
  protected PersistentTrieEntityMap<FmapTestEntity> newMap() {
    return new PersistentTrieEntityMap<>();
  }

  /**
   * Exercises the trie's bitmap-array insert/remove and hash-collision-chaining logic at a scale
   * where many keys must split into sub-nodes and several genuine 32-bit hash collisions are
   * statistically expected, while also checking many intermediate snapshots stay independently
   * correct.
   */
  @Test
  void manyEntriesWithInterleavedSnapshotsStayConsistent() {
    PersistentTrieEntityMap<FmapTestEntity> map = newMap();
    int n = 20_000;
    Set<FeedScopedId> expectedAtEachSnapshot = new HashSet<>();

    for (int i = 0; i < n; i++) {
      FeedScopedId id = FeedScopedIdForTestFactory.id(i);
      map.add(new FmapTestEntity(id, i));
      expectedAtEachSnapshot.add(id);
      if (i % 1000 == 0) {
        EntityMap<FmapTestEntity> snapshot = map.snapshot();
        assertEquals(expectedAtEachSnapshot.size(), snapshot.size());
        for (FeedScopedId expected : expectedAtEachSnapshot) {
          assertEquals(expected, snapshot.get(expected).getId());
        }
      }
    }

    assertEquals(n, map.size());
    for (int i = 0; i < n; i++) {
      FeedScopedId id = FeedScopedIdForTestFactory.id(i);
      assertEquals(i, map.get(id).version());
    }

    // remove half of them and check the live map and an old snapshot diverge correctly
    EntityMap<FmapTestEntity> beforeRemoval = map.snapshot();
    for (int i = 0; i < n; i += 2) {
      map.remove(FeedScopedIdForTestFactory.id(i));
    }
    assertEquals(n / 2, map.size());
    assertEquals(n, beforeRemoval.size());
    assertNull(map.get(FeedScopedIdForTestFactory.id(0)));
    assertEquals(0, beforeRemoval.get(FeedScopedIdForTestFactory.id(0)).version());
  }
}
