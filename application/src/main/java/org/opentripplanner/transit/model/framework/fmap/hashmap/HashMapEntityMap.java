package org.opentripplanner.transit.model.framework.fmap.hashmap;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.TransitEntity;
import org.opentripplanner.transit.model.framework.fmap.EntityMap;
import org.opentripplanner.transit.model.framework.fmap.MapBackedEntityMap;
import org.opentripplanner.transit.model.framework.fmap.MutableEntityMap;

/**
 * Baseline strategy: a plain {@link HashMap}. A snapshot is a full defensive copy of the map,
 * produced with {@link Map#copyOf}.
 *
 * @param <E> the entity type
 */
public class HashMapEntityMap<E extends TransitEntity> implements MutableEntityMap<E> {

  private final Map<FeedScopedId, E> map = new HashMap<>();

  @Override
  public void add(E entity) {
    map.put(entity.getId(), entity);
  }

  @Override
  public void remove(FeedScopedId id) {
    map.remove(id);
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

  @Override
  public EntityMap<E> snapshot() {
    return new MapBackedEntityMap<>(Map.copyOf(map));
  }
}
