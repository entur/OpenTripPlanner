package org.opentripplanner.transit.model.framework.fmap;

import java.util.Collection;
import java.util.List;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.TransitEntity;

/**
 * A read-only, immutable view of a map from {@link FeedScopedId} to an entity. An instance is a
 * point-in-time snapshot: once obtained, it never changes, so it is safe to read from multiple
 * threads concurrently without synchronization.
 *
 * @param <E> the entity type
 */
public interface EntityMap<E extends TransitEntity> {
  /**
   * @return the value to which the specified id is mapped, or {@code null} if this map contains
   * no mapping for the id
   */
  E get(FeedScopedId id);

  boolean containsKey(FeedScopedId id);

  int size();

  boolean isEmpty();

  Collection<E> values();

  /** Returns the singleton empty map. */
  @SuppressWarnings("unchecked")
  static <E extends TransitEntity> EntityMap<E> empty() {
    return (EntityMap<E>) EmptyEntityMap.INSTANCE;
  }
}

final class EmptyEntityMap implements EntityMap<TransitEntity> {

  static final EmptyEntityMap INSTANCE = new EmptyEntityMap();

  private EmptyEntityMap() {}

  @Override
  public TransitEntity get(FeedScopedId id) {
    return null;
  }

  @Override
  public boolean containsKey(FeedScopedId id) {
    return false;
  }

  @Override
  public int size() {
    return 0;
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public Collection<TransitEntity> values() {
    return List.of();
  }
}
