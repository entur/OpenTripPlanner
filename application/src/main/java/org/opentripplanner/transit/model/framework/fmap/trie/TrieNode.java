package org.opentripplanner.transit.model.framework.fmap.trie;

import java.util.List;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.TransitEntity;

/**
 * A node of a 32-way, bitmap-compressed hash array mapped trie (HAMT/CHAMP-style). {@code bitmap}
 * marks which of the 32 possible 5-bit slots at this level are occupied; {@code slots} holds only
 * the occupied ones, in bit order, so a node with a single child costs a 1-element array instead
 * of a 32-element one.
 * <p>
 * Each occupied slot holds either a child {@link TrieNode} (push one level deeper, consuming the
 * next 5 hash bits) or a {@link Leaf} chain (one entry, except in the rare case of a genuine
 * 32-bit hash collision). All mutation methods are path-copying: they return a new node, sharing
 * every subtree that did not change with the original.
 *
 * @param <E> the entity type
 */
final class TrieNode<E extends TransitEntity> {

  private static final TrieNode<?> EMPTY = new TrieNode<>(0, new Object[0]);

  final int bitmap;
  final Object[] slots;

  TrieNode(int bitmap, Object[] slots) {
    this.bitmap = bitmap;
    this.slots = slots;
  }

  @SuppressWarnings("unchecked")
  static <E extends TransitEntity> TrieNode<E> empty() {
    return (TrieNode<E>) EMPTY;
  }

  /** MurmurHash3 finalizer: spreads entropy across all 32 bits so each 5-bit chunk is well mixed. */
  static int spread(int h) {
    h ^= (h >>> 16);
    h *= 0x85ebca6b;
    h ^= (h >>> 13);
    h *= 0xc2b2ae35;
    h ^= (h >>> 16);
    return h;
  }

  @SuppressWarnings("unchecked")
  static <E extends TransitEntity> E get(TrieNode<E> node, FeedScopedId key, int hash) {
    int shift = 0;
    while (true) {
      int bit = 1 << ((hash >>> shift) & 0x1F);
      if ((node.bitmap & bit) == 0) {
        return null;
      }
      Object slot = node.slots[Integer.bitCount(node.bitmap & (bit - 1))];
      if (slot instanceof TrieNode<?>) {
        node = (TrieNode<E>) slot;
        shift += 5;
      } else {
        return ((Leaf<E>) slot).find(key);
      }
    }
  }

  /**
   * Returns the new root. Sets {@code sizeDelta[0]} to 1 if {@code key} was not present before
   * (a new entry was added), or 0 if an existing entry's value was replaced.
   */
  @SuppressWarnings("unchecked")
  static <E extends TransitEntity> TrieNode<E> insert(
    TrieNode<E> node,
    FeedScopedId key,
    int hash,
    int shift,
    E value,
    int[] sizeDelta
  ) {
    int bit = 1 << ((hash >>> shift) & 0x1F);
    int pos = Integer.bitCount(node.bitmap & (bit - 1));

    if ((node.bitmap & bit) == 0) {
      sizeDelta[0] = 1;
      return new TrieNode<>(
        node.bitmap | bit,
        arrayInsert(node.slots, pos, new Leaf<>(key, value, null))
      );
    }

    Object existing = node.slots[pos];
    Object newSlot;
    if (existing instanceof TrieNode<?>) {
      newSlot = insert((TrieNode<E>) existing, key, hash, shift + 5, value, sizeDelta);
    } else {
      Leaf<E> leaf = (Leaf<E>) existing;
      if (containsKey(leaf, key)) {
        sizeDelta[0] = 0;
        newSlot = replaceInChain(leaf, key, value);
      } else if (shift + 5 >= 32) {
        // All 32 hash bits are exhausted: this is a genuine hash collision, chain it.
        sizeDelta[0] = 1;
        newSlot = new Leaf<>(key, value, leaf);
      } else {
        // A different key landed in the same slot: split it into a child node one level deeper.
        // `leaf` is guaranteed to be a single entry here - multi-entry chains only occur once all
        // hash bits are exhausted (see the branch above).
        sizeDelta[0] = 1;
        TrieNode<E> sub = singleton(leaf.key, leaf.value, spread(leaf.key.hashCode()), shift + 5);
        newSlot = insert(sub, key, hash, shift + 5, value, new int[1]);
      }
    }
    Object[] newSlots = node.slots.clone();
    newSlots[pos] = newSlot;
    return new TrieNode<>(node.bitmap, newSlots);
  }

  /** Returns the new root. Sets {@code sizeDelta[0]} to 1 if {@code key} was removed, else 0. */
  @SuppressWarnings("unchecked")
  static <E extends TransitEntity> TrieNode<E> remove(
    TrieNode<E> node,
    FeedScopedId key,
    int hash,
    int shift,
    int[] sizeDelta
  ) {
    int bit = 1 << ((hash >>> shift) & 0x1F);
    if ((node.bitmap & bit) == 0) {
      sizeDelta[0] = 0;
      return node;
    }
    int pos = Integer.bitCount(node.bitmap & (bit - 1));
    Object existing = node.slots[pos];

    Object newSlot;
    if (existing instanceof TrieNode<?>) {
      TrieNode<E> newChild = remove((TrieNode<E>) existing, key, hash, shift + 5, sizeDelta);
      if (sizeDelta[0] == 0) {
        return node;
      }
      if (newChild.bitmap == 0) {
        return new TrieNode<>(node.bitmap & ~bit, arrayRemove(node.slots, pos));
      }
      newSlot = newChild;
    } else {
      Leaf<E> leaf = (Leaf<E>) existing;
      if (!containsKey(leaf, key)) {
        sizeDelta[0] = 0;
        return node;
      }
      sizeDelta[0] = 1;
      Leaf<E> newChain = removeFromChain(leaf, key);
      if (newChain == null) {
        return new TrieNode<>(node.bitmap & ~bit, arrayRemove(node.slots, pos));
      }
      newSlot = newChain;
    }
    Object[] newSlots = node.slots.clone();
    newSlots[pos] = newSlot;
    return new TrieNode<>(node.bitmap, newSlots);
  }

  @SuppressWarnings("unchecked")
  static <E extends TransitEntity> void collectValues(TrieNode<E> node, List<E> out) {
    for (Object slot : node.slots) {
      if (slot instanceof TrieNode<?>) {
        collectValues((TrieNode<E>) slot, out);
      } else {
        for (Leaf<E> l = (Leaf<E>) slot; l != null; l = l.next) {
          out.add(l.value);
        }
      }
    }
  }

  private static <E extends TransitEntity> boolean containsKey(Leaf<E> leaf, FeedScopedId key) {
    for (Leaf<E> l = leaf; l != null; l = l.next) {
      if (l.key.equals(key)) {
        return true;
      }
    }
    return false;
  }

  private static <E extends TransitEntity> Leaf<E> replaceInChain(
    Leaf<E> leaf,
    FeedScopedId key,
    E value
  ) {
    if (leaf.key.equals(key)) {
      return new Leaf<>(key, value, leaf.next);
    }
    return new Leaf<>(leaf.key, leaf.value, replaceInChain(leaf.next, key, value));
  }

  private static <E extends TransitEntity> Leaf<E> removeFromChain(Leaf<E> leaf, FeedScopedId key) {
    if (leaf == null) {
      return null;
    }
    if (leaf.key.equals(key)) {
      return leaf.next;
    }
    return new Leaf<>(leaf.key, leaf.value, removeFromChain(leaf.next, key));
  }

  private static <E extends TransitEntity> TrieNode<E> singleton(
    FeedScopedId key,
    E value,
    int hash,
    int shift
  ) {
    int bit = 1 << ((hash >>> shift) & 0x1F);
    return new TrieNode<>(bit, new Object[] { new Leaf<>(key, value, null) });
  }

  private static Object[] arrayInsert(Object[] arr, int pos, Object val) {
    Object[] result = new Object[arr.length + 1];
    System.arraycopy(arr, 0, result, 0, pos);
    result[pos] = val;
    System.arraycopy(arr, pos, result, pos + 1, arr.length - pos);
    return result;
  }

  private static Object[] arrayRemove(Object[] arr, int pos) {
    Object[] result = new Object[arr.length - 1];
    System.arraycopy(arr, 0, result, 0, pos);
    System.arraycopy(arr, pos + 1, result, pos, arr.length - pos - 1);
    return result;
  }
}
