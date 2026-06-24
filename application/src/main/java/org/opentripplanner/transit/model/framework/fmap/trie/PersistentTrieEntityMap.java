package org.opentripplanner.transit.model.framework.fmap.trie;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.TransitEntity;
import org.opentripplanner.transit.model.framework.fmap.EntityMap;
import org.opentripplanner.transit.model.framework.fmap.MutableEntityMap;

/**
 * A persistent (structurally shared) hash trie. Mutation never modifies an existing node - it
 * returns a new root that shares every untouched subtree with the previous one - so {@link
 * #snapshot()} is O(1): it just hands out the current root, which is itself already a valid,
 * immutable, point-in-time view.
 *
 * @param <E> the entity type
 */
public class PersistentTrieEntityMap<E extends TransitEntity> implements MutableEntityMap<E> {

  private TrieNode<E> root = TrieNode.empty();
  private int size = 0;

  @Override
  public void add(E entity) {
    FeedScopedId id = entity.getId();
    int[] sizeDelta = new int[1];
    root = TrieNode.insert(root, id, TrieNode.spread(id.hashCode()), 0, entity, sizeDelta);
    size += sizeDelta[0];
  }

  @Override
  public void remove(FeedScopedId id) {
    int[] sizeDelta = new int[1];
    root = TrieNode.remove(root, id, TrieNode.spread(id.hashCode()), 0, sizeDelta);
    size -= sizeDelta[0];
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

  @Override
  public EntityMap<E> snapshot() {
    return new TrieSnapshot<>(root, size);
  }
}
