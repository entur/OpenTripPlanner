package org.opentripplanner.transit.model.framework.fmap;

import java.util.Collection;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.TransitEntity;

/**
 * A mutable, single-writer buffer on top of an {@link EntityMap}. All implementations in this
 * package assume a single writer thread calls {@link #add}/{@link #remove}/{@link #snapshot()};
 * any number of reader threads may concurrently read from snapshots already handed out by
 * {@link #snapshot()}.
 * <p>
 * Calling {@link #snapshot()} must never make later mutations of this map visible through a
 * snapshot that was already returned - each snapshot is a frozen, immutable point-in-time view.
 *
 * @param <E> the entity type
 */
public interface MutableEntityMap<E extends TransitEntity> extends EntityMap<E> {
  void add(E entity);

  default void addAll(Collection<E> entities) {
    entities.forEach(this::add);
  }

  void remove(FeedScopedId id);

  /** Freeze the current content and return an immutable snapshot. This map remains mutable. */
  EntityMap<E> snapshot();
}
