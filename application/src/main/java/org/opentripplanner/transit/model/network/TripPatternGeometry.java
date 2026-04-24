package org.opentripplanner.transit.model.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.street.geometry.CompactLineStringUtils;
import org.opentripplanner.street.geometry.GeometryUtils;
import org.opentripplanner.street.geometry.SphericalDistanceLibrary;
import org.opentripplanner.transit.model.site.StopLocation;

/**
 * Encapsulates the per-hop geometry of a {@link TripPattern} together with a precomputed
 * cumulative distance table. Holding both fields together lets callers look up the distance
 * between any two stop positions in O(1) instead of re-running the haversine sum over the
 * pattern geometry on every leg construction.
 * <p>
 * When no shape data is available at construction, the per-hop geometry is materialized as
 * a straight line between consecutive stops and compressed through the same pipeline as real
 * shape data. A {@code fromShape} flag preserves the null-return of
 * {@link #concatenatedGeometry()} so that GraphQL resolvers can still distinguish
 * "shape available" from "no shape".
 */
final class TripPatternGeometry implements Serializable {

  /**
   * Compressed per-hop geometry, one entry per hop (length = numberOfStops - 1). Always
   * non-null. For shapeless patterns the entries are the compressed form of synthetic
   * 2-point straight lines between consecutive stops.
   */
  private final byte[][] hopGeometries;

  /**
   * Cumulative distance in meters along the pattern from stop 0 up to stop i.
   * Length = numberOfStops; entry 0 is 0.
   */
  private final int[] cumulativeDistanceMeters;

  /**
   * True iff the pattern was built with real shape data. When false, the stored hops are
   * synthesized straight lines and {@link #concatenatedGeometry()} returns {@code null} to
   * preserve the historical "no shape available" sentinel on the public API.
   */
  private final boolean fromShape;

  private TripPatternGeometry(
    byte[][] hopGeometries,
    int[] cumulativeDistanceMeters,
    boolean fromShape
  ) {
    this.hopGeometries = hopGeometries;
    this.cumulativeDistanceMeters = cumulativeDistanceMeters;
    this.fromShape = fromShape;
  }

  /**
   * Build a {@link TripPatternGeometry} for the given stop pattern. When {@code hopGeometries}
   * is non-null the per-hop distances are summed over each line string; otherwise the straight
   * line between consecutive stops is materialized from the stop coordinates. Distances are
   * summed in double and rounded to the nearest meter only when writing each entry of the
   * cumulative table, which bounds the rounding error of any
   * {@code distanceBetween(board, alight)} query to at most 1 meter independent of leg length.
   */
  static TripPatternGeometry of(StopPattern stopPattern, @Nullable List<LineString> hopGeometries) {
    int numberOfStops = stopPattern.getSize();
    double[] cumulativeDouble = new double[numberOfStops];
    byte[][] compressed = new byte[Math.max(numberOfStops - 1, 0)][];
    boolean fromShape = hopGeometries != null;

    if (fromShape) {
      for (int i = 0; i < hopGeometries.size(); i++) {
        LineString hop = hopGeometries.get(i);
        cumulativeDouble[i + 1] =
          cumulativeDouble[i] + GeometryUtils.sumDistances(hop.getCoordinateSequence());
        compressed[i] = CompactLineStringUtils.compactLineString(hop, false);
      }
    } else {
      for (int i = 0; i < numberOfStops - 1; i++) {
        StopLocation from = stopPattern.getStop(i);
        StopLocation to = stopPattern.getStop(i + 1);
        LineString hop = GeometryUtils.makeLineString(from.getCoordinate(), to.getCoordinate());
        cumulativeDouble[i + 1] =
          cumulativeDouble[i] +
          SphericalDistanceLibrary.distance(from.getLat(), from.getLon(), to.getLat(), to.getLon());
        compressed[i] = CompactLineStringUtils.compactLineString(hop, false);
      }
    }

    int[] cumulativeMeters = new int[numberOfStops];
    for (int i = 0; i < numberOfStops; i++) {
      cumulativeMeters[i] = (int) Math.round(cumulativeDouble[i]);
    }
    return new TripPatternGeometry(compressed, cumulativeMeters, fromShape);
  }

  /**
   * Distance in meters along the pattern between the two stop positions. Constant time.
   */
  int distanceBetween(int boardingStopPosition, int alightingStopPosition) {
    return (
      cumulativeDistanceMeters[alightingStopPosition] -
      cumulativeDistanceMeters[boardingStopPosition]
    );
  }

  /**
   * Return the geometry of the hop at {@code hopIndex}. For shapeless patterns the returned
   * geometry is the synthetic straight line between the consecutive stop coordinates, which
   * matches the previous on-the-fly synthesis behaviour.
   */
  LineString hopGeometry(int hopIndex) {
    return CompactLineStringUtils.uncompactLineString(hopGeometries[hopIndex], false);
  }

  /**
   * Return the concatenated hop geometry between the boarding and alighting stop positions.
   * Symmetric with {@link #distanceBetween(int, int)}.
   */
  LineString geometryBetween(int boardingStopPosition, int alightingStopPosition) {
    List<LineString> hops = new ArrayList<>(alightingStopPosition - boardingStopPosition);
    for (int i = boardingStopPosition; i < alightingStopPosition; i++) {
      hops.add(hopGeometry(i));
    }
    return GeometryUtils.concatenateLineStrings(hops);
  }

  /**
   * Return the concatenated geometry of all hops, or {@code null} when the pattern was built
   * without shape data or has no hops. The null-return preserves existing behaviour for
   * GraphQL field resolvers that expose the full-pattern geometry and treat null as "no shape
   * available".
   */
  @Nullable
  LineString concatenatedGeometry() {
    if (!fromShape || hopGeometries.length == 0) {
      return null;
    }
    return geometryBetween(0, hopGeometries.length);
  }
}
