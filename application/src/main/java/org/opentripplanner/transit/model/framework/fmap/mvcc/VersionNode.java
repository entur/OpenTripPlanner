package org.opentripplanner.transit.model.framework.fmap.mvcc;

import org.opentripplanner.transit.model.framework.TransitEntity;

/**
 * One version of a single entry: the value (or {@code null} if the entry was removed at this
 * epoch) plus a link to the previous version, if any. The chain is immutable once created - a
 * write prepends a new head, it never mutates an existing node.
 */
final class VersionNode<E extends TransitEntity> {

  final int epoch;
  final E value;
  final VersionNode<E> previous;

  VersionNode(int epoch, E value, VersionNode<E> previous) {
    this.epoch = epoch;
    this.value = value;
    this.previous = previous;
  }

  /** The value visible to a reader at {@code snapshotEpoch}, or {@code null} if none. */
  E valueAsOf(int snapshotEpoch) {
    VersionNode<E> n = this;
    while (n != null && n.epoch > snapshotEpoch) {
      n = n.previous;
    }
    return n == null ? null : n.value;
  }
}
