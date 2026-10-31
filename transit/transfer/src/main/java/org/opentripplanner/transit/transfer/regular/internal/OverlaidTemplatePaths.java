package org.opentripplanner.transit.transfer.regular.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.opentripplanner.transit.transfer.regular.api.preferences.AbstractUserPreferences;

/**
 * The runtime path templates: the paths generated at graph build (the base), with the transfers
 * from some stops replaced at runtime (the overlay).
 * <p>
 * The unit of change is a <em>leaf</em>, all paths from one {@code fromStop} under one profile.
 * A replaced leaf hides the base leaf completely; an empty replaced leaf means the stop has no
 * transfers. Resetting a leaf removes it from the overlay, so the base leaf is used again.
 * <p>
 * <b>Copy-on-write.</b> The base is shared by all instances and never modified. The leaves in the
 * overlay are never modified once stored either, so {@link #copy()} only copies the two upper
 * levels of the overlay - its cost is proportional to the number of replaced leaves, not to the
 * number of paths.
 * <p>
 * This class is not thread-safe. An instance published in a snapshot is never written to.
 *
 * @param <P> the street path type
 */
final class OverlaidTemplatePaths<P> {

  private static final int[] NO_STOPS = new int[0];

  private final TemplatePathsByUserProfile<P> base;

  /** The overlay by to-stop, derived on first read, reset by every write. */
  private volatile Map<AbstractUserPreferences<?>, Map<Integer, int[]>> overlayReverse;
  private final Map<AbstractUserPreferences<?>, Map<Integer, Map<Integer, P>>> overlay;

  OverlaidTemplatePaths(TemplatePathsByUserProfile<P> base) {
    this(base, new HashMap<>());
  }

  private OverlaidTemplatePaths(
    TemplatePathsByUserProfile<P> base,
    Map<AbstractUserPreferences<?>, Map<Integer, Map<Integer, P>>> overlay
  ) {
    this.base = base;
    this.overlay = overlay;
  }

  /**
   * Copy the overlay. The base and the leaves are shared, not copied. A write to the copy never
   * changes this instance, and the other way around.
   */
  OverlaidTemplatePaths<P> copy() {
    var copy = new HashMap<AbstractUserPreferences<?>, Map<Integer, Map<Integer, P>>>();
    overlay.forEach((profile, byFromStop) -> copy.put(profile, new HashMap<>(byFromStop)));
    return new OverlaidTemplatePaths<>(base, copy);
  }

  /**
   * Replace all paths from {@code fromStop} under {@code profile}. The given map is copied, not
   * retained.
   *
   * @throws IllegalArgumentException if no paths were generated for {@code profile} at graph build
   */
  void replace(AbstractUserPreferences<?> profile, int fromStop, Map<Integer, P> pathsByToStop) {
    assertProfileExists(profile);
    overlayReverse = null;
    overlay.computeIfAbsent(profile, p -> new HashMap<>()).put(fromStop, Map.copyOf(pathsByToStop));
  }

  /**
   * Use the graph-build paths from {@code fromStop} under {@code profile} again.
   *
   * @return {@code false} if the paths were not replaced, so nothing changed
   * @throws IllegalArgumentException if no paths were generated for {@code profile} at graph build
   */
  boolean reset(AbstractUserPreferences<?> profile, int fromStop) {
    assertProfileExists(profile);
    var byFromStop = overlay.get(profile);
    if (byFromStop == null || !byFromStop.containsKey(fromStop)) {
      return false;
    }
    overlayReverse = null;
    byFromStop.remove(fromStop);
    if (byFromStop.isEmpty()) {
      overlay.remove(profile);
    }
    return true;
  }

  /** Were transfers generated under {@code profile} at graph build? */
  boolean hasProfile(AbstractUserPreferences<?> profile) {
    return base.profiles().contains(profile);
  }

  /** See {@link TemplatePathsByUserProfile#closestProfile}. */
  @Nullable
  AbstractUserPreferences<?> closestProfile(
    AbstractUserPreferences<?> userPreferences,
    Comparator<AbstractUserPreferences<?>> declarationOrder
  ) {
    return base.closestProfile(userPreferences, declarationOrder);
  }

  /**
   * All paths from {@code fromStop} under {@code profile}, by {@code toStop}: the replaced ones if
   * the stop is in the overlay, the graph-build ones otherwise. The returned map must not be
   * modified.
   */
  Map<Integer, P> pathsFrom(AbstractUserPreferences<?> profile, int fromStop) {
    var replaced = overlay.getOrDefault(profile, Map.of()).get(fromStop);
    if (replaced != null) {
      return replaced;
    }
    return base.pathsFrom(profile, fromStop);
  }

  /**
   * The from-stops with a path to {@code toStop} under {@code profile}: the graph-build paths from
   * stops that are not replaced, and the replaced paths. The returned array must not be modified.
   */
  int[] fromStopsTo(AbstractUserPreferences<?> profile, int toStop) {
    var baseStops = base.fromStopsTo(profile, toStop);
    var replaced = overlay.get(profile);
    if (replaced == null) {
      return baseStops;
    }
    var added = overlayReverse().get(profile).getOrDefault(toStop, NO_STOPS);
    var result = new int[baseStops.length + added.length];
    int n = 0;
    for (int fromStop : baseStops) {
      if (!replaced.containsKey(fromStop)) {
        result[n++] = fromStop;
      }
    }
    for (int fromStop : added) {
      result[n++] = fromStop;
    }
    return n == result.length ? result : Arrays.copyOf(result, n);
  }

  /** The replaced paths by profile and to-stop, built on first use. */
  private Map<AbstractUserPreferences<?>, Map<Integer, int[]>> overlayReverse() {
    var index = overlayReverse;
    if (index == null) {
      var built = new HashMap<AbstractUserPreferences<?>, Map<Integer, int[]>>();
      overlay.forEach((profile, byFromStop) -> {
        var lists = new HashMap<Integer, List<Integer>>();
        byFromStop.forEach((fromStop, byToStop) ->
          byToStop
            .keySet()
            .forEach(toStop -> lists.computeIfAbsent(toStop, k -> new ArrayList<>()).add(fromStop))
        );
        var arrays = new HashMap<Integer, int[]>();
        lists.forEach((toStop, froms) ->
          arrays.put(
            toStop,
            froms
              .stream()
              .mapToInt(i -> i)
              .toArray()
          )
        );
        built.put(profile, arrays);
      });
      index = built;
      overlayReverse = index;
    }
    return index;
  }

  /** Call {@code consumer} for every path stored under {@code profile}. */
  void forEachPath(AbstractUserPreferences<?> profile, PathConsumer<P> consumer) {
    var replaced = overlay.getOrDefault(profile, Map.of());
    base.forEachFromStop(profile, (fromStop, byToStop) -> {
      if (!replaced.containsKey(fromStop)) {
        byToStop.forEach((toStop, path) -> consumer.accept(fromStop, toStop, path));
      }
    });
    replaced.forEach((fromStop, byToStop) ->
      byToStop.forEach((toStop, path) -> consumer.accept(fromStop, toStop, path))
    );
  }

  /** The path from {@code fromStop} to {@code toStop} under {@code profile}, if any. */
  @Nullable
  P findPath(@Nullable AbstractUserPreferences<?> profile, int fromStop, int toStop) {
    return profile == null ? null : pathsFrom(profile, fromStop).get(toStop);
  }

  private void assertProfileExists(AbstractUserPreferences<?> profile) {
    if (!base.profiles().contains(profile)) {
      throw new IllegalArgumentException(
        "No transfers were generated for the profile " + profile + " at graph build."
      );
    }
  }

  @FunctionalInterface
  interface PathConsumer<P> {
    void accept(int fromStop, int toStop, P path);
  }
}
