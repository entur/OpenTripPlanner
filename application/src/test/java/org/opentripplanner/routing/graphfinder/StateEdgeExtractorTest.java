package org.opentripplanner.routing.graphfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.opentripplanner.street.model.StreetMode;
import org.opentripplanner.street.model.StreetModelForTest;
import org.opentripplanner.street.model.edge.StreetEdge;
import org.opentripplanner.street.model.vertex.StreetVertex;
import org.opentripplanner.street.search.request.StreetSearchRequest;
import org.opentripplanner.street.search.state.State;

class StateEdgeExtractorTest {

  private static final Instant START_TIME = Instant.parse("2024-01-15T12:00:00Z");

  @Test
  void departAfterEdgesInChronologicalOrder() {
    var graph = createThreeEdgeGraph();
    var state = walkForward(graph);

    var result = StateEdgeExtractor.extractEdgesInChronologicalOrder(state);

    assertEquals(3, result.edges().size());
    assertSame(graph.e1, result.edges().get(0));
    assertSame(graph.e2, result.edges().get(1));
    assertSame(graph.e3, result.edges().get(2));
  }

  @Test
  void arriveByEdgesInChronologicalOrder() {
    var graph = createThreeEdgeGraph();
    var state = walkBackward(graph);

    var result = StateEdgeExtractor.extractEdgesInChronologicalOrder(state);

    assertEquals(3, result.edges().size());
    assertSame(graph.e1, result.edges().get(0));
    assertSame(graph.e2, result.edges().get(1));
    assertSame(graph.e3, result.edges().get(2));
  }

  @Test
  void bothDirectionsProduceIdenticalEdges() {
    var graph = createThreeEdgeGraph();

    var forward = StateEdgeExtractor.extractEdgesInChronologicalOrder(walkForward(graph));
    var backward = StateEdgeExtractor.extractEdgesInChronologicalOrder(walkBackward(graph));

    assertEquals(forward.edges(), backward.edges());
    assertEquals(forward.effectiveWalkDistance(), backward.effectiveWalkDistance(), 1e-9);
  }

  @Test
  void zeroEdgesForInitialState() {
    var v1 = StreetModelForTest.intersectionVertex("StateEdgeExtractorTest_solo", 59.910, 10.750);
    var request = StreetSearchRequest.of()
      .withMode(StreetMode.WALK)
      .withArriveBy(false)
      .withStartTime(START_TIME)
      .build();
    var state = new State(v1, request);

    var result = StateEdgeExtractor.extractEdgesInChronologicalOrder(state);

    assertEquals(0, result.edges().size());
    assertEquals(0.0, result.effectiveWalkDistance(), 1e-9);
  }

  @Test
  void effectiveWalkDistanceIsPositive() {
    var graph = createThreeEdgeGraph();
    var state = walkForward(graph);

    var result = StateEdgeExtractor.extractEdgesInChronologicalOrder(state);

    assertTrue(result.effectiveWalkDistance() > 0);
  }

  private State walkForward(ThreeEdgeGraph g) {
    var request = StreetSearchRequest.of()
      .withMode(StreetMode.WALK)
      .withArriveBy(false)
      .withStartTime(START_TIME)
      .build();
    var s0 = new State(g.v1, request);
    var s1 = g.e1.traverse(s0)[0];
    var s2 = g.e2.traverse(s1)[0];
    return g.e3.traverse(s2)[0];
  }

  private State walkBackward(ThreeEdgeGraph g) {
    var request = StreetSearchRequest.of()
      .withMode(StreetMode.WALK)
      .withArriveBy(true)
      .withStartTime(START_TIME)
      .build();
    var s0 = new State(g.v4, request);
    var s1 = g.e3.traverse(s0)[0];
    var s2 = g.e2.traverse(s1)[0];
    return g.e1.traverse(s2)[0];
  }

  private static ThreeEdgeGraph createThreeEdgeGraph() {
    var v1 = StreetModelForTest.intersectionVertex("StateEdgeExtractorTest_1", 59.910, 10.750);
    var v2 = StreetModelForTest.intersectionVertex("StateEdgeExtractorTest_2", 59.911, 10.751);
    var v3 = StreetModelForTest.intersectionVertex("StateEdgeExtractorTest_3", 59.912, 10.752);
    var v4 = StreetModelForTest.intersectionVertex("StateEdgeExtractorTest_4", 59.913, 10.753);
    var e1 = StreetModelForTest.streetEdge(v1, v2);
    var e2 = StreetModelForTest.streetEdge(v2, v3);
    var e3 = StreetModelForTest.streetEdge(v3, v4);
    return new ThreeEdgeGraph(v1, v2, v3, v4, e1, e2, e3);
  }

  private record ThreeEdgeGraph(
    StreetVertex v1,
    StreetVertex v2,
    StreetVertex v3,
    StreetVertex v4,
    StreetEdge e1,
    StreetEdge e2,
    StreetEdge e3
  ) {}
}
