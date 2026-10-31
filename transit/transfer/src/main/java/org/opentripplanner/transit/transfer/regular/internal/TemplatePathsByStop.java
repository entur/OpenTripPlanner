package org.opentripplanner.transit.transfer.regular.internal;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The {@code P} path templates of one user profile, keyed by {@code (fromStop, toStop)}.
 * <p>
 * Part of the immutable base of {@link TemplatePathsByUserProfile}: written only at graph build,
 * and never modified once the build repository is sealed. The {@code toStop} maps handed out must
 * not be modified.
 * <p>
 * This class is not thread-safe while it is written to.
 *
 * @param <P> the street path type
 */
final class TemplatePathsByStop<P> implements Serializable {

  private static final int[] NO_STOPS = new int[0];

  private final Map<Integer, Map<Integer, P>> paths = new HashMap<>();

  /** The from-stops by to-stop, derived from {@link #paths} on first use. */
  private transient volatile int[][] reverseIndex;

  /**
   * Store the paths from {@code fromStop}, keyed by {@code toStop}; paths to other stops are kept.
   * The given map is copied, not retained.
   */
  void setPaths(int fromStop, Map<Integer, P> pathsByToStop) {
    if (pathsByToStop.isEmpty()) {
      return;
    }
    // Never modify a stored toStop map, it may already have been handed out - replace it.
    var byToStop = new HashMap<>(paths.getOrDefault(fromStop, Map.of()));
    byToStop.putAll(pathsByToStop);
    paths.put(fromStop, byToStop);
  }

  /** The paths from {@code fromStop}, by {@code toStop}. Empty if the stop is unknown. */
  Map<Integer, P> pathsFrom(int fromStop) {
    return paths.getOrDefault(fromStop, Map.of());
  }

  /** Call {@code consumer} with the paths of every {@code fromStop}, by {@code toStop}. */
  void forEachFromStop(BiConsumer<Integer, Map<Integer, P>> consumer) {
    paths.forEach(consumer);
  }

  /**
   * The from-stops with a path to {@code toStop}. The index is built on first use, from the paths
   * stored by then, so this must not be called while the paths are still written to. The returned
   * array must not be modified.
   */
  int[] fromStopsTo(int toStop) {
    var index = reverseIndex;
    if (index == null) {
      synchronized (this) {
        index = reverseIndex;
        if (index == null) {
          index = buildReverseIndex();
          reverseIndex = index;
        }
      }
    }
    return toStop < index.length && index[toStop] != null ? index[toStop] : NO_STOPS;
  }

  private int[][] buildReverseIndex() {
    int max = 0;
    for (var byToStop : paths.values()) {
      for (int toStop : byToStop.keySet()) {
        max = Math.max(max, toStop);
      }
    }
    var counts = new int[max + 1];
    paths.values().forEach(byToStop -> byToStop.keySet().forEach(toStop -> counts[toStop]++));
    var index = new int[max + 1][];
    for (int i = 0; i <= max; ++i) {
      if (counts[i] > 0) {
        index[i] = new int[counts[i]];
      }
    }
    var fill = new int[max + 1];
    paths.forEach((fromStop, byToStop) ->
      byToStop.keySet().forEach(toStop -> index[toStop][fill[toStop]++] = fromStop)
    );
    return index;
  }

  /** The number of stored paths. */
  int size() {
    return paths.values().stream().mapToInt(Map::size).sum();
  }
}
