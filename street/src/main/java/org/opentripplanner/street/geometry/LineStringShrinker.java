package org.opentripplanner.street.geometry;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;

public class LineStringShrinker {

  /**
   * Factor by which a candidate segment is shrunk at each end before the containment test. Floating
   * point math cannot represent boundaries precisely, so the segment between two boundary points is
   * pulled slightly inward to make the test robust.
   */
  private static final double AREA_INTERSECTION_SHRINKING = 0.0001;

  public static LineString shrink(Coordinate from, Coordinate to) {
    var dx = AREA_INTERSECTION_SHRINKING * (to.x - from.x);
    var dy = AREA_INTERSECTION_SHRINKING * (to.y - from.y);
    return GeometryUtils.makeLineString(from.x + dx, from.y + dy, to.x - dx, to.y - dy);
  }
}
