package org.opentripplanner.street.geometry;

import java.io.Serializable;
import java.util.List;
import org.locationtech.jts.geom.LineString;

/**
 * An ordered sequence of compactly-encoded line strings (e.g. the per-hop geometries of a transit
 * pattern), kept in their packed {@code byte[]} form to save memory.
 * <p>
 * This type owns the codec interaction so that callers work only in terms of {@link LineString}s
 * and hop indices: they never see the packed bytes, the fixed-point delta origins, the flat
 * coordinate buffers, or the seam-deduplication arithmetic that {@link CompactLineStringUtils}
 * deals in. Each line string is compacted without endpoint context (its endpoints are stored in
 * the packed form), so a sequence is self-contained and needs no external from/to vertices to
 * decode.
 */
public final class CompactGeometrySequence implements Serializable {

  private final byte[][] packedGeometries;

  private CompactGeometrySequence(byte[][] packedGeometries) {
    this.packedGeometries = packedGeometries;
  }

  /**
   * Compact each line string into its packed form, preserving order. Endpoints are encoded into
   * the packed form (no external endpoint context), so the resulting sequence round-trips on its
   * own.
   */
  public static CompactGeometrySequence compact(List<LineString> geometries) {
    byte[][] packed = new byte[geometries.size()][];
    for (int i = 0; i < geometries.size(); i++) {
      packed[i] = CompactLineStringUtils.compactLineString(geometries.get(i), false);
    }
    return new CompactGeometrySequence(packed);
  }

  /**
   * Number of line strings in the sequence.
   */
  public int size() {
    return packedGeometries.length;
  }

  /**
   * Decode the line string at {@code index}.
   */
  public LineString get(int index) {
    return CompactLineStringUtils.uncompactLineString(packedGeometries[index], false);
  }

  /**
   * Decode and concatenate the line strings in {@code [fromIndex, toIndexExclusive)} into a single
   * {@link LineString}.
   * <p>
   * Consecutive members are assumed to join end-to-end: the last coordinate of one equals the
   * first coordinate of the next (as for transit-pattern hops meeting at a shared stop). That
   * shared seam coordinate is emitted only once. Returns an empty line string when the range is
   * empty or degenerate.
   * <p>
   * Decodes straight into one result buffer rather than materializing an intermediate
   * {@link LineString} per member.
   */
  public LineString concatenate(int fromIndex, int toIndexExclusive) {
    int count = toIndexExclusive - fromIndex;
    if (count <= 0) {
      return GeometryUtils.emptyLineString();
    }
    int totalCoords = 0;
    for (int i = fromIndex; i < toIndexExclusive; i++) {
      totalCoords += CompactLineStringUtils.coordinateCount(packedGeometries[i]);
    }
    // Each seam between two consecutive members repeats one coordinate; emit it only once.
    totalCoords -= (count - 1);
    if (totalCoords <= 0) {
      return GeometryUtils.emptyLineString();
    }
    double[] out = new double[totalCoords * 2];
    int offset = 0;
    for (int i = fromIndex; i < toIndexExclusive; i++) {
      offset = CompactLineStringUtils.decodeDeltaCoordinatesInto(
        packedGeometries[i],
        out,
        offset,
        0,
        0,
        i > fromIndex
      );
    }
    return GeometryUtils.makeLineString(out);
  }
}
