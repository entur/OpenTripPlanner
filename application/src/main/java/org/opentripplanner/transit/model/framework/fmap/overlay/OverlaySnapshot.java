package org.opentripplanner.transit.model.framework.fmap.overlay;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.TransitEntity;
import org.opentripplanner.transit.model.framework.fmap.EntityMap;

/**
 * An immutable, point-in-time view of an {@link OverlayEntityMap}: an immutable {@code added}
 * layer and an immutable {@code removed} set sitting on top of an immutable {@code base}.
 *
 * @param <E> the entity type
 */
final class OverlaySnapshot<E extends TransitEntity> implements EntityMap<E> {

  private final EntityMap<E> base;
  private final Map<FeedScopedId, E> added;
  private final Set<FeedScopedId> removed;

  OverlaySnapshot(EntityMap<E> base, Map<FeedScopedId, E> added, Set<FeedScopedId> removed) {
    this.base = base;
    this.added = added;
    this.removed = removed;
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
}
