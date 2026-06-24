package org.opentripplanner.transit.model.framework.fmap.densearray;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.TransitEntity;
import org.opentripplanner.transit.model.framework.fmap.EntityMap;

/**
 * An immutable, point-in-time view over a {@link DenseArrayEntityMap}. The {@code index} table is
 * shared (and keeps growing) with the live mutable map and any other snapshot taken from it - an
 * id added after this snapshot was taken will resolve to an index beyond {@code values.length},
 * which {@link #get} treats as "not present in this snapshot".
 *
 * @param <E> the entity type
 */
final class DenseArraySnapshot<E extends TransitEntity> implements EntityMap<E> {

  private final Map<FeedScopedId, Integer> index;
  private final Object[] values;

  DenseArraySnapshot(Map<FeedScopedId, Integer> index, Object[] values) {
    this.index = index;
    this.values = values;
  }

  @SuppressWarnings("unchecked")
  @Override
  public E get(FeedScopedId id) {
    Integer i = index.get(id);
    if (i == null || i >= values.length) {
      return null;
    }
    return (E) values[i];
  }

  @Override
  public boolean containsKey(FeedScopedId id) {
    return get(id) != null;
  }

  @Override
  public int size() {
    int count = 0;
    for (Object v : values) {
      if (v != null) {
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
    return Arrays.stream(values)
      .filter(v -> v != null)
      .map(v -> (E) v)
      .collect(Collectors.toList());
  }
}
