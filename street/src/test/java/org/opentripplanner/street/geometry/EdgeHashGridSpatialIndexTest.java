package org.opentripplanner.street.geometry;

import static com.google.common.truth.Truth.assertThat;
import static org.opentripplanner.street.model.StreetModelFactory.intersectionVertex;
import static org.opentripplanner.street.model.StreetModelFactory.streetEdge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.edge.StreetEdge;
import org.opentripplanner.street.model.vertex.StreetVertex;

/**
 * Tests for {@link EdgeHashGridSpatialIndex}, the identity-deduplicating edge index, and for the
 * value-deduplicating behaviour of the generic {@link HashGridSpatialIndex} base it must NOT change.
 * <p>
 * Background: the default grid bins are ~0.0035° in longitude and ~0.005° in latitude, so an edge
 * whose endpoints differ by more than ~0.0035° in longitude spans several bins and is stored once
 * per touched bin — exactly the cross-bin duplication that {@code query()} must collapse.
 */
class EdgeHashGridSpatialIndexTest {

  /**
   * One edge spanning several bins, queried over an envelope covering those bins, must come back
   * exactly once (identity dedup of cross-bin duplicates).
   */
  @Test
  void crossBinDuplicatesAreCollapsed() {
    var index = new EdgeHashGridSpatialIndex();
    // ~0.01° of longitude => spans ~4 longitude bins at this latitude.
    StreetEdge edge = edge(60.0, 10.0, 60.0, 10.01);
    index.insert(edge.getGeometry(), edge);

    List<Edge> result = index.query(new Envelope(10.0, 10.01, 60.0, 60.0));

    assertThat(result).containsExactly(edge);
  }

  /**
   * For a battery of random envelopes, the identity-backed edge index returns the same set of edges
   * as the generic value-backed index. Because {@code Edge.equals} is reference identity, the two
   * dedup strategies must produce identical result sets — this is the load-bearing equivalence proof.
   */
  @Test
  void identityIndexMatchesGenericIndexForEdges() {
    var identityIndex = new EdgeHashGridSpatialIndex();
    var genericIndex = new HashGridSpatialIndex<Edge>();

    var rnd = new Random(42);
    for (int i = 0; i < 60; i++) {
      StreetEdge edge = randomEdge(rnd);
      identityIndex.insert(edge.getGeometry(), edge);
      genericIndex.insert(edge.getGeometry(), edge);
    }

    for (int i = 0; i < 200; i++) {
      Envelope env = randomEnvelope(rnd);
      Set<Edge> fromIdentity = new HashSet<>(identityIndex.query(env));
      Set<Edge> fromGeneric = new HashSet<>(genericIndex.query(env));
      assertThat(fromIdentity).isEqualTo(fromGeneric);
    }
  }

  /**
   * Two distinct edges that share the same {@code (fromVertex, toVertex)} pair have an equal
   * {@code Edge.hashCode()} ({@code Objects.hash(fromv, tov)}) but are different references. The
   * index must return BOTH — identity dedup keeps references the value hash would have bucketed
   * together, matching the old {@code HashSet} behaviour (whose membership also used identity
   * {@code equals}).
   */
  @Test
  void forwardReverseHashCollisionKeepsBothEdges() {
    var index = new EdgeHashGridSpatialIndex();
    var a = intersectionVertex("a", 60.0, 10.0);
    var b = intersectionVertex("b", 60.0, 10.01);

    StreetEdge edge1 = streetEdge(a, b);
    StreetEdge edge2 = streetEdge(a, b);
    assertThat(edge1).isNotSameInstanceAs(edge2);
    assertThat(edge1.hashCode()).isEqualTo(edge2.hashCode());

    index.insert(edge1.getGeometry(), edge1);
    index.insert(edge2.getGeometry(), edge2);

    List<Edge> result = index.query(new Envelope(10.0, 10.01, 60.0, 60.0));

    assertThat(result).containsExactly(edge1, edge2);
  }

  /**
   * The same edge reference inserted twice into one bin (permitted by the index contract for
   * multi-envelope inserts) must still be returned once.
   */
  @Test
  void withinBinDuplicateIsCollapsed() {
    var index = new EdgeHashGridSpatialIndex();
    // tiny extent so the edge lands in a single bin
    StreetEdge edge = edge(60.0, 10.0, 60.0, 10.0005);
    var pointEnvelope = new Envelope(new Coordinate(10.0, 60.0));

    index.insert(pointEnvelope, edge);
    index.insert(pointEnvelope, edge);

    List<Edge> result = index.query(pointEnvelope);

    assertThat(result).containsExactly(edge);
  }

  /**
   * {@code queryAlongLineStrings} on the edge index also deduplicates by identity: an edge touched
   * by many adjacent bins is returned once, and the result matches the generic index.
   */
  @Test
  void queryAlongLineStringsDedupsByIdentity() {
    var identityIndex = new EdgeHashGridSpatialIndex();
    var genericIndex = new HashGridSpatialIndex<Edge>();

    // spans many bins along its own line string
    StreetEdge spanning = edge(60.0, 10.0, 60.0, 10.02);
    StreetEdge other = edge(60.01, 10.0, 60.01, 10.001);
    for (var index : List.of(identityIndex, genericIndex)) {
      index.insert(spanning.getGeometry(), spanning);
      index.insert(other.getGeometry(), other);
    }

    List<LineString> lines = List.of(spanning.getGeometry());
    Set<Edge> fromIdentity = identityIndex.queryAlongLineStrings(lines);
    Set<Edge> fromGeneric = genericIndex.queryAlongLineStrings(lines);

    assertThat(fromIdentity).contains(spanning);
    assertThat(fromIdentity).isEqualTo(fromGeneric);
    // The spanning edge appears once despite touching many bins along its own line string.
    assertThat(
      fromIdentity
        .stream()
        .filter(e -> e == spanning)
        .count()
    ).isEqualTo(1);
  }

  /** An envelope touching no bins returns an empty (non-null) list; a dense envelope is correct. */
  @Test
  void emptyAndDenseQueries() {
    var index = new EdgeHashGridSpatialIndex();
    assertThat(index.query(new Envelope(10.0, 10.01, 60.0, 60.0))).isEmpty();

    var distinct = new HashSet<Edge>();
    var rnd = new Random(7);
    for (int i = 0; i < 40; i++) {
      // All within one small dense region so a single envelope catches them all.
      StreetEdge edge = edge(
        60.0 + rnd.nextDouble() * 0.001,
        10.0 + rnd.nextDouble() * 0.001,
        60.0 + rnd.nextDouble() * 0.001,
        10.0 + rnd.nextDouble() * 0.001
      );
      index.insert(edge.getGeometry(), edge);
      distinct.add(edge);
    }

    Set<Edge> result = new HashSet<>(index.query(new Envelope(9.99, 10.02, 59.99, 60.02)));
    assertThat(result).isEqualTo(distinct);
  }

  /**
   * Regression guard: the generic base index must keep deduplicating by VALUE, not identity. Two
   * distinct objects that are {@code equals} (e.g. transit stops, whose {@code AbstractTransitEntity}
   * equals/hashCode are id-based) must collapse to one — proving the identity behaviour was scoped to
   * {@link EdgeHashGridSpatialIndex} and not leaked into the generic default used by the stop indexes.
   */
  @Test
  void genericBaseStillDedupsByValueNotIdentity() {
    var index = new HashGridSpatialIndex<IdKeyed>();
    var first = new IdKeyed(7);
    // distinct instance, equal by id
    var second = new IdKeyed(7);
    assertThat(first).isNotSameInstanceAs(second);
    assertThat(first).isEqualTo(second);

    var env = new Envelope(new Coordinate(10.0, 60.0));
    index.insert(env, first);
    index.insert(env, second);

    assertThat(index.query(env)).hasSize(1);
  }

  /**
   * The per-query dedup accumulator must be a fresh local with no shared/ThreadLocal state, so
   * concurrent readers each get a correct, independently-deduplicated result.
   * <p>
   * This exercises concurrent READS on a populated index — the documented multi-thread-safe case.
   * It deliberately does not perform concurrent writes, which the index contract leaves to the
   * client to synchronize.
   */
  @Test
  void concurrentReadersGetConsistentDedup() throws Exception {
    var index = new EdgeHashGridSpatialIndex();
    var rnd = new Random(99);
    for (int i = 0; i < 80; i++) {
      StreetEdge edge = randomEdge(rnd);
      index.insert(edge.getGeometry(), edge);
    }
    var queryEnvelope = new Envelope(9.99, 10.06, 59.99, 60.04);
    Set<Edge> expected = new HashSet<>(index.query(queryEnvelope));

    int threads = 8;
    var pool = Executors.newFixedThreadPool(threads);
    try {
      var tasks = new ArrayList<Callable<Set<Edge>>>();
      for (int t = 0; t < threads; t++) {
        tasks.add(() -> {
          Set<Edge> last = null;
          for (int i = 0; i < 500; i++) {
            last = new HashSet<>(index.query(queryEnvelope));
          }
          return last;
        });
      }
      var futures = pool.invokeAll(tasks);
      for (Future<Set<Edge>> f : futures) {
        assertThat(f.get()).isEqualTo(expected);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  // ---- helpers ----

  private static StreetEdge edge(double latA, double lonA, double latB, double lonB) {
    StreetVertex a = intersectionVertex("a_%s_%s".formatted(latA, lonA), latA, lonA);
    StreetVertex b = intersectionVertex("b_%s_%s".formatted(latB, lonB), latB, lonB);
    return streetEdge(a, b);
  }

  private static StreetEdge randomEdge(Random rnd) {
    double latA = 60.0 + rnd.nextDouble() * 0.03;
    double lonA = 10.0 + rnd.nextDouble() * 0.05;
    double latB = latA + (rnd.nextDouble() - 0.5) * 0.01;
    double lonB = lonA + (rnd.nextDouble() - 0.5) * 0.01;
    return edge(latA, lonA, latB, lonB);
  }

  private static Envelope randomEnvelope(Random rnd) {
    double lon = 10.0 + rnd.nextDouble() * 0.05;
    double lat = 60.0 + rnd.nextDouble() * 0.03;
    double w = rnd.nextDouble() * 0.01;
    double h = rnd.nextDouble() * 0.01;
    return new Envelope(lon, lon + w, lat, lat + h);
  }

  /** A value-equals stand-in for an id-based transit entity (see AbstractTransitEntity). */
  private static final class IdKeyed {

    private final int id;

    private IdKeyed(int id) {
      this.id = id;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof IdKeyed other && other.id == id;
    }

    @Override
    public int hashCode() {
      return Integer.hashCode(id);
    }
  }
}
