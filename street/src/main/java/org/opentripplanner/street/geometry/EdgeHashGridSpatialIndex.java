package org.opentripplanner.street.geometry;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.opentripplanner.street.model.edge.Edge;

/**
 * A {@link HashGridSpatialIndex} specialised for {@link Edge}, deduplicating query results by
 * reference identity instead of value.
 * <p>
 * {@link Edge#equals(Object)} is pure reference equality ({@code this == o}) and
 * {@link Edge#hashCode()} is a value hash ({@code Objects.hash(fromv, tov)}) that allocates a
 * varargs array per call and collides forward/reverse edges sharing an endpoint pair. The default
 * value-deduplicating {@link java.util.HashSet} therefore already partitions edges purely by
 * reference (the value hash only ever selects a bucket, it never merges two distinct references).
 * Backing the result set with an {@link IdentityHashMap} collapses the identical equivalence classes
 * — one survivor per distinct reference — while never calling {@code Edge.hashCode} or
 * {@code Edge.equals}, removing the per-element varargs {@code Object[]} and the
 * {@code HashMap.Node} allocations.
 * <p>
 * Correctness invariant: this is valid <em>only</em> because {@code Edge.equals} is reference
 * identity. It must not be generalised to id-based-equals types (e.g. transit stops), where two
 * distinct instances with the same id must collapse by value; identity dedup would wrongly keep
 * both. The {@code extends HashGridSpatialIndex<Edge>} bound makes that mistake unreachable from any
 * non-edge index.
 * <p>
 * Thread-safety: the {@link IdentityHashMap} is a fresh per-query local that never escapes the
 * calling thread; no {@code ThreadLocal} is used. Probing it calls {@link System#identityHashCode},
 * which lazily installs the identity hash into each {@code Edge}'s header on first access — an
 * idempotent, benign write, not a query-visible mutation. The benign read-during-write race on the
 * bins is identical to the generic path, neither improved nor worsened.
 */
public final class EdgeHashGridSpatialIndex extends HashGridSpatialIndex<Edge> {

  public EdgeHashGridSpatialIndex() {
    super();
  }

  public EdgeHashGridSpatialIndex(double xBinSize, double yBinSize) {
    super(xBinSize, yBinSize);
  }

  @Override
  protected Set<Edge> newResultSet(int sizeHint) {
    return Collections.newSetFromMap(new IdentityHashMap<>(sizeHint));
  }
}
