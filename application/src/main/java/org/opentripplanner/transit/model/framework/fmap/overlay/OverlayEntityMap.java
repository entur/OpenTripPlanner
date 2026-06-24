package org.opentripplanner.transit.model.framework.fmap.overlay;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.TransitEntity;
import org.opentripplanner.transit.model.framework.fmap.EntityMap;
import org.opentripplanner.transit.model.framework.fmap.MapBackedEntityMap;
import org.opentripplanner.transit.model.framework.fmap.MutableEntityMap;

/**
 * Keeps a small mutable overlay (added/removed ids) on top of an immutable {@code base}
 * snapshot. Reads check the overlay first, then fall back to {@code base}. A snapshot is cheap
 * (just defensive copies of the overlay) as long as the overlay stays small; once it grows past
 * {@code compactionThreshold}, the next {@link #snapshot()} call compacts everything into a fresh
 * {@code base} and clears the overlay.
 *
 * @param <E> the entity type
 */
public class OverlayEntityMap<E extends TransitEntity> implements MutableEntityMap<E> {

  private final int compactionThreshold;
  private EntityMap<E> base = EntityMap.empty();
  private final Map<FeedScopedId, E> added = new HashMap<>();
  private final Set<FeedScopedId> removed = new HashSet<>();

  public OverlayEntityMap(int compactionThreshold) {
    this.compactionThreshold = compactionThreshold;
  }

  @Override
  public void add(E entity) {
    removed.remove(entity.getId());
    added.put(entity.getId(), entity);
  }

  @Override
  public void remove(FeedScopedId id) {
    added.remove(id);
    removed.add(id);
  }

  @Override
  public E get(FeedScopedId id) {
    E e = added.get(id);
    if (e != null) {
      return e;
    }
    return removed.contains(id) ? null : base.get(id);
  }

  @Override
  public boolean containsKey(FeedScopedId id) {
    return get(id) != null;
  }

  @Override
  public Collection<E> values() {
    return OverlaySupport.mergeValues(base, added, removed);
  }

  @Override
  public int size() {
    return values().size();
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public EntityMap<E> snapshot() {
    if (added.size() + removed.size() >= compactionThreshold) {
      base = compact();
      added.clear();
      removed.clear();
      return base;
    }
    return new OverlaySnapshot<>(base, Map.copyOf(added), Set.copyOf(removed));
  }

  private EntityMap<E> compact() {
    Map<FeedScopedId, E> merged = new HashMap<>();
    for (E e : base.values()) {
      if (!removed.contains(e.getId())) {
        merged.put(e.getId(), e);
      }
    }
    merged.putAll(added);
    return new MapBackedEntityMap<>(Map.copyOf(merged));
  }
}
