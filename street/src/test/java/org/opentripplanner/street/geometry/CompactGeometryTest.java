package org.opentripplanner.street.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

class CompactGeometryTest {

  private static final GeometryFactory GF = new GeometryFactory();
  private static final double TOLERANCE = 0.0000015;

  @Test
  void ofEmptyReturnsSingleton() {
    assertSame(CompactGeometry.STRAIGHT_LINE, CompactGeometry.of(new byte[0]));
  }

  @Test
  void ofNullReturnsNull() {
    assertNull(CompactGeometry.of(null));
  }

  @Test
  void compactNullReturnsNull() {
    assertNull(CompactGeometry.compact(null));
  }

  @Test
  void roundTripPreservesAllCoordinatesIncludingEndpoints() {
    LineString original = line(-179.99, 1.12345, 10.123456, 59.654321, 179.99, 1.12345);
    CompactGeometry g = CompactGeometry.compact(original);

    assertEquals(original.getNumPoints(), g.coordinateCount());
    LineString result = g.toLineString(false);
    assertEquals(original.getNumPoints(), result.getNumPoints());
    assertTrue(original.equalsExact(result, TOLERANCE));
  }

  @Test
  void reverseToLineStringMatchesReverseOfOriginal() {
    LineString original = line(10.0, 59.0, 10.1, 59.1, 10.2, 59.0);
    CompactGeometry g = CompactGeometry.compact(original);
    LineString reversed = g.toLineString(true);
    assertTrue(original.reverse().equalsExact(reversed, TOLERANCE));
  }

  @Test
  void twoPointLineRoundTrips() {
    LineString original = line(10.0, 59.0, 10.5, 59.0);
    CompactGeometry g = CompactGeometry.compact(original);
    // compact() pads with (0,0) sentinels, so even a 2-point line is encoded (not the singleton).
    assertNotEquals(CompactGeometry.STRAIGHT_LINE, g);
    LineString result = g.toLineString(false);
    assertEquals(2, result.getNumPoints());
    assertTrue(original.equalsExact(result, TOLERANCE));
  }

  @Test
  void equalsAndHashCodeAreContentBased() {
    LineString ls = line(10.0, 59.0, 10.1, 59.1, 10.2, 59.0);
    CompactGeometry a = CompactGeometry.compact(ls);
    CompactGeometry b = CompactGeometry.compact(ls);
    // Different instances, same content -> equal, same hashCode (enables interning).
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());

    CompactGeometry other = CompactGeometry.compact(line(20.0, 50.0, 20.5, 50.0));
    assertNotEquals(a, other);
  }

  @Test
  void straightLineSingletonHasZeroCoordinates() {
    assertEquals(0, CompactGeometry.STRAIGHT_LINE.coordinateCount());
    assertTrue(CompactGeometry.STRAIGHT_LINE.toLineString(false).isEmpty());
  }

  private static LineString line(double... lonLat) {
    Coordinate[] coords = new Coordinate[lonLat.length / 2];
    for (int i = 0; i < coords.length; i++) {
      coords[i] = new Coordinate(lonLat[i * 2], lonLat[i * 2 + 1]);
    }
    return GF.createLineString(coords);
  }
}
