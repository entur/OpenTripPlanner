package org.opentripplanner.graph_builder.module.osm.moduletests;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.graph_builder.module.osm.moduletests._support.NodeBuilder.node;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.opentripplanner.framework.geometry.GeometryUtils;
import org.opentripplanner.framework.geometry.WgsCoordinate;
import org.opentripplanner.framework.i18n.NonLocalizedString;
import org.opentripplanner.graph_builder.module.osm.OsmModuleTestFactory;
import org.opentripplanner.graph_builder.module.osm.moduletests._support.TestOsmProvider;
import org.opentripplanner.osm.model.OsmEntityType;
import org.opentripplanner.osm.model.OsmWay;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.street.model.StreetTraversalPermission;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.edge.ElevatorAlightEdge;
import org.opentripplanner.street.model.edge.ElevatorBoardEdge;
import org.opentripplanner.street.model.edge.ElevatorHopEdge;
import org.opentripplanner.street.model.edge.StreetEdgeBuilder;
import org.opentripplanner.street.model.vertex.ElevatorVertex;
import org.opentripplanner.street.model.vertex.OsmElevatorVertex;
import org.opentripplanner.street.model.vertex.OsmVertex;
import org.opentripplanner.street.model.vertex.StreetVertex;
import org.opentripplanner.street.model.vertex.VertexFactory;
import org.opentripplanner.transit.model.basic.Accessibility;

class ElevatorTest {

  @Test
  void testDuration() {
    var way = new OsmWay();
    way.addTag("duration", "00:01:02");
    way.addTag("highway", "elevator");
    var provider = TestOsmProvider.of().addWay(way).build();
    var graph = new Graph();
    var osmModule = OsmModuleTestFactory.of(provider).withGraph(graph).builder().build();

    osmModule.buildGraph();

    var edges = graph.getEdgesOfType(ElevatorHopEdge.class);
    assertThat(edges).hasSize(2);
    for (var edge : edges) {
      assertThat(edge.getTravelTime()).hasValue(Duration.ofSeconds(62));
    }
  }

  @Test
  void testMultilevelNodeDuration() {
    var node0 = node(0, new WgsCoordinate(0, 0));
    var node1 = node(1, new WgsCoordinate(2, 0));
    var elevatorNode = node(2, new WgsCoordinate(1, 0));
    elevatorNode.addTag("duration", "00:01:02");
    elevatorNode.addTag("highway", "elevator");
    elevatorNode.addTag("level", "1;2");
    var provider = TestOsmProvider.of()
      .addWayFromNodes(way -> way.addTag("level", "1"), node0, elevatorNode)
      .addWayFromNodes(way -> way.addTag("level", "2"), node1, elevatorNode)
      .build();
    var graph = new Graph();

    OsmModuleTestFactory.of(provider).withGraph(graph).builder().build().buildGraph();

    var edges = graph.getEdgesOfType(ElevatorHopEdge.class);
    assertThat(edges).hasSize(2);
    for (var edge : edges) {
      assertThat(edge.getTravelTime()).hasValue(Duration.ofSeconds(62));
    }
  }

  @Test
  void testMultilevelNodeWithWaysOnSameLevel() {
    var n1 = node(1, new WgsCoordinate(0, 1));
    var n2 = node(2, new WgsCoordinate(0, 2));
    var elevatorNode = node(3, new WgsCoordinate(0, 3));
    elevatorNode.addTag("highway", "elevator");
    var provider = TestOsmProvider.of()
      .addWayFromNodes(way -> way.addTag("level", "1"), n1, elevatorNode)
      .addWayFromNodes(way -> way.addTag("level", "1"), n2, elevatorNode)
      .build();
    var graph = new Graph();

    OsmModuleTestFactory.of(provider).withGraph(graph).builder().build().buildGraph();

    var edges = graph.getEdgesOfType(ElevatorHopEdge.class);
    assertThat(edges).hasSize(2);
    for (var edge : edges) {
      assertEquals(edge.getLevels(), 0.0);
    }
  }

  @Test
  void testMultilevelNodeWithMultipleWays() {
    var n1 = node(1, new WgsCoordinate(0, 1));
    var n2 = node(2, new WgsCoordinate(0, 2));
    var n3 = node(3, new WgsCoordinate(0, 3));
    var n4 = node(4, new WgsCoordinate(0, 4));
    var elevatorNode = node(5, new WgsCoordinate(0, 5));
    elevatorNode.addTag("highway", "elevator");

    var way1 = new OsmWay();
    way1.setId(1);
    way1.addTag("level", "0");
    way1.addNodeRef(1);
    way1.addNodeRef(5);
    var way2 = new OsmWay();
    way2.setId(2);
    way2.addTag("level", "2");
    way2.addNodeRef(2);
    way2.addNodeRef(5);
    var way3 = new OsmWay();
    way3.setId(3);
    way3.addTag("level", "2");
    way3.addNodeRef(3);
    way3.addNodeRef(5);
    var way4 = new OsmWay();
    way4.setId(4);
    way4.addTag("level", "3");
    way4.addNodeRef(4);
    way4.addNodeRef(5);

    var provider = new TestOsmProvider(
      List.of(),
      List.of(way1, way2, way3, way4),
      List.of(n1, n2, n3, n4, elevatorNode)
    );
    var graph = new Graph();
    OsmModuleTestFactory.of(provider).withGraph(graph).builder().build().buildGraph();

    VertexFactory vertexFactory = new VertexFactory(graph);
    Set<Edge> edgeSet = new HashSet<>();

    var osmVertex1 = new OsmVertex(0, 1, 1);
    var osmVertex2 = new OsmVertex(0, 2, 2);
    var osmVertex3 = new OsmVertex(0, 3, 3);
    var osmVertex4 = new OsmVertex(0, 4, 4);

    var osmElevatorVertex1 = vertexFactory.osmElevator(
      elevatorNode,
      OsmEntityType.WAY,
      way1.getId()
    );
    var osmElevatorVertex2 = vertexFactory.osmElevator(
      elevatorNode,
      OsmEntityType.WAY,
      way2.getId()
    );
    var osmElevatorVertex3 = vertexFactory.osmElevator(
      elevatorNode,
      OsmEntityType.WAY,
      way3.getId()
    );
    var osmElevatorVertex4 = vertexFactory.osmElevator(
      elevatorNode,
      OsmEntityType.WAY,
      way4.getId()
    );

    var elevatorVertex1 = vertexFactory.elevator(
      osmElevatorVertex1,
      osmElevatorVertex1.getLabelString()
    );
    var elevatorVertex2 = vertexFactory.elevator(
      osmElevatorVertex2,
      osmElevatorVertex2.getLabelString()
    );
    var elevatorVertex3 = vertexFactory.elevator(
      osmElevatorVertex3,
      osmElevatorVertex3.getLabelString()
    );
    var elevatorVertex4 = vertexFactory.elevator(
      osmElevatorVertex4,
      osmElevatorVertex4.getLabelString()
    );

    addStreetEdge(edgeSet, osmElevatorVertex1, osmVertex1);
    addStreetEdge(edgeSet, osmElevatorVertex2, osmVertex2);
    addStreetEdge(edgeSet, osmElevatorVertex3, osmVertex3);
    addStreetEdge(edgeSet, osmElevatorVertex4, osmVertex4);

    addElevatorBoardAndAlightEdges(edgeSet, osmElevatorVertex1, elevatorVertex1);
    addElevatorBoardAndAlightEdges(edgeSet, osmElevatorVertex2, elevatorVertex2);
    addElevatorBoardAndAlightEdges(edgeSet, osmElevatorVertex3, elevatorVertex3);
    addElevatorBoardAndAlightEdges(edgeSet, osmElevatorVertex4, elevatorVertex4);

    addElevatorHopEdges(edgeSet, elevatorVertex1, elevatorVertex2, 2);
    addElevatorHopEdges(edgeSet, elevatorVertex2, elevatorVertex3, 0);
    addElevatorHopEdges(edgeSet, elevatorVertex3, elevatorVertex4, 1);

    assertEquals(edgeSet, new HashSet<>(graph.getEdges()));
  }

  private void addStreetEdge(Set<Edge> edgeSet, StreetVertex vertex1, StreetVertex vertex2) {
    edgeSet.add(
      new StreetEdgeBuilder<>()
        .withFromVertex(vertex1)
        .withToVertex(vertex2)
        .withGeometry(
          GeometryUtils.makeLineString(List.of(vertex1.getCoordinate(), vertex2.getCoordinate()))
        )
        .withMeterLength(5)
        .withPermission(StreetTraversalPermission.PEDESTRIAN)
        .buildAndConnect()
    );
  }

  private void addElevatorBoardAndAlightEdges(
    Set<Edge> edgeSet,
    OsmElevatorVertex osmElevatorVertex,
    ElevatorVertex elevatorVertex
  ) {
    edgeSet.add(ElevatorBoardEdge.createElevatorBoardEdge(osmElevatorVertex, elevatorVertex));
    edgeSet.add(
      ElevatorAlightEdge.createElevatorAlightEdge(
        elevatorVertex,
        osmElevatorVertex,
        new NonLocalizedString("0")
      )
    );
  }

  private void addElevatorHopEdges(
    Set<Edge> edgeSet,
    ElevatorVertex elevatorVertex1,
    ElevatorVertex elevatorVertex2,
    double levels
  ) {
    edgeSet.add(
      ElevatorHopEdge.createElevatorHopEdge(
        elevatorVertex1,
        elevatorVertex2,
        StreetTraversalPermission.PEDESTRIAN,
        Accessibility.NO_INFORMATION,
        levels,
        -1
      )
    );
    edgeSet.add(
      ElevatorHopEdge.createElevatorHopEdge(
        elevatorVertex2,
        elevatorVertex1,
        StreetTraversalPermission.PEDESTRIAN,
        Accessibility.NO_INFORMATION,
        levels,
        -1
      )
    );
  }

  @ParameterizedTest
  @CsvSource(
    value = {
      "1, 2, 3, 4", "1, 1, 1, 1", "0, 1, 1, null", "null, null, 1, null", "null, null, null, null",
    },
    nullValues = "null"
  )
  void testOsmElevatorNodeUniqueLabels(String level1, String ref1, String level2, String ref2) {
    var n1 = node(1, new WgsCoordinate(0, 1));
    var n2 = node(2, new WgsCoordinate(0, 2));
    var elevatorNode = node(3, new WgsCoordinate(0, 3));
    elevatorNode.addTag("highway", "elevator");
    var provider = TestOsmProvider.of()
      .addWayFromNodes(
        way -> {
          way.addTag("level", level1);
          way.addTag("level:ref", ref1);
        },
        n1,
        elevatorNode
      )
      .addWayFromNodes(
        way -> {
          way.addTag("level", level2);
          way.addTag("level:ref", ref2);
        },
        elevatorNode,
        n2
      )
      .build();
    var graph = new Graph();

    OsmModuleTestFactory.of(provider).withGraph(graph).builder().build().buildGraph();

    assertEquals(
      graph.getVertices().size(),
      graph.getVertices().stream().map(vertex -> vertex.getLabel()).distinct().count()
    );
  }
}
