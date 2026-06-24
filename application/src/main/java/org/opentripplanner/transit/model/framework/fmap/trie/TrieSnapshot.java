package org.opentripplanner.transit.model.framework.fmap.trie;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.TransitEntity;
import org.opentripplanner.transit.model.framework.fmap.EntityMap;

/**
 * An immutable, point-in-time view of a {@link PersistentTrieEntityMap}: just a {@code root} node
 * reference and its {@code size}, both frozen at the moment the snapshot was taken. The root and
 * all of its descendants are themselves immutable, so no copying was needed to produce this view.
 *
 * @param <E> the entity type
 */
final class TrieSnapshot<E extends TransitEntity> implements EntityMap<E> {

  private final TrieNode<E> root;
  private final int size;

  TrieSnapshot(TrieNode<E> root, int size) {
    this.root = root;
    this.size = size;
  }

  @Override
  public E get(FeedScopedId id) {
    return TrieNode.get(root, id, TrieNode.spread(id.hashCode()));
  }

  @Override
  public boolean containsKey(FeedScopedId id) {
    return get(id) != null;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public boolean isEmpty() {
    return size == 0;
  }

  @Override
  public Collection<E> values() {
    List<E> result = new ArrayList<>(size);
    TrieNode.collectValues(root, result);
    return result;
  }
}
