package org.opentripplanner.transit.model.framework.fmap.trie;

import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.TransitEntity;

/**
 * A single key/value entry stored in a trie slot. {@code next} is non-null only in the rare case
 * of a true 32-bit hash collision between two different ids - every other slot holds a
 * single-element chain.
 */
final class Leaf<E extends TransitEntity> {

  final FeedScopedId key;
  final E value;
  final Leaf<E> next;

  Leaf(FeedScopedId key, E value, Leaf<E> next) {
    this.key = key;
    this.value = value;
    this.next = next;
  }

  E find(FeedScopedId id) {
    for (Leaf<E> l = this; l != null; l = l.next) {
      if (l.key.equals(id)) {
        return l.value;
      }
    }
    return null;
  }
}
