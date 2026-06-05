package org.opentripplanner.street.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;

class HashGridSpatialIndexTest {

  // 1-degree bins so cell = round(coord) and tests can place items in known cells.
  private static HashGridSpatialIndex<Integer> index() {
    return new HashGridSpatialIndex<>(1.0, 1.0);
  }

  @Test
  void singleBinReturnsAllItemsOnce() {
    var idx = index();
    idx.insert(new Envelope(new Coordinate(0.2, 0.1)), 1);
    idx.insert(new Envelope(new Coordinate(0.4, 0.3)), 2);

    List<Integer> result = idx.query(new Envelope(new Coordinate(0.25, 0.2)));

    assertEquals(2, result.size(), "single bin should return each item exactly once");
    assertEquals(Set.of(1, 2), Set.copyOf(result));
  }

  @Test
  void straddlingItemIsDedupedAcrossBins() {
    var idx = index();
    // cell (0,0)
    idx.insert(new Envelope(new Coordinate(0.2, 0.1)), 1);
    // cell (0,0)
    idx.insert(new Envelope(new Coordinate(0.4, 0.3)), 2);
    // Item 3 spans x in [0.4, 0.6] -> indexed in cells (0,0) and (1,0).
    idx.insert(new Envelope(0.4, 0.6, 0.1, 0.1), 3);

    // Query spans cells (0,0) and (1,0), so item 3 is found in both bins.
    List<Integer> result = idx.query(new Envelope(0.3, 0.7, 0.0, 0.2));

    assertEquals(Set.of(1, 2, 3), Set.copyOf(result));
    assertEquals(
      1,
      result
        .stream()
        .filter(i -> i == 3)
        .count(),
      "straddling item must be deduped"
    );
    assertEquals(3, result.size(), "no duplicates across the two touched bins");
  }

  @Test
  void emptyEnvelopeReturnsEmpty() {
    var idx = index();
    idx.insert(new Envelope(new Coordinate(0.2, 0.1)), 1);

    assertTrue(idx.query(new Envelope(new Coordinate(100.0, 100.0))).isEmpty());
  }
}
