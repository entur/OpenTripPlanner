package org.opentripplanner.street.linking;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.opentripplanner.street.geometry.GeometryUtils;
import org.opentripplanner.street.model.edge.Area;

class AreaEdgeSelectorTest {

  private static final GeometryFactory GF = GeometryUtils.getGeometryFactory();

  // Two squares sharing the edge x=10: A covers x in [0,10], B covers x in [10,20], both y in [0,10].
  private static final Area A = area(square(0, 10));
  private static final Area B = area(square(10, 20));
  private static final List<Area> AREAS = List.of(A, B);

  @Test
  void midpointPicksAreaContainingMidpoint() {
    // midpoint (5,5) is inside A
    assertSame(A, AreaEdgeSelector.MIDPOINT_CONTAINS.select(AREAS, line(2, 5, 8, 5)));
    // midpoint (15,5) is inside B
    assertSame(B, AreaEdgeSelector.MIDPOINT_CONTAINS.select(AREAS, line(12, 5, 18, 5)));
  }

  @Test
  void midpointReturnsNullWhenMidpointOutsideAllAreas() {
    // midpoint (23,5) is outside both squares, even though the line clips B
    assertNull(AreaEdgeSelector.MIDPOINT_CONTAINS.select(AREAS, line(16, 5, 30, 5)));
  }

  @Test
  void intersectionPicksFirstAreaTheLineCrosses() {
    // line clips B (x in [16,20] at y=5) but not A
    assertSame(B, AreaEdgeSelector.LINE_INTERSECTION.select(AREAS, line(16, 5, 30, 5)));
  }

  @Test
  void intersectionReturnsNullWhenLineCrossesNothing() {
    assertNull(AreaEdgeSelector.LINE_INTERSECTION.select(AREAS, line(25, 25, 30, 30)));
  }

  @Test
  void resolvesViaMidpoint() {
    // midpoint (5,5) is inside A
    assertSame(A, AreaEdgeSelector.resolve(AREAS, line(2, 5, 8, 5)));
  }

  @Test
  void resolvesViaIntersectionFallback() {
    // midpoint (23,5) is outside both squares, but the line clips B -> intersection fallback wins
    assertSame(B, AreaEdgeSelector.resolve(AREAS, line(16, 5, 30, 5)));
  }

  @Test
  void resolvesViaFirstAreaFallback() {
    // neither geometric test matches -> terminal first-area fallback
    assertSame(A, AreaEdgeSelector.resolve(AREAS, line(25, 25, 30, 30)));
  }

  @Test
  void singleAreaGroupReturnsOnlyAreaWithoutGeometricTests() {
    // The line neither contains B's midpoint nor crosses B, yet a single-area group resolves to its
    // only area via the size-1 shortcut.
    assertSame(B, AreaEdgeSelector.resolve(List.of(B), line(2, 5, 8, 5)));
  }

  @Test
  void midpointTakesPrecedenceOverIntersection() {
    // The line crosses A first (x in [9,10]) but its midpoint (14,5) lies in B. Intersection would
    // pick A; midpoint runs first and picks B, proving the chain order.
    assertSame(B, AreaEdgeSelector.resolve(AREAS, line(9, 5, 19, 5)));
  }

  private static Area area(Polygon geometry) {
    var area = new Area();
    area.setGeometry(geometry);
    return area;
  }

  private static Polygon square(double minX, double maxX) {
    return GF.createPolygon(
      new Coordinate[] {
        new Coordinate(minX, 0),
        new Coordinate(maxX, 0),
        new Coordinate(maxX, 10),
        new Coordinate(minX, 10),
        new Coordinate(minX, 0),
      }
    );
  }

  private static LineString line(double x1, double y1, double x2, double y2) {
    return GF.createLineString(new Coordinate[] { new Coordinate(x1, y1), new Coordinate(x2, y2) });
  }
}
