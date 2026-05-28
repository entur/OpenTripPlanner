package org.opentripplanner.street.geometry;

import java.io.Serializable;
import java.util.List;
import org.locationtech.jts.geom.LineString;

/**
 * An ordered sequence of {@link CompactGeometry} members (e.g. the per-hop geometries of a transit
 * pattern), kept in their packed form to save memory.
 * <p>
 * Callers work only in terms of {@link LineString}s and indices: they never see the packed bytes,
 * the fixed-point delta origins, the flat coordinate buffers, or the seam-deduplication arithmetic
 * that {@link CompactGeometry}'s engine deals in. Each member is encoded self-contained (its
 * endpoints are stored in the packed form), so a sequence round-trips on its own.
 */
public final class CompactGeometrySequence implements Serializable {

  private final CompactGeometry[] geometries;

  private CompactGeometrySequence(CompactGeometry[] geometries) {
    this.geometries = geometries;
  }

  /**
   * Compact each line string into a {@link CompactGeometry}, preserving order.
   */
  public static CompactGeometrySequence compact(List<LineString> geometries) {
    var packed = new CompactGeometry[geometries.size()];
    for (int i = 0; i < geometries.size(); i++) {
      packed[i] = CompactGeometry.compact(geometries.get(i));
    }
    return new CompactGeometrySequence(packed);
  }

  /**
   * Number of line strings in the sequence.
   */
  public int size() {
    return geometries.length;
  }

  /**
   * Decode the line string at {@code index}.
   */
  public LineString get(int index) {
    return geometries[index].toLineString(false);
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
      totalCoords += geometries[i].coordinateCount();
    }
    // Each seam between two consecutive members repeats one coordinate; emit it only once.
    totalCoords -= (count - 1);
    if (totalCoords <= 0) {
      return GeometryUtils.emptyLineString();
    }
    double[] out = new double[totalCoords * 2];
    int offset = 0;
    for (int i = fromIndex; i < toIndexExclusive; i++) {
      offset = CompactGeometry.decodeInto(geometries[i].packed(), out, offset, 0, 0, i > fromIndex);
    }
    return GeometryUtils.makeLineString(out);
  }
}
