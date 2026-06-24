package org.opentripplanner.transit.model.framework.fmap.densearray;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.TransitEntity;
import org.opentripplanner.transit.model.framework.fmap.EntityMap;
import org.opentripplanner.transit.model.framework.fmap.MutableEntityMap;

/**
 * Assigns every entity a stable, dense {@code int} index on first insert and stores values in a
 * plain {@code Object[]} addressed by that index. A snapshot is a defensive copy of the array
 * (cheap arraycopy), so it costs less per entry than rebuilding a hash table, even though it is
 * still O(n).
 * <p>
 * The id -> index lookup table is never copied: index assignment is permanent (indices are never
 * reused, even after a {@link #remove}) and the table only ever grows, so it is safe to share the
 * same {@link ConcurrentHashMap} between the live mutable map and every snapshot taken from it -
 * a snapshot's own array length is what bounds which indices are visible to it.
 *
 * @param <E> the entity type
 */
public class DenseArrayEntityMap<E extends TransitEntity> implements MutableEntityMap<E> {

  private final Map<FeedScopedId, Integer> index = new ConcurrentHashMap<>();
  private Object[] values = new Object[16];
  private int nextIndex = 0;

  @Override
  public void add(E entity) {
    // Must resolve the index first, in a separate statement: `values` is evaluated for the
    // store *before* the index expression runs, so an indexOf()-triggered resize wouldn't
    // otherwise be reflected in which array object actually receives the write.
    int i = indexOf(entity.getId(), true);
    values[i] = entity;
  }

  @Override
  public void remove(FeedScopedId id) {
    Integer i = index.get(id);
    if (i != null) {
      values[i] = null;
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public E get(FeedScopedId id) {
    Integer i = index.get(id);
    return i == null ? null : (E) values[i];
  }

  @Override
  public boolean containsKey(FeedScopedId id) {
    return get(id) != null;
  }

  @Override
  public int size() {
    int count = 0;
    for (int i = 0; i < nextIndex; i++) {
      if (values[i] != null) {
        count++;
      }
    }
    return count;
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Collection<E> values() {
    return Arrays.stream(values, 0, nextIndex)
      .filter(v -> v != null)
      .map(v -> (E) v)
      .collect(Collectors.toList());
  }

  @Override
  public EntityMap<E> snapshot() {
    return new DenseArraySnapshot<>(index, Arrays.copyOf(values, nextIndex));
  }

  private int indexOf(FeedScopedId id, boolean createIfAbsent) {
    Integer i = index.get(id);
    if (i != null) {
      return i;
    }
    if (!createIfAbsent) {
      return -1;
    }
    int newIndex = nextIndex++;
    index.put(id, newIndex);
    ensureCapacity(newIndex + 1);
    return newIndex;
  }

  private void ensureCapacity(int minCapacity) {
    if (minCapacity > values.length) {
      values = Arrays.copyOf(values, Math.max(minCapacity, values.length * 2));
    }
  }
}
