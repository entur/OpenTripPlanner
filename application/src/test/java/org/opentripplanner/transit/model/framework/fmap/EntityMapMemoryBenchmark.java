package org.opentripplanner.transit.model.framework.fmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import org.opentripplanner.core.model.id.FeedScopedIdForTestFactory;
import org.opentripplanner.transit.model.framework.fmap.densearray.DenseArrayEntityMap;
import org.opentripplanner.transit.model.framework.fmap.hashmap.HashMapEntityMap;
import org.opentripplanner.transit.model.framework.fmap.mvcc.VersionedEntityMap;
import org.opentripplanner.transit.model.framework.fmap.overlay.OverlayEntityMap;
import org.opentripplanner.transit.model.framework.fmap.trie.PersistentTrieEntityMap;

/**
 * Measures the heap footprint of a single {@code (strategy, size)} combination, in its own JVM
 * process with a fixed {@code -Xms == -Xmx}. A single shared, long-running process with the
 * default dynamically-sized heap (as {@link EntityMapBenchmark} uses for timing) makes {@code
 * Runtime.totalMemory() - Runtime.freeMemory()} far too noisy to trust for memory deltas - the
 * heap pool itself grows/shrinks by tens of MB regardless of what we allocate. Pinning the heap
 * size removes that source of noise, at the cost of needing one process launch per data point.
 * <p>
 * Prints a single number to stdout: used heap bytes after building {@code size} entities, then
 * either taking one snapshot (default) or - if a third {@code retainedSnapshots} argument is
 * given - simulating that many realistic small update rounds (touching ~0.1% of entries each,
 * same as {@link EntityMapBenchmark}), keeping every round's snapshot reachable in a list. All of
 * that is measured after two back-to-back {@code System.gc()} calls.
 * Args: {@code <strategyName> <size> [retainedSnapshots]}.
 */
public final class EntityMapMemoryBenchmark {

  private static final Map<String, Supplier<MutableEntityMap<FmapTestEntity>>> STRATEGIES = Map.of(
    "hashmap",
    HashMapEntityMap::new,
    "trie",
    PersistentTrieEntityMap::new,
    "overlay",
    () -> new OverlayEntityMap<>(10_000),
    "densearray",
    DenseArrayEntityMap::new,
    "mvcc",
    VersionedEntityMap::new
  );

  public static void main(String[] args) {
    String strategyName = args[0];
    int size = Integer.parseInt(args[1]);

    if ("none".equals(strategyName)) {
      // No map at all - isolates the irreducible per-entity cost (FmapTestEntity + FeedScopedId
      // + its backing Strings) that every strategy below pays regardless of map structure.
      List<FmapTestEntity> entities = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
        entities.add(new FmapTestEntity(FeedScopedIdForTestFactory.id(i), 0));
      }
      System.gc();
      System.gc();
      System.out.println(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
      if (entities.size() != size) {
        throw new AssertionError();
      }
      return;
    }

    int retainedSnapshots = args.length > 2 ? Integer.parseInt(args[2]) : 1;

    MutableEntityMap<FmapTestEntity> map = STRATEGIES.get(strategyName).get();
    for (int i = 0; i < size; i++) {
      map.add(new FmapTestEntity(FeedScopedIdForTestFactory.id(i), 0));
    }

    List<EntityMap<FmapTestEntity>> retained = new ArrayList<>(retainedSnapshots);
    Random random = new Random(42);
    int touchPerRound = Math.max(1, size / 1000);
    int nextNewId = size;
    for (int round = 0; round < retainedSnapshots; round++) {
      for (int t = 0; t < touchPerRound; t++) {
        if (t % 2 == 0) {
          map.add(
            new FmapTestEntity(FeedScopedIdForTestFactory.id(random.nextInt(size)), round + 1)
          );
        } else {
          map.add(new FmapTestEntity(FeedScopedIdForTestFactory.id(nextNewId++), round + 1));
        }
      }
      retained.add(map.snapshot());
    }

    System.gc();
    System.gc();
    long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    System.out.println(used);

    if (retained.size() != retainedSnapshots) {
      // Unreachable - just keeps `map` and every retained snapshot alive up to the measurement.
      throw new AssertionError();
    }
  }
}
