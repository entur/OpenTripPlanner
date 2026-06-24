package org.opentripplanner.transit.model.framework.fmap.overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.TransitEntity;
import org.opentripplanner.transit.model.framework.fmap.EntityMap;

/**
 * Shared merge logic between the live {@link OverlayEntityMap} and a frozen {@link
 * OverlaySnapshot}: the logical content is always {@code (base minus removed) plus added}, with
 * {@code added} taking precedence over {@code base} for any id present in both.
 */
final class OverlaySupport {

  private OverlaySupport() {}

  static <E extends TransitEntity> List<E> mergeValues(
    EntityMap<E> base,
    Map<FeedScopedId, E> added,
    Set<FeedScopedId> removed
  ) {
    List<E> result = new ArrayList<>(added.values());
    for (E e : base.values()) {
      FeedScopedId id = e.getId();
      if (!removed.contains(id) && !added.containsKey(id)) {
        result.add(e);
      }
    }
    return result;
  }
}
