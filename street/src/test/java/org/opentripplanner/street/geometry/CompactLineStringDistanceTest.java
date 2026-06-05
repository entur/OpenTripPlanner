package org.opentripplanner.street.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

/**
 * Verifies that {@link CompactLineString#squaredDistanceToPointEquirectangular} (the
 * allocation-free walk used by the vertex linker) returns the same projected distance as the old JTS
 * path it replaced: {@code equirectangularProject(uncompactLineString(...)).distance(point)}. The
 * method returns the squared distance, so the test compares {@code sqrt(walk)} to the JTS distance.
 */
class CompactLineStringDistanceTest {

  private static final GeometryFactory GF = new GeometryFactory();

  /** Oracle: faithful copy of the old {@code VertexLinker.distance()} computation. */
  private static double jtsOracle(
    double ax,
    double ay,
    double bx,
    double by,
    byte[] packed,
    boolean reverse,
    double px,
    double py,
    double xscale
  ) {
    LineString geom = CompactLineString.uncompactLineString(ax, ay, bx, by, packed, reverse);
    Coordinate[] coords = new Coordinate[geom.getNumPoints()];
    for (int i = 0; i < coords.length; i++) {
      Coordinate c = (Coordinate) geom.getCoordinateN(i).clone();
      c.x *= xscale;
      coords[i] = c;
    }
    LineString transformed = GF.createLineString(coords);
    Point p = GF.createPoint(new Coordinate(px * xscale, py));
    return transformed.distance(p);
  }

  private static double walk(
    double ax,
    double ay,
    double bx,
    double by,
    byte[] packed,
    boolean reverse,
    double px,
    double py,
    double xscale
  ) {
    return Math.sqrt(
      CompactLineString.squaredDistanceToPointEquirectangular(
        ax,
        ay,
        bx,
        by,
        packed,
        reverse,
        px,
        py,
        xscale
      )
    );
  }

  private static byte[] pack(double ax, double ay, double bx, double by, double... intermediate) {
    Coordinate[] full = new Coordinate[intermediate.length / 2 + 2];
    full[0] = new Coordinate(ax, ay);
    for (int i = 0; i < intermediate.length / 2; i++) {
      full[i + 1] = new Coordinate(intermediate[i * 2], intermediate[i * 2 + 1]);
    }
    full[full.length - 1] = new Coordinate(bx, by);
    LineString ls = GF.createLineString(full);
    return CompactLineString.compactLineString(ax, ay, bx, by, ls, false);
  }

  @Test
  void straightLine() {
    byte[] packed = pack(0.0, 0.0, 1.0, 1.0);
    double xscale = 0.5;
    // Point off the segment.
    assertParity(0.0, 0.0, 1.0, 1.0, packed, false, 0.4, 0.6, xscale);
    // Point exactly on the segment.
    assertParity(0.0, 0.0, 1.0, 1.0, packed, false, 0.5, 0.5, xscale);
    // Point beyond an endpoint (clamps to the end).
    assertParity(0.0, 0.0, 1.0, 1.0, packed, false, 2.0, 2.0, xscale);
    // Point at a vertex.
    assertParity(0.0, 0.0, 1.0, 1.0, packed, false, 0.0, 0.0, xscale);
  }

  @Test
  void singleIntermediatePoint() {
    byte[] packed = pack(0.0, 0.0, 2.0, 0.0, 1.0, 0.5);
    double xscale = Math.cos(Math.toRadians(59.9));
    assertParity(0.0, 0.0, 2.0, 0.0, packed, false, 1.0, 0.0, xscale);
    assertParity(0.0, 0.0, 2.0, 0.0, packed, false, 1.0, 0.5, xscale);
    assertParity(0.0, 0.0, 2.0, 0.0, packed, false, -1.0, -1.0, xscale);
  }

  @Test
  void multiSegment() {
    byte[] packed = pack(
      10.0,
      59.0,
      10.01,
      59.01,
      10.002,
      59.001,
      10.004,
      59.003,
      10.006,
      59.002,
      10.008,
      59.008
    );
    double xscale = Math.cos(Math.toRadians(59.0));
    assertParity(10.0, 59.0, 10.01, 59.01, packed, false, 10.005, 59.0045, xscale);
    assertParity(10.0, 59.0, 10.01, 59.01, packed, false, 10.0, 59.01, xscale);
  }

  @Test
  void reverseSeedsFromCorrectEndpoint() {
    // Encode with reverse=true: endpoints (B,A), as a back edge would.
    Coordinate[] full = {
      new Coordinate(2.0, 0.0),
      new Coordinate(1.5, 0.4),
      new Coordinate(1.0, 0.1),
      new Coordinate(0.0, 0.0),
    };
    LineString ls = GF.createLineString(full);
    byte[] packed = CompactLineString.compactLineString(0.0, 0.0, 2.0, 0.0, ls, true);
    double xscale = 0.7;
    assertParity(0.0, 0.0, 2.0, 0.0, packed, true, 1.2, 0.3, xscale);
    assertParity(0.0, 0.0, 2.0, 0.0, packed, true, 0.5, 0.5, xscale);
  }

  @Test
  void degenerateRepeatedPoint() {
    // Two consecutive intermediate points rounding to the same fixed-point cell.
    byte[] packed = pack(0.0, 0.0, 1.0, 0.0, 0.5, 0.0000001, 0.5, 0.0000002);
    double xscale = 0.9;
    assertParity(0.0, 0.0, 1.0, 0.0, packed, false, 0.5, 0.001, xscale);
  }

  @Test
  void randomParity() {
    Random rnd = new Random(1234567);
    double maxErr = 0;
    for (int it = 0; it < 1000; it++) {
      double ax = 10.0 + (rnd.nextDouble() - 0.5) * 0.1;
      double ay = 59.9 + (rnd.nextDouble() - 0.5) * 0.1;
      double bx = ax + (rnd.nextDouble() - 0.5) * 0.003;
      double by = ay + (rnd.nextDouble() - 0.5) * 0.003;
      int k = rnd.nextInt(20);
      double[] interm = new double[k * 2];
      for (int i = 0; i < k; i++) {
        double f = (double) (i + 1) / (k + 1);
        interm[i * 2] = ax + f * (bx - ax) + (rnd.nextDouble() - 0.5) * 0.0004;
        interm[i * 2 + 1] = ay + f * (by - ay) + (rnd.nextDouble() - 0.5) * 0.0004;
      }
      byte[] packed = pack(ax, ay, bx, by, interm);
      double px = (ax + bx) / 2 + (rnd.nextDouble() - 0.5) * 0.001;
      double py = (ay + by) / 2 + (rnd.nextDouble() - 0.5) * 0.001;
      double xscale = Math.cos(Math.toRadians(ay));

      double oracle = jtsOracle(ax, ay, bx, by, packed, false, px, py, xscale);
      double got = walk(ax, ay, bx, by, packed, false, px, py, xscale);
      maxErr = Math.max(maxErr, Math.abs(oracle - got));
    }
    // Same algorithm, just inlined; expect agreement to well below the linker's dedup epsilon.
    org.junit.jupiter.api.Assertions.assertTrue(
      maxErr < 1e-12,
      "max divergence from JTS oracle was " + maxErr
    );
  }

  private static void assertParity(
    double ax,
    double ay,
    double bx,
    double by,
    byte[] packed,
    boolean reverse,
    double px,
    double py,
    double xscale
  ) {
    double oracle = jtsOracle(ax, ay, bx, by, packed, reverse, px, py, xscale);
    double got = walk(ax, ay, bx, by, packed, reverse, px, py, xscale);
    assertEquals(oracle, got, 1e-12);
  }
}
