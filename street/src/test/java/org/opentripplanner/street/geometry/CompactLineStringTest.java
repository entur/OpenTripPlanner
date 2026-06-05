package org.opentripplanner.street.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.street.geometry.GeometryUtils.makeLineString;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.LineString;

class CompactLineStringTest {

  private static final double TOLERANCE = 0.0000015;

  @Test
  void ofEmptyReturnsSingleton() {
    assertSame(CompactLineString.STRAIGHT_LINE, CompactLineString.wrap(new byte[0]));
  }

  @Test
  void wrapNullReturnsNull() {
    assertNull(CompactLineString.wrap(null));
  }

  @Test
  void ofNullReturnsNull() {
    assertNull(CompactLineString.of(null));
  }

  @Test
  void roundTripPreservesAllCoordinatesIncludingEndpoints() {
    LineString original = makeLineString(-179.99, 1.12345, 10.123456, 59.654321, 179.99, 1.12345);
    CompactLineString g = CompactLineString.of(original);

    assertEquals(original.getNumPoints(), g.coordinateCount());
    LineString result = g.toLineString(false);
    assertEquals(original.getNumPoints(), result.getNumPoints());
    assertTrue(original.equalsExact(result, TOLERANCE));
  }

  @Test
  void reverseToLineStringMatchesReverseOfOriginal() {
    LineString original = makeLineString(10.0, 59.0, 10.1, 59.1, 10.2, 59.0);
    CompactLineString g = CompactLineString.of(original);
    LineString reversed = g.toLineString(true);
    assertTrue(original.reverse().equalsExact(reversed, TOLERANCE));
  }

  @Test
  void twoPointLineRoundTrips() {
    LineString original = makeLineString(10.0, 59.0, 10.5, 59.0);
    CompactLineString g = CompactLineString.of(original);
    // compact() pads with (0,0) sentinels, so even a 2-point line is encoded (not the singleton).
    assertNotEquals(CompactLineString.STRAIGHT_LINE, g);
    LineString result = g.toLineString(false);
    assertEquals(2, result.getNumPoints());
    assertTrue(original.equalsExact(result, TOLERANCE));
  }

  @Test
  void equalsAndHashCodeAreContentBased() {
    LineString ls = makeLineString(10.0, 59.0, 10.1, 59.1, 10.2, 59.0);
    CompactLineString a = CompactLineString.of(ls);
    CompactLineString b = CompactLineString.of(ls);
    // Different instances, same content -> equal, same hashCode (enables interning).
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());

    CompactLineString other = CompactLineString.of(makeLineString(20.0, 50.0, 20.5, 50.0));
    assertNotEquals(a, other);
  }

  @Test
  void straightLineSingletonHasZeroCoordinates() {
    assertEquals(0, CompactLineString.STRAIGHT_LINE.coordinateCount());
    assertTrue(CompactLineString.STRAIGHT_LINE.toLineString(false).isEmpty());
  }

  // ---- endpoint-context contract (byte[]-based, used by StreetEdge) ------------------------

  private static final double X_0 = 1.111111111;
  private static final double Y_0 = 0.123456789;
  private static final double X_1 = 2.0;
  private static final double Y_1 = 0.0;

  @Test
  void endpointContextTwoPointLineUsesStraightLineSingleton() {
    LineString ls = makeLineString(X_0, Y_0, X_1, Y_1);
    byte[] coords = CompactLineString.compactLineString(X_0, Y_0, X_1, Y_1, ls, false);
    // ==, not equals: a 2-point line stores nothing, so it reuses the shared empty array.
    assertSame(CompactLineString.STRAIGHT_LINE_PACKED, coords);
    LineString ls2 = CompactLineString.uncompactLineString(X_0, Y_0, X_1, Y_1, coords, false);
    assertTrue(ls.equalsExact(ls2, TOLERANCE));
  }

  @Test
  void endpointContextRoundTripWithReverse() {
    LineString ls = makeLineString(X_0, Y_0, -179.99, 1.12345, 179.99, 1.12345, X_1, Y_1);
    byte[] coords = CompactLineString.compactLineString(X_0, Y_0, X_1, Y_1, ls, false);
    assertNotSame(CompactLineString.STRAIGHT_LINE_PACKED, coords);
    LineString ls2 = CompactLineString.uncompactLineString(X_0, Y_0, X_1, Y_1, coords, false);
    assertTrue(ls.equalsExact(ls2, TOLERANCE));

    // Reverse mode: encoding (B,A) with reverse=true yields the same bytes, and decoding with
    // reverse=true reproduces the reversed line string.
    LineString reversed = ls.reverse();
    byte[] coordsRev = CompactLineString.compactLineString(X_1, Y_1, X_0, Y_0, ls, true);
    assertEquals(coords.length, coordsRev.length);
    for (int i = 0; i < coords.length; i++) {
      assertEquals(coords[i], coordsRev[i]);
    }
    LineString ls3 = CompactLineString.uncompactLineString(X_1, Y_1, X_0, Y_0, coordsRev, true);
    assertTrue(reversed.equalsExact(ls3, TOLERANCE));
  }
}
