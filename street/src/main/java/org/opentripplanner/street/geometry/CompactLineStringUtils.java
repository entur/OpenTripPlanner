package org.opentripplanner.street.geometry;

import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.LineString;

/**
 * Endpoint-context glue around {@link CompactLineString} for callers (e.g. {@code StreetEdge}) whose
 * endpoints come from external context and are <i>omitted</i> from the packed form to save memory.
 * The Dlugosz/delta engine lives in {@link CompactLineString}; this class only handles the endpoint
 * splice/strip + the stick-to-endpoint check at compaction time.
 * <p>
 * For the self-contained contract (endpoints baked into the packed form) use {@link CompactLineString}
 * directly.
 *
 * @author laurent
 */
public final class CompactLineStringUtils {

  /**
   * Constant to check that line string end points are sticking to given points. 0.000001 is around
   * 1 meter at the equator. Do not use a too low value, ShapeFile builder has some rounding issues
   * and do not ensure perfect equality between endpoints and geometry.
   */
  private static final double EPS = 0.000001;

  /**
   * Singleton representation of a straight-line (where nothing has to be stored). Shares its
   * underlying byte array with {@link CompactLineString#STRAIGHT_LINE} so there is exactly one
   * empty-byte-array instance in the JVM.
   */
  static final byte[] STRAIGHT_LINE_PACKED = CompactLineString.STRAIGHT_LINE.packed();

  private CompactLineStringUtils() {}

  /**
   * Pack a line string into the endpoint-context form: the first and last coordinates are
   * <i>omitted</i> (they must equal the supplied {@code (xa,ya)} and {@code (xb,yb)} endpoints up
   * to {@link #EPS}) and only the intermediate points are stored, delta-coded against the start
   * endpoint.
   *
   * @param xa         X coordinate of end point A
   * @param ya         Y coordinate of end point A
   * @param xb         X coordinate of end point B
   * @param yb         Y coordinate of end point B
   * @param lineString The geometry to compact. The first and last coordinate must equal A and B
   *                   (or, when {@code reverse} is true, B and A).
   * @param reverse    True if A and B are inverted (B is start, A is end).
   */
  public static byte[] compactLineString(
    double xa,
    double ya,
    double xb,
    double yb,
    LineString lineString,
    boolean reverse
  ) {
    if (lineString == null) {
      return null;
    }
    if (lineString.getNumPoints() == 2) {
      return STRAIGHT_LINE_PACKED;
    }
    double x0 = reverse ? xb : xa;
    double y0 = reverse ? yb : ya;
    double x1 = reverse ? xa : xb;
    double y1 = reverse ? ya : yb;
    CoordinateSequence seq = lineString.getCoordinateSequence();
    int n = seq.size();
    /*
     * Check if the lineString is really sticking to the given end-points. TODO: If this is not
     * guaranteed, store all delta (from 0 to n-1) -- but how to mark it? A prefix?
     */
    if (
      Math.abs(x0 - seq.getX(0)) > EPS ||
      Math.abs(y0 - seq.getY(0)) > EPS ||
      Math.abs(x1 - seq.getX(n - 1)) > EPS ||
      Math.abs(y1 - seq.getY(n - 1)) > EPS
    ) {
      throw new IllegalArgumentException(
        "CompactLineStringUtils geometry must stick to given end points. If you need to relax this, please read source code."
      );
    }
    int oix = CompactLineString.toFixedPoint(x0);
    int oiy = CompactLineString.toFixedPoint(y0);
    return CompactLineString.packIntermediateDeltas(lineString, oix, oiy);
  }

  /**
   * Decode a line string previously produced by
   * {@link #compactLineString(double, double, double, double, LineString, boolean)}, splicing the
   * supplied endpoints back around the decoded intermediate points.
   *
   * @param xa           X coordinate of end point A
   * @param ya           Y coordinate of end point A
   * @param xb           X coordinate of end point B
   * @param yb           Y coordinate of end point B
   * @param packedCoords The byte array to uncompact
   */
  public static LineString uncompactLineString(
    double xa,
    double ya,
    double xb,
    double yb,
    byte[] packedCoords,
    boolean reverse
  ) {
    double x0 = reverse ? xb : xa;
    double y0 = reverse ? yb : ya;
    double x1 = reverse ? xa : xb;
    double y1 = reverse ? ya : yb;
    int intermediateCount = packedCoords == null
      ? 0
      : DlugoszVarLenIntPacker.countValues(packedCoords) / 2;
    double[] c = new double[(intermediateCount + 2) * 2];
    c[0] = x0;
    c[1] = y0;
    if (intermediateCount > 0) {
      int oix = CompactLineString.toFixedPoint(x0);
      int oiy = CompactLineString.toFixedPoint(y0);
      CompactLineString.decodeInto(packedCoords, c, 2, oix, oiy, false);
    }
    c[c.length - 2] = x1;
    c[c.length - 1] = y1;
    LineString out = GeometryUtils.makeLineString(c);
    return reverse ? out.reverse() : out;
  }

  /**
   * Distance from a point to an endpoint-context compacted line string, computed directly on the
   * packed form without materializing a {@link LineString} or any {@link
   * org.locationtech.jts.geom.Coordinate}.
   * <p>
   * This is the allocation-free equivalent of {@code VertexLinker.distance()}: it uses the same
   * "fast somewhat inaccurate" local equirectangular projection (only the x axis is scaled by
   * {@code xscale}; distances come out in latitude degrees) and the same per-segment point-to-segment
   * math JTS {@code DistanceOp} runs internally, but it streams the segments straight from the packed
   * deltas via {@link DlugoszVarLenIntPacker.Decoder} instead of decoding into an array first.
   * <p>
   * The result is order-invariant, so {@code reverse} only affects which endpoint seeds the delta
   * chain (it must match the value the bytes were encoded with); the minimum distance over the
   * segment set is identical regardless.
   *
   * @param xa      X (longitude) of end point A
   * @param ya      Y (latitude) of end point A
   * @param xb      X (longitude) of end point B
   * @param yb      Y (latitude) of end point B
   * @param packedCoords the endpoint-context packed intermediate points (may be empty/null for a
   *                     straight line)
   * @param reverse True if A and B are inverted (B is start, A is end) — must match compaction.
   * @param px      X (longitude) of the query point
   * @param py      Y (latitude) of the query point
   * @param xscale  cos(latitude) of the projection centre, as used by the linker
   * @return the projected distance in latitude degrees
   */
  public static double distanceToPointEquirectangular(
    double xa,
    double ya,
    double xb,
    double yb,
    byte[] packedCoords,
    boolean reverse,
    double px,
    double py,
    double xscale
  ) {
    double x0 = reverse ? xb : xa;
    double y0 = reverse ? yb : ya;
    double x1 = reverse ? xa : xb;
    double y1 = reverse ? ya : yb;

    double pxs = px * xscale;
    double prevX = x0 * xscale;
    double prevY = y0;
    double minDistSq = Double.POSITIVE_INFINITY;

    if (packedCoords != null && packedCoords.length > 0) {
      int oix = CompactLineString.toFixedPoint(x0);
      int oiy = CompactLineString.toFixedPoint(y0);
      var decoder = new DlugoszVarLenIntPacker.Decoder(packedCoords);
      while (decoder.hasNext()) {
        oix += decoder.next();
        oiy += decoder.next();
        double curX = CompactLineString.toFloatingPoint(oix) * xscale;
        double curY = CompactLineString.toFloatingPoint(oiy);
        double distSq = segmentDistanceSq(pxs, py, prevX, prevY, curX, curY);
        if (distSq < minDistSq) {
          minDistSq = distSq;
        }
        prevX = curX;
        prevY = curY;
      }
    }

    // Final implicit segment to the other endpoint (the only segment for a straight line).
    double endX = x1 * xscale;
    double distSq = segmentDistanceSq(pxs, py, prevX, prevY, endX, y1);
    if (distSq < minDistSq) {
      minDistSq = distSq;
    }
    return Math.sqrt(minDistSq);
  }

  /**
   * Squared euclidean distance from point {@code (px,py)} to the segment {@code (ax,ay)-(bx,by)} in
   * the projected plane. Same projection-and-clamp the JTS distance computation performs per segment.
   */
  private static double segmentDistanceSq(
    double px,
    double py,
    double ax,
    double ay,
    double bx,
    double by
  ) {
    double dx = bx - ax;
    double dy = by - ay;
    double lengthSq = dx * dx + dy * dy;
    double t = lengthSq == 0.0 ? 0.0 : ((px - ax) * dx + (py - ay) * dy) / lengthSq;
    if (t < 0.0) {
      t = 0.0;
    } else if (t > 1.0) {
      t = 1.0;
    }
    double closestX = ax + t * dx;
    double closestY = ay + t * dy;
    double ex = px - closestX;
    double ey = py - closestY;
    return ex * ex + ey * ey;
  }
}
