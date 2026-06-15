package org.opentripplanner.street.linking;

import java.util.List;
import javax.annotation.Nullable;
import org.locationtech.jts.algorithm.locate.SimplePointInAreaLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Location;
import org.opentripplanner.street.model.edge.Area;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Picks which {@link Area} of a multi-area {@link org.opentripplanner.street.model.edge.AreaGroup}
 * owns a newly created visibility edge, so that the edge can borrow the area's name, permission and
 * safety factors.
 * <p>
 * The constants form an ordered fallback chain run by {@link #resolve}: each is tried in turn and
 * the first one that resolves an area wins. A selector returns {@code null} to signal "I cannot
 * decide - try the next one".
 */
enum AreaEdgeSelector {
  /**
   * Returns the first area whose interior or boundary contains the edge midpoint, using a cheap
   * point-in-area scan with no allocation. This is exact whenever the edge lies within a single
   * sub-area (the common case) and avoids the constructive {@code polygon.intersection(line)}
   * overlay - a major allocation source during real-time rental linking. Returns {@code null} when
   * the midpoint falls outside every sub-area (e.g. in a hole, an uncovered part of the group, or
   * for a forced edge leaving the group).
   */
  MIDPOINT_CONTAINS {
    @Override
    @Nullable
    Area select(List<Area> areas, LineString line) {
      var from = line.getCoordinateN(0);
      var to = line.getCoordinateN(1);
      var midpoint = new Coordinate((from.x + to.x) / 2, (from.y + to.y) / 2);
      for (Area area : areas) {
        if (SimplePointInAreaLocator.locate(midpoint, area.getGeometry()) != Location.EXTERIOR) {
          return area;
        }
      }
      return null;
    }
  },
  /**
   * Returns the first area whose intersection with the edge has positive length. This builds a
   * constructive JTS overlay geometry and is therefore relatively expensive; it is used only as a
   * fallback when the cheaper {@link #MIDPOINT_CONTAINS} cannot decide. It still handles edges whose
   * midpoint falls in a hole or uncovered part of the group but which nonetheless cross a sub-area.
   * Returns {@code null} when the edge crosses no sub-area.
   */
  LINE_INTERSECTION {
    @Override
    @Nullable
    Area select(List<Area> areas, LineString line) {
      for (Area area : areas) {
        if (area.getGeometry().intersection(line).getLength() > INTERSECTION_EPSILON) {
          return area;
        }
      }
      return null;
    }
  };

  private static final Logger LOG = LoggerFactory.getLogger(AreaEdgeSelector.class);

  private static final double INTERSECTION_EPSILON = 0.000001;

  /**
   * The area that owns this edge, or {@code null} if this selector cannot decide.
   *
   * @param areas the candidate sub-areas of the group (never empty)
   * @param line  the 2-point segment of the edge being created
   */
  @Nullable
  abstract Area select(List<Area> areas, LineString line);

  /**
   * Resolves which {@link Area} owns the edge. Never returns {@code null}: a single-area group
   * trivially resolves to its only area (skipping the geometric tests and the multi-area warning),
   * and a multi-area group falls back to the first area when no selector matches - unexpected for a
   * multi-area group, so it is logged.
   *
   * @param areas the candidate sub-areas of the group (never empty)
   * @param line  the 2-point segment of the edge being created
   */
  static Area resolve(List<Area> areas, LineString line) {
    if (areas.size() == 1) {
      return areas.getFirst();
    }
    for (AreaEdgeSelector selector : values()) {
      Area hit = selector.select(areas, line);
      if (hit != null) {
        return hit;
      }
    }
    // neither geometric test found an owner: force-linked point from outside the group
    LOG.warn("No intersecting area found. This may indicate a bug.");
    return areas.getFirst();
  }
}
