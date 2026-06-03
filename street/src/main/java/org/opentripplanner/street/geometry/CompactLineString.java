package org.opentripplanner.street.geometry;

import java.io.Serializable;
import java.util.Arrays;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.utils.lang.IntUtils;

/**
 * A single delta-packed line string.
 * <p>
 * The same packed-bytes format and the same delta engine ({@link #packIntermediateDeltas} /
 * {@link #decodeInto}) serve two encoding contracts:
 * <ul>
 *   <li><b>self-contained</b> &mdash; the line string's endpoints are encoded into the packed form
 *       (by padding with {@code (0,0)} sentinels). Decode is fully described by the bytes alone.
 *       Exposed as the instance API {@link #of(LineString)} / {@link #toLineString(boolean)}, where a
 *       {@code CompactLineString} value object owns the bytes. Used by
 *       {@link CompactLineStringSequence}.</li>
 *   <li><b>endpoint-context</b> &mdash; the line string's endpoints are <i>omitted</i> from the
 *       packed form and re-supplied at decode time from external context (e.g. a street edge's
 *       from/to vertex). Exposed as the static, {@code byte[]}-based API
 *       {@link #compactLineString} / {@link #uncompactLineString} /
 *       {@link #squaredDistanceToPointEquirectangular}. Used by {@code StreetEdge}, which stores the
 *       raw bytes directly (no wrapper object per edge).</li>
 * </ul>
 */
public final class CompactLineString implements Serializable {

  /**
   * Multiplier for fixed-float representation. For lat/lon CRS, 1e6 leads to a precision of 0.11
   * meter at a minimum (at the equator).
   */
  private static final double FIXED_FLOAT_MULT = 1.0e6;

  /**
   * Encode a floating-point coordinate as a fixed-point integer at the precision implied by the
   * codec's {@code FIXED_FLOAT_MULT}. Rounds to the nearest integer.
   */
  private static int toFixedPoint(double v) {
    return IntUtils.round(v * FIXED_FLOAT_MULT);
  }

  /**
   * Decode a fixed-point integer back to the corresponding floating-point coordinate.
   */
  private static double toFloatingPoint(int v) {
    return v / FIXED_FLOAT_MULT;
  }

  /**
   * Shared instance for a 2-point straight line (nothing to store). Reused everywhere a straight
   * line is encoded, both in self-contained and endpoint-context contexts.
   */
  static final CompactLineString STRAIGHT_LINE = new CompactLineString(new byte[0]);

  /**
   * Empty packed byte array for a straight line in the endpoint-context contract. Shares the
   * underlying array with {@link #STRAIGHT_LINE} so there is exactly one empty-byte-array instance.
   */
  static final byte[] STRAIGHT_LINE_PACKED = STRAIGHT_LINE.packed();

  /**
   * Constant to check that line string end points are sticking to given points. 0.000001 is around
   * 1 meter at the equator. Do not use a too low value, ShapeFile builder has some rounding issues
   * and do not ensure perfect equality between endpoints and geometry.
   */
  private static final double EPS = 0.000001;

  private final byte[] packed;

  private CompactLineString(byte[] packed) {
    this.packed = packed;
  }

  /**
   * Wrap an already-packed byte array. Returns the shared {@link #STRAIGHT_LINE} singleton for an
   * empty array. {@code null} input maps to {@code null}.
   */
  static CompactLineString wrap(byte[] packed) {
    if (packed == null) {
      return null;
    }
    return packed.length == 0 ? STRAIGHT_LINE : new CompactLineString(packed);
  }

  /**
   * Compact a line string with no external endpoint context: endpoints are encoded into the packed
   * form by padding the input with two {@code (0,0)} sentinels and delta-encoding every original
   * coordinate against that origin.
   */
  static CompactLineString of(LineString lineString) {
    if (lineString == null) {
      return null;
    }
    LineString padded = GeometryUtils.addStartEndCoordinatesToLineString(
      new Coordinate(0.0, 0.0),
      lineString,
      new Coordinate(0.0, 0.0)
    );
    return wrap(packIntermediateDeltas(padded, 0, 0));
  }

  /**
   * Decode this self-contained packed line string. The deltas are accumulated from fixed-point
   * origin {@code (0,0)} so every original coordinate (including endpoints) round-trips.
   *
   * @param reverse if {@code true}, the resulting line string is reversed.
   */
  LineString toLineString(boolean reverse) {
    int n = coordinateCount();
    if (n == 0) {
      return GeometryUtils.emptyLineString();
    }
    double[] c = new double[n * 2];
    decodeInto(packed, c, 0, 0, 0, false);
    LineString out = GeometryUtils.makeLineString(c);
    return reverse ? out.reverse() : out;
  }

  /** Number of coordinates encoded, without decoding them. */
  int coordinateCount() {
    return DlugoszVarLenIntPacker.countValues(packed) / 2;
  }

  /** Package-private accessor for the endpoint-context glue and {@link CompactLineStringSequence}. */
  byte[] packed() {
    return packed;
  }

  // ---- the single copy of the decode/encode engine ---------------------------------------

  /**
   * Decode delta-encoded coordinate pairs from {@code packed} into {@code out} starting at
   * {@code offset}, accumulating from fixed-point origin {@code (oix, oiy)}. Returns the new write
   * offset (one past the last double written).
   * <p>
   * Streams through the packed bytes without allocating an intermediate {@code int[]} or boxing.
   * This is the single home of the decode logic; both the self-contained {@link #toLineString(boolean)}
   * and the endpoint-context {@link #uncompactLineString} call it.
   *
   * @param packed         the compact line string, or {@code null} / empty for a no-op
   * @param out            target array to write decoded coordinates into (flat: [x0, y0, x1, y1, …])
   * @param offset         starting index in {@code out} to write to
   * @param oix            initial x in fixed-point (start of delta chain)
   * @param oiy            initial y in fixed-point (start of delta chain)
   * @param skipFirstCoord skip writing the first decoded coordinate. Deltas still accumulate so the
   *                       remaining coordinates land at their correct absolute positions. Used to
   *                       drop the shared seam coord at hop seams in transit-pattern leg-geometry
   *                       concatenation.
   * @return the offset of the first unwritten slot in {@code out}
   */
  static int decodeInto(
    byte[] packed,
    double[] out,
    int offset,
    int oix,
    int oiy,
    boolean skipFirstCoord
  ) {
    if (packed == null || packed.length == 0) {
      return offset;
    }
    int writeIdx = offset;
    boolean skip = skipFirstCoord;
    var decoder = new DlugoszVarLenIntPacker.Decoder(packed);
    while (decoder.hasNext()) {
      oix += decoder.next();
      oiy += decoder.next();
      if (skip) {
        skip = false;
        continue;
      }
      out[writeIdx++] = toFloatingPoint(oix);
      out[writeIdx++] = toFloatingPoint(oiy);
    }
    return writeIdx;
  }

  /**
   * Encode the <i>intermediate</i> points of {@code lineString} (indices 1..n-2) as fixed-point
   * deltas accumulated from {@code (oix, oiy)} and produce the Dlugosz-packed bytes. The first and
   * last coordinates of the input are <b>not</b> stored.
   * <p>
   * Single home of the encode logic; both the self-contained {@link #of(LineString)} (which
   * passes a padded line string starting and ending at {@code (0,0)}) and the endpoint-context
   * {@link #compactLineString} (which passes the vertex coordinates as origin) delegate here.
   */
  private static byte[] packIntermediateDeltas(LineString lineString, int oix, int oiy) {
    CoordinateSequence seq = lineString.getCoordinateSequence();
    int n = seq.size();
    int[] coords = new int[(n - 2) * 2];
    for (int i = 1; i < n - 1; i++) {
      // Round to fixed point before taking the delta to prevent rounding errors from accumulating.
      int ix = toFixedPoint(seq.getX(i));
      int iy = toFixedPoint(seq.getY(i));
      coords[(i - 1) * 2] = ix - oix;
      coords[(i - 1) * 2 + 1] = iy - oiy;
      oix = ix;
      oiy = iy;
    }
    return DlugoszVarLenIntPacker.pack(coords);
  }

  // ---- endpoint-context contract: endpoints omitted, supplied externally ------------------
  // Used by StreetEdge, whose endpoints come from its from/to vertices. These operate on a raw
  // packed byte[] (StreetEdge stores the bytes directly, not a CompactLineString instance) so they
  // add no per-edge wrapper objects.

  /**
   * Pack a line string into the endpoint-context form: the first and last coordinates are
   * <i>omitted</i> (they must equal the supplied {@code (xa,ya)} and {@code (xb,yb)} endpoints up to
   * {@link #EPS}) and only the intermediate points are stored, delta-coded against the start
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
        "CompactLineString geometry must stick to given end points. If you need to relax this, please read source code."
      );
    }
    int oix = toFixedPoint(x0);
    int oiy = toFixedPoint(y0);
    return packIntermediateDeltas(lineString, oix, oiy);
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
      int oix = toFixedPoint(x0);
      int oiy = toFixedPoint(y0);
      decodeInto(packedCoords, c, 2, oix, oiy, false);
    }
    c[c.length - 2] = x1;
    c[c.length - 1] = y1;
    LineString out = GeometryUtils.makeLineString(c);
    return reverse ? out.reverse() : out;
  }

  /**
   * Squared distance from a point to an endpoint-context compacted line string, computed directly on
   * the packed form without materializing a {@link LineString} or any {@link
   * org.locationtech.jts.geom.Coordinate}.
   * <p>
   * This is the allocation-free equivalent of the old {@code VertexLinker.distance()}: it uses the
   * same "fast somewhat inaccurate" local equirectangular projection (only the x axis is scaled by
   * {@code xscale}; distances come out in latitude degrees) and the same per-segment point-to-segment
   * math JTS {@code DistanceOp} runs internally, but it streams the segments straight from the packed
   * deltas via {@link DlugoszVarLenIntPacker.Decoder} instead of decoding into an array first.
   * <p>
   * The <b>square</b> of the distance is returned: the linker only ever orders edges and applies
   * thresholds, both of which are monotonic in the distance, so the per-candidate {@code sqrt} is
   * unnecessary (callers square their thresholds instead).
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
   * @return the squared projected distance in latitude degrees squared
   */
  public static double squaredDistanceToPointEquirectangular(
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
      int oix = toFixedPoint(x0);
      int oiy = toFixedPoint(y0);
      var decoder = new DlugoszVarLenIntPacker.Decoder(packedCoords);
      while (decoder.hasNext()) {
        oix += decoder.next();
        oiy += decoder.next();
        double curX = toFloatingPoint(oix) * xscale;
        double curY = toFloatingPoint(oiy);
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
    return minDistSq;
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

  // ---- value semantics: enables sharing/interning of identical geometries -----------------

  @Override
  public boolean equals(Object o) {
    return o instanceof CompactLineString g && Arrays.equals(packed, g.packed);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(packed);
  }
}
