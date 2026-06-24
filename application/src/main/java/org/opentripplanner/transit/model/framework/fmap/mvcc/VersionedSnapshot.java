package org.opentripplanner.transit.model.framework.fmap.mvcc;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.TransitEntity;
import org.opentripplanner.transit.model.framework.fmap.EntityMap;

/**
 * An immutable, point-in-time view of a {@link VersionedEntityMap} at a fixed {@code epoch}. The
 * {@code heads} table is shared with the live map and keeps growing after this snapshot was
 * taken; {@link #get} stays correct (and O(1) plus chain length) because it walks each entry's own
 * version chain back to the first version visible at {@code epoch}.
 *
 * @param <E> the entity type
 */
final class VersionedSnapshot<E extends TransitEntity> implements EntityMap<E> {

  private final Map<FeedScopedId, VersionNode<E>> heads;
  private final int epoch;

  VersionedSnapshot(Map<FeedScopedId, VersionNode<E>> heads, int epoch) {
    this.heads = heads;
    this.epoch = epoch;
  }

  @Override
  public E get(FeedScopedId id) {
    VersionNode<E> n = heads.get(id);
    return n == null ? null : n.valueAsOf(epoch);
  }

  @Override
  public boolean containsKey(FeedScopedId id) {
    return get(id) != null;
  }

  @Override
  public int size() {
    return (int) heads
      .values()
      .stream()
      .filter(n -> n.valueAsOf(epoch) != null)
      .count();
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public Collection<E> values() {
    return heads
      .values()
      .stream()
      .map(n -> n.valueAsOf(epoch))
      .filter(Objects::nonNull)
      .toList();
  }
}
