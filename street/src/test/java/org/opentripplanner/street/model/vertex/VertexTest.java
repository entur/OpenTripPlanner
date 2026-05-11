package org.opentripplanner.street.model.vertex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.street.model.StreetModelFactory;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.edge.StreetEdge;

class VertexTest {

  private static final double LAT = 1.0;
  private static final double LON = 2.0;
  private static final double EPSILON_10_E_MINUS_7 = 1e-7;
  private static final double EPSILON_10_E_MINUS_8 = 1e-8;

  @Test
  void testSameLocation() {
    Vertex v1 = new SimpleVertex("", LAT, LON);
    Vertex v2 = new SimpleVertex("", LAT + EPSILON_10_E_MINUS_8, LON);
    assertTrue(v1.sameLocation(v2));
  }

  @Test
  void testDifferentLocation() {
    Vertex v1 = new SimpleVertex("", LAT, LON);
    Vertex v2 = new SimpleVertex("", LAT + EPSILON_10_E_MINUS_7, LON);
    assertFalse(v1.sameLocation(v2));
  }

  @Test
  void testWgsCoordinate() {
    Vertex v1 = new SimpleVertex("", LAT, LON);
    assertEquals(new WgsCoordinate(LAT, LON), v1.toWgsCoordinate());
  }

  @Test
  void checkIncomingReturnsTrueWhenPredicateMatches() {
    var from = StreetModelFactory.intersectionVertex("from", LAT, LON);
    var to = StreetModelFactory.intersectionVertex("to", LAT + 0.01, LON + 0.01);
    StreetEdge edge = StreetModelFactory.streetEdge(from, to);

    assertTrue(to.checkIncoming(e -> e == edge));
  }

  @Test
  void checkIncomingReturnsFalseWhenPredicateDoesNotMatch() {
    var from = StreetModelFactory.intersectionVertex("from", LAT, LON);
    var to = StreetModelFactory.intersectionVertex("to", LAT + 0.01, LON + 0.01);
    StreetModelFactory.streetEdge(from, to);

    assertFalse(to.checkIncoming(Edge::isCrossing));
  }

  @Test
  void checkIncomingReturnsFalseWhenNoEdges() {
    var v = StreetModelFactory.intersectionVertex("v", LAT, LON);
    assertFalse(v.checkIncoming(e -> true));
  }

  @Test
  void checkOutgoingReturnsTrueWhenPredicateMatches() {
    var from = StreetModelFactory.intersectionVertex("from", LAT, LON);
    var to = StreetModelFactory.intersectionVertex("to", LAT + 0.01, LON + 0.01);
    var edge = StreetModelFactory.streetEdge(from, to);

    assertTrue(from.checkOutgoing(e -> e == edge));
  }

  @Test
  void checkOutgoingReturnsFalseWhenPredicateDoesNotMatch() {
    var from = StreetModelFactory.intersectionVertex("from", LAT, LON);
    var to = StreetModelFactory.intersectionVertex("to", LAT + 0.01, LON + 0.01);
    StreetModelFactory.streetEdge(from, to);

    assertFalse(from.checkOutgoing(Edge::isCrossing));
  }

  @Test
  void checkOutgoingReturnsFalseWhenNoEdges() {
    var v = StreetModelFactory.intersectionVertex("v", LAT, LON);
    assertFalse(v.checkOutgoing(e -> true));
  }
}
