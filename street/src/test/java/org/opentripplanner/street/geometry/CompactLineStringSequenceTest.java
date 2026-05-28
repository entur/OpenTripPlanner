package org.opentripplanner.street.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

class CompactLineStringSequenceTest {

  private static final GeometryFactory GF = new GeometryFactory();
  private static final double TOLERANCE = 0.0000015;

  // Three hops that join end-to-end: hop[i] last coord == hop[i+1] first coord.
  private static final LineString HOP_0 = line(10.0, 59.0, 10.1, 59.1, 10.2, 59.0);
  private static final LineString HOP_1 = line(10.2, 59.0, 10.3, 59.1, 10.4, 59.0);
  // HOP_2 is a straight 2-point line.
  private static final LineString HOP_2 = line(10.4, 59.0, 10.5, 59.0);

  // length = size()+1; entry 0 is 0; arbitrary positive deltas for assertion purposes.
  private static final int[] CUMULATIVE = { 0, 100, 250, 300 };

  @Test
  void sizeAndGetRoundTripEachMember() {
    var seq = CompactLineStringSequence.compact(List.of(HOP_0, HOP_1, HOP_2), CUMULATIVE);
    assertEquals(3, seq.size());
    assertTrue(HOP_0.equalsExact(seq.get(0), TOLERANCE));
    assertTrue(HOP_1.equalsExact(seq.get(1), TOLERANCE));
    assertTrue(HOP_2.equalsExact(seq.get(2), TOLERANCE));
  }

  @Test
  void concatenateAllDeduplicatesSeams() {
    var seq = CompactLineStringSequence.compact(List.of(HOP_0, HOP_1, HOP_2), CUMULATIVE);
    LineString full = seq.concatenate(0, seq.size());

    // 3 + 3 + 2 points, minus the 2 shared seam coordinates = 6.
    assertEquals(6, full.getNumPoints());
    LineString expected = line(
      10.0,
      59.0,
      10.1,
      59.1,
      10.2,
      59.0,
      10.3,
      59.1,
      10.4,
      59.0,
      10.5,
      59.0
    );
    assertTrue(expected.equalsExact(full, TOLERANCE));
  }

  @Test
  void concatenateSubRange() {
    var seq = CompactLineStringSequence.compact(List.of(HOP_0, HOP_1, HOP_2), CUMULATIVE);
    LineString sub = seq.concatenate(1, 3);

    // hop1 (3 pts) + hop2 (2 pts) minus 1 shared seam = 4.
    assertEquals(4, sub.getNumPoints());
    LineString expected = line(10.2, 59.0, 10.3, 59.1, 10.4, 59.0, 10.5, 59.0);
    assertTrue(expected.equalsExact(sub, TOLERANCE));
  }

  @Test
  void concatenateSingleMemberEqualsThatMember() {
    var seq = CompactLineStringSequence.compact(List.of(HOP_0, HOP_1, HOP_2), CUMULATIVE);
    LineString single = seq.concatenate(0, 1);
    assertEquals(HOP_0.getNumPoints(), single.getNumPoints());
    assertTrue(HOP_0.equalsExact(single, TOLERANCE));
  }

  @Test
  void concatenateEmptyRangeReturnsEmptyLineString() {
    var seq = CompactLineStringSequence.compact(List.of(HOP_0, HOP_1, HOP_2), CUMULATIVE);
    assertTrue(seq.concatenate(1, 1).isEmpty());
    assertTrue(seq.concatenate(2, 0).isEmpty());
  }

  @Test
  void emptySequence() {
    var seq = CompactLineStringSequence.compact(List.of(), new int[] { 0 });
    assertEquals(0, seq.size());
    assertTrue(seq.concatenate(0, 0).isEmpty());
  }

  @Test
  void distanceBetweenIsCumulativeDelta() {
    var seq = CompactLineStringSequence.compact(List.of(HOP_0, HOP_1, HOP_2), CUMULATIVE);
    assertEquals(100, seq.distanceBetween(0, 1));
    assertEquals(150, seq.distanceBetween(1, 2));
    assertEquals(300, seq.distanceBetween(0, 3));
    assertEquals(0, seq.distanceBetween(2, 2));
  }

  @Test
  void compactRejectsWrongLengthCumulative() {
    assertThrows(IllegalArgumentException.class, () ->
      CompactLineStringSequence.compact(List.of(HOP_0, HOP_1, HOP_2), new int[] { 0, 100, 250 })
    );
  }

  private static LineString line(double... lonLat) {
    Coordinate[] coords = new Coordinate[lonLat.length / 2];
    for (int i = 0; i < coords.length; i++) {
      coords[i] = new Coordinate(lonLat[i * 2], lonLat[i * 2 + 1]);
    }
    return GF.createLineString(coords);
  }
}
