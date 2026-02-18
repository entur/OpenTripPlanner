package org.opentripplanner.graph_builder.module.osm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.street.model.StreetMode.BIKE;
import static org.opentripplanner.street.model.StreetMode.CAR;
import static org.opentripplanner.street.model.StreetMode.WALK;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.astar.model.GraphPath;
import org.opentripplanner.astar.model.ShortestPathTree;
import org.opentripplanner.osm.DefaultOsmProvider;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.street.model.StreetMode;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.model.vertex.VertexLabel;
import org.opentripplanner.street.search.intersection_model.ConstantIntersectionTraversalCalculator;
import org.opentripplanner.street.search.intersection_model.IntersectionTraversalCalculator;
import org.opentripplanner.street.search.request.StreetSearchRequest;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.street.search.strategy.DominanceFunctions;
import org.opentripplanner.streetadapter.EuclideanRemainingWeightHeuristic;
import org.opentripplanner.streetadapter.StreetSearchBuilder;
import org.opentripplanner.test.support.ResourceLoader;

public class TriangleInequalityTest {

  private static Graph graph;

  private static final IntersectionTraversalCalculator CALCULATOR =
    new ConstantIntersectionTraversalCalculator(10.0);

  private Vertex start;
  private Vertex end;

  @BeforeAll
  public static void onlyOnce() {
    graph = new Graph();

    var file = ResourceLoader.of(TriangleInequalityTest.class).file("NYC_small.osm.pbf");
    var provider = new DefaultOsmProvider(file, true);
    var osmModule = OsmModuleTestFactory.of(provider)
      .withGraph(graph)
      .builder()
      .withAreaVisibility(true)
      .build();

    osmModule.buildGraph();
  }

  @BeforeEach
  public void before() {
    start = graph.getVertex(VertexLabel.osm(1919595913));
    end = graph.getVertex(VertexLabel.osm(42448554));
  }

  @Test
  public void testTriangleInequalityDefaultModes() {
    checkTriangleInequality(StreetMode.WALK);
  }

  @Test
  public void testTriangleInequalityWalkingOnly() {
    checkTriangleInequality(StreetMode.WALK);
  }

  @Test
  public void testTriangleInequalityDrivingOnly() {
    checkTriangleInequality(CAR);
  }

  @Test
  public void testTriangleInequalityWalkTransit() {
    checkTriangleInequality(WALK);
  }

  @Test
  public void testTriangleInequalityWalkBike() {
    checkTriangleInequality(BIKE);
  }

  @Test
  public void testTriangleInequalityWalkingOnlyBasicSPT() {
    checkTriangleInequality(WALK);
  }

  @Test
  public void testTriangleInequalityDrivingOnlyBasicSPT() {
    checkTriangleInequality(CAR);
  }

  @Test
  public void testTriangleInequalityWalkTransitBasicSPT() {
    checkTriangleInequality(WALK);
  }

  @Test
  public void testTriangleInequalityWalkBikeBasicSPT() {
    checkTriangleInequality(BIKE);
  }

  @Test
  public void testTriangleInequalityWalkingOnlyMultiSPT() {
    checkTriangleInequality(WALK);
  }

  @Test
  public void testTriangleInequalityDrivingOnlyMultiSPT() {
    checkTriangleInequality(CAR);
  }

  @Test
  public void testTriangleInequalityWalkTransitMultiSPT() {
    checkTriangleInequality(WALK);
  }

  @Test
  public void testTriangleInequalityWalkBikeMultiSPT() {
    checkTriangleInequality(BIKE);
  }

  private GraphPath<State, Edge, Vertex> getPath(
    StreetSearchRequest req,
    Edge startBackEdge,
    Vertex u,
    Vertex v
  ) {
    return StreetSearchBuilder.of()
      .withHeuristic(new EuclideanRemainingWeightHeuristic())
      .withOriginBackEdge(startBackEdge)
      .withRequest(req)
      .withFrom(u)
      .withTo(v)
      .getShortestPathTree()
      .getPath(v);
  }

  private void checkTriangleInequality(StreetMode mode) {
    assertNotNull(start);
    assertNotNull(end);

    // All reluctance terms are 1.0 so that duration is monotonically increasing in weight.
    var request = StreetSearchRequest.of()
      .withMode(mode)
      .withWalk(walk -> walk.withStairsReluctance(1.0).withSpeed(1.0).withReluctance(1.0))
      .withTurnReluctance(1.0)
      .withCar(car -> car.withReluctance(1.0))
      .withBike(bike -> bike.withSpeed(1.0).withReluctance(1.0))
      .withScooter(scooter -> scooter.withSpeed(1.0).withReluctance(1.0))
      .withIntersectionTraversalCalculator(CALCULATOR)
      .build();

    ShortestPathTree<State, Edge, Vertex> tree = StreetSearchBuilder.of()
      .withHeuristic(new EuclideanRemainingWeightHeuristic())
      .withDominanceFunction(new DominanceFunctions.EarliestArrival())
      .withRequest(request)
      .withFrom(start)
      .withTo(end)
      .getShortestPathTree();

    GraphPath<State, Edge, Vertex> path = tree.getPath(end);
    assertNotNull(path);

    double startEndWeight = path.getWeight();
    int startEndDuration = path.getDuration();
    assertTrue(startEndWeight > 0);
    assertEquals(startEndWeight, startEndDuration, 1.0 * path.edges.size());

    // Try every vertex in the graph as an intermediate.
    boolean violated = false;
    for (Vertex intermediate : graph.getVertices()) {
      if (intermediate == start || intermediate == end) {
        continue;
      }

      GraphPath<State, Edge, Vertex> startIntermediatePath = getPath(
        request,
        null,
        start,
        intermediate
      );
      if (startIntermediatePath == null) {
        continue;
      }

      Edge back = startIntermediatePath.states.getLast().getBackEdge();
      GraphPath<State, Edge, Vertex> intermediateEndPath = getPath(
        request,
        back,
        intermediate,
        end
      );
      if (intermediateEndPath == null) {
        continue;
      }

      double startIntermediateWeight = startIntermediatePath.getWeight();
      int startIntermediateDuration = startIntermediatePath.getDuration();
      double intermediateEndWeight = intermediateEndPath.getWeight();
      int intermediateEndDuration = intermediateEndPath.getDuration();

      // TODO(flamholz): fix traversal so that there's no rounding at the second resolution.
      assertEquals(
        startIntermediateWeight,
        startIntermediateDuration,
        1.0 * startIntermediatePath.edges.size()
      );
      assertEquals(
        intermediateEndWeight,
        intermediateEndDuration,
        1.0 * intermediateEndPath.edges.size()
      );

      double diff = startIntermediateWeight + intermediateEndWeight - startEndWeight;
      if (diff < -0.01) {
        System.out.println("Triangle inequality violated - diff = " + diff);
        violated = true;
      }
      //assertTrue(startIntermediateDuration + intermediateEndDuration >=
      //        startEndDuration);
    }

    assertFalse(violated);
  }
}
