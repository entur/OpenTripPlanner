package org.opentripplanner.transit.model.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.locationtech.jts.geom.Coordinate;
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
 * When no shape data is available ({@code hopGeometries} is null at construction), the
 * per-hop geometry is synthesized on the fly as a straight line between consecutive stops,
 * and cumulative distances are computed from the stop coordinates. This mirrors the
 * original behaviour on {@link TripPattern}.
 */
final class TripPatternGeometry implements Serializable {

  private final StopPattern stopPattern;

  /**
   * Compressed per-hop geometry, one entry per hop (length = numberOfStops - 1).
   * Null iff the pattern was built without shape data; a synthetic straight line between
   * consecutive stops is returned by {@link #hopGeometry(int)} in that case.
   */
  @Nullable
  private final byte[][] hopGeometries;

  /**
   * Cumulative distance in meters along the pattern from stop 0 up to stop i.
   * Length = numberOfStops; entry 0 is 0.
   */
  private final int[] cumulativeDistanceMeters;

  private TripPatternGeometry(
    StopPattern stopPattern,
    @Nullable byte[][] hopGeometries,
    int[] cumulativeDistanceMeters
  ) {
    this.stopPattern = stopPattern;
    this.hopGeometries = hopGeometries;
    this.cumulativeDistanceMeters = cumulativeDistanceMeters;
  }

  /**
   * Build a {@link TripPatternGeometry} for the given stop pattern. When {@code hopGeometries}
   * is non-null the per-hop distances are summed over each line string; otherwise the straight
   * line between consecutive stops is used. Distances are summed in double and rounded to the
   * nearest meter only when writing each entry of the cumulative table, which bounds the
   * rounding error of any {@code distanceBetween(board, alight)} query to at most 1 meter
   * independent of leg length.
   */
  static TripPatternGeometry of(StopPattern stopPattern, @Nullable List<LineString> hopGeometries) {
    int numberOfStops = stopPattern.getSize();
    double[] cumulativeDouble = new double[numberOfStops];
    byte[][] compressed = null;

    if (hopGeometries != null) {
      compressed = new byte[hopGeometries.size()][];
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
        cumulativeDouble[i + 1] =
          cumulativeDouble[i] +
          SphericalDistanceLibrary.distance(from.getLat(), from.getLon(), to.getLat(), to.getLon());
      }
    }

    int[] cumulativeMeters = new int[numberOfStops];
    for (int i = 0; i < numberOfStops; i++) {
      cumulativeMeters[i] = (int) Math.round(cumulativeDouble[i]);
    }
    return new TripPatternGeometry(stopPattern, compressed, cumulativeMeters);
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
   * Return the geometry of the hop at {@code hopIndex}. When the pattern has shape data the
   * compressed hop is decoded; otherwise a straight line between consecutive stops is
   * synthesized from the stop coordinates.
   */
  LineString hopGeometry(int hopIndex) {
    if (hopGeometries != null) {
      return CompactLineStringUtils.uncompactLineString(hopGeometries[hopIndex], false);
    }
    return GeometryUtils.getGeometryFactory().createLineString(
      new Coordinate[] {
        stopPattern.getStop(hopIndex).getCoordinate().asJtsCoordinate(),
        stopPattern.getStop(hopIndex + 1).getCoordinate().asJtsCoordinate(),
      }
    );
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
   * without shape data. The null-return preserves existing behaviour for GraphQL field
   * resolvers that expose the full-pattern geometry and treat null as "no shape available".
   */
  @Nullable
  LineString concatenatedGeometry() {
    if (hopGeometries == null || hopGeometries.length == 0) {
      return null;
    }
    return geometryBetween(0, hopGeometries.length);
  }
}
