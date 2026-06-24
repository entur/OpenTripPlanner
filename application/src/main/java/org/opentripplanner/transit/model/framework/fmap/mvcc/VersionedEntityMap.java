package org.opentripplanner.transit.model.framework.fmap.mvcc;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.TransitEntity;
import org.opentripplanner.transit.model.framework.fmap.EntityMap;
import org.opentripplanner.transit.model.framework.fmap.MutableEntityMap;

/**
 * Every write prepends a new {@link VersionNode} to that id's chain, tagged with the current
 * "open" epoch. Calling {@link #snapshot()} simply hands out the current epoch number together
 * with a reference to the shared, ever-growing {@code heads} table - no copying at all, since
 * existing version chains are immutable and a reader just walks past versions newer than its
 * epoch.
 * <p>
 * Simplification: this prototype never reclaims old versions (no epoch-based garbage collection),
 * so {@link #size()}/{@link #values()} - on both the live map and any snapshot - degrade to
 * O(total versions ever written) rather than O(live entries), and memory grows without bound for
 * a long-running map. {@link #get} and {@link #snapshot()} are unaffected and stay O(1)/O(chain
 * length for that one key).
 *
 * @param <E> the entity type
 */
public class VersionedEntityMap<E extends TransitEntity> implements MutableEntityMap<E> {

  private final Map<FeedScopedId, VersionNode<E>> heads = new ConcurrentHashMap<>();
  private int currentEpoch = 0;

  @Override
  public void add(E entity) {
    write(entity.getId(), entity);
  }

  @Override
  public void remove(FeedScopedId id) {
    write(id, null);
  }

  @Override
  public E get(FeedScopedId id) {
    VersionNode<E> n = heads.get(id);
    return n == null ? null : n.value;
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
      .filter(n -> n.value != null)
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
      .map(n -> n.value)
      .filter(Objects::nonNull)
      .toList();
  }

  @Override
  public EntityMap<E> snapshot() {
    return new VersionedSnapshot<>(heads, currentEpoch++);
  }

  private void write(FeedScopedId id, E value) {
    heads.compute(id, (k, prev) -> new VersionNode<>(currentEpoch, value, prev));
  }
}
