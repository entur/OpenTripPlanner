package org.opentripplanner.transit.model.framework.fmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.core.model.id.FeedScopedIdForTestFactory;

/**
 * A shared correctness contract for every {@link MutableEntityMap} strategy in this package.
 * Concrete subclasses only need to provide a fresh instance via {@link #newMap()} - every
 * strategy must behave identically from the caller's point of view, even though their internal
 * snapshot/mutable mechanics differ.
 */
public abstract class MutableEntityMapContractTest<M extends MutableEntityMap<FmapTestEntity>> {

  private static final FeedScopedId ID_A = FeedScopedIdForTestFactory.id("a");
  private static final FeedScopedId ID_B = FeedScopedIdForTestFactory.id("b");
  private static final FeedScopedId ID_C = FeedScopedIdForTestFactory.id("c");
  private static final FeedScopedId MISSING_ID = FeedScopedIdForTestFactory.id("missing");

  protected abstract M newMap();

  @Test
  void getOnEmptyMapReturnsNull() {
    assertNull(newMap().get(MISSING_ID));
  }

  @Test
  void addAndGet() {
    M map = newMap();
    var entityA = new FmapTestEntity(ID_A);
    map.add(entityA);
    assertEquals(entityA, map.get(ID_A));
    assertNull(map.get(MISSING_ID));
  }

  @Test
  void addReplacesExistingValueForSameId() {
    M map = newMap();
    map.add(new FmapTestEntity(ID_A, 1));
    map.add(new FmapTestEntity(ID_A, 2));
    assertEquals(2, map.get(ID_A).version());
    assertEquals(1, map.size());
  }

  @Test
  void containsKey() {
    M map = newMap();
    assertFalse(map.containsKey(ID_A));
    map.add(new FmapTestEntity(ID_A));
    assertTrue(map.containsKey(ID_A));
    assertFalse(map.containsKey(MISSING_ID));
  }

  @Test
  void sizeAndIsEmpty() {
    M map = newMap();
    assertTrue(map.isEmpty());
    assertEquals(0, map.size());
    map.add(new FmapTestEntity(ID_A));
    map.add(new FmapTestEntity(ID_B));
    assertFalse(map.isEmpty());
    assertEquals(2, map.size());
  }

  @Test
  void remove() {
    M map = newMap();
    map.add(new FmapTestEntity(ID_A));
    map.remove(ID_A);
    assertNull(map.get(ID_A));
    assertFalse(map.containsKey(ID_A));
    assertEquals(0, map.size());
  }

  @Test
  void removeOfMissingIdIsNoOp() {
    M map = newMap();
    map.add(new FmapTestEntity(ID_A));
    map.remove(MISSING_ID);
    assertEquals(1, map.size());
  }

  @Test
  void addAllAndValues() {
    M map = newMap();
    var a = new FmapTestEntity(ID_A);
    var b = new FmapTestEntity(ID_B);
    map.addAll(List.of(a, b));
    assertEquals(Set.of(a, b), Set.copyOf(map.values()));
    assertEquals(2, map.size());
  }

  @Test
  void snapshotReflectsStateAtTimeItWasTaken() {
    M map = newMap();
    map.add(new FmapTestEntity(ID_A, 1));
    EntityMap<FmapTestEntity> snapshot = map.snapshot();

    assertEquals(1, snapshot.get(ID_A).version());
    assertEquals(1, snapshot.size());
    assertTrue(snapshot.containsKey(ID_A));
    assertFalse(snapshot.containsKey(ID_B));
  }

  @Test
  void laterMutationsAreNotVisibleThroughAnAlreadyTakenSnapshot() {
    M map = newMap();
    map.add(new FmapTestEntity(ID_A, 1));
    EntityMap<FmapTestEntity> snapshot = map.snapshot();

    // Mutate the live map after the snapshot was taken: replace, add, and remove.
    map.add(new FmapTestEntity(ID_A, 2));
    map.add(new FmapTestEntity(ID_B, 1));
    map.remove(ID_A);

    // The live map sees all of that.
    assertNull(map.get(ID_A));
    assertEquals(1, map.get(ID_B).version());
    assertEquals(1, map.size());

    // The snapshot must still show the world exactly as it was when it was taken.
    assertEquals(1, snapshot.get(ID_A).version());
    assertNull(snapshot.get(ID_B));
    assertEquals(1, snapshot.size());
  }

  @Test
  void removalAfterSnapshotDoesNotAffectThatSnapshot() {
    M map = newMap();
    map.add(new FmapTestEntity(ID_A));
    map.add(new FmapTestEntity(ID_B));
    EntityMap<FmapTestEntity> beforeRemoval = map.snapshot();

    map.remove(ID_B);
    EntityMap<FmapTestEntity> afterRemoval = map.snapshot();

    assertTrue(beforeRemoval.containsKey(ID_B));
    assertFalse(afterRemoval.containsKey(ID_B));
    assertTrue(afterRemoval.containsKey(ID_A));
  }

  @Test
  void multipleSequentialSnapshotsAreEachIndependentlyCorrect() {
    M map = newMap();
    map.add(new FmapTestEntity(ID_A, 1));
    EntityMap<FmapTestEntity> s0 = map.snapshot();

    map.add(new FmapTestEntity(ID_B, 1));
    EntityMap<FmapTestEntity> s1 = map.snapshot();

    map.add(new FmapTestEntity(ID_C, 1));
    EntityMap<FmapTestEntity> s2 = map.snapshot();

    assertEquals(Set.of(ID_A), idsOf(s0));
    assertEquals(Set.of(ID_A, ID_B), idsOf(s1));
    assertEquals(Set.of(ID_A, ID_B, ID_C), idsOf(s2));
  }

  private static Set<FeedScopedId> idsOf(EntityMap<FmapTestEntity> map) {
    return map.values().stream().map(FmapTestEntity::getId).collect(Collectors.toSet());
  }
}
