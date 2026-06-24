package org.opentripplanner.transit.model.framework.fmap;

import java.util.Collection;
import java.util.Map;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.TransitEntity;

/**
 * A simple {@link EntityMap} backed directly by a {@link Map}. The caller is responsible for
 * making sure the given map is never mutated after being wrapped - typically by passing in the
 * result of {@link Map#copyOf} or another already-immutable map.
 *
 * @param <E> the entity type
 */
public final class MapBackedEntityMap<E extends TransitEntity> implements EntityMap<E> {

  private final Map<FeedScopedId, E> map;

  public MapBackedEntityMap(Map<FeedScopedId, E> map) {
    this.map = map;
  }

  @Override
  public E get(FeedScopedId id) {
    return map.get(id);
  }

  @Override
  public boolean containsKey(FeedScopedId id) {
    return map.containsKey(id);
  }

  @Override
  public int size() {
    return map.size();
  }

  @Override
  public boolean isEmpty() {
    return map.isEmpty();
  }

  @Override
  public Collection<E> values() {
    return map.values();
  }
}
