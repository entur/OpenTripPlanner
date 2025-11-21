package org.opentripplanner.graph_builder.module.osm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.graph_builder.module.osm.moduletests._support.TestOsmProvider;
import org.opentripplanner.osm.model.OsmNode;
import org.opentripplanner.osm.model.OsmWay;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.service.streetdetails.StreetDetailsRepository;
import org.opentripplanner.service.streetdetails.internal.DefaultStreetDetailsRepository;
import org.opentripplanner.service.streetdetails.model.InclinedEdgeLevelInfo;
import org.opentripplanner.service.streetdetails.model.Level;
import org.opentripplanner.service.streetdetails.model.VertexLevelInfo;

public class EdgeLevelInfoTest {

  @Test
  void testInclinedEdgeLevelInfo() {
    var n1 = new OsmNode(0, 0);
    n1.setId(1);
    var n2 = new OsmNode(0, 1);
    n2.setId(2);

    var levelStairs = new OsmWay();
    levelStairs.setId(1);
    levelStairs.addTag("highway", "steps");
    levelStairs.addTag("incline", "up");
    levelStairs.addTag("level", "1;2");
    levelStairs.addNodeRef(1);
    levelStairs.addNodeRef(2);

    var inclineStairs = new OsmWay();
    inclineStairs.setId(2);
    inclineStairs.addTag("highway", "steps");
    inclineStairs.addTag("incline", "up");
    inclineStairs.addNodeRef(1);
    inclineStairs.addNodeRef(2);

    var escalator = new OsmWay();
    escalator.setId(3);
    escalator.addTag("highway", "steps");
    escalator.addTag("conveying", "yes");
    escalator.addTag("level", "1;-1");
    escalator.addTag("level:ref", "1;P1");
    escalator.addNodeRef(1);
    escalator.addNodeRef(2);

    var osmProvider = new TestOsmProvider(
      List.of(),
      List.of(levelStairs, inclineStairs, escalator),
      List.of(n1, n2)
    );
    var osmDb = new OsmDatabase(DataImportIssueStore.NOOP);
    osmProvider.readOsm(osmDb);
    var graph = new Graph();
    var streetDetailsRepository = new DefaultStreetDetailsRepository();
    var osmModule = OsmModuleTestFactory.of(osmProvider)
      .withGraph(graph)
      .builder()
      .withStreetDetailsRepository(streetDetailsRepository)
      // The build config field that needs to bet set for inclined edge level info to be stored.
      .withIncludeInclinedEdgeLevelInfo(true)
      .build();
    osmModule.buildGraph();

    var edgeLevelInfoSet = Set.of(
      new InclinedEdgeLevelInfo(
        new VertexLevelInfo(new Level(1, "1"), 1),
        new VertexLevelInfo(new Level(2, "2"), 2)
      ),
      new InclinedEdgeLevelInfo(new VertexLevelInfo(null, 1), new VertexLevelInfo(null, 2)),
      new InclinedEdgeLevelInfo(
        new VertexLevelInfo(new Level(-1, "P1"), 2),
        new VertexLevelInfo(new Level(1, "1"), 1)
      )
    );
    assertEquals(
      edgeLevelInfoSet,
      getAllInclinedEdgeLevelInfoObjects(graph, streetDetailsRepository)
    );
  }

  @Test
  void testEdgeLevelInfoNotStoredWithoutIncludeEdgeLevelInfo() {
    var n1 = new OsmNode(0, 0);
    n1.setId(1);
    var n2 = new OsmNode(0, 1);
    n2.setId(2);

    var inclineStairs = new OsmWay();
    inclineStairs.setId(2);
    inclineStairs.addTag("highway", "steps");
    inclineStairs.addTag("incline", "up");
    inclineStairs.addNodeRef(1);
    inclineStairs.addNodeRef(2);

    var osmProvider = new TestOsmProvider(List.of(), List.of(inclineStairs), List.of(n1, n2));
    var osmDb = new OsmDatabase(DataImportIssueStore.NOOP);
    osmProvider.readOsm(osmDb);
    var graph = new Graph();
    var streetDetailsRepository = new DefaultStreetDetailsRepository();
    var osmModule = OsmModuleTestFactory.of(osmProvider)
      .withGraph(graph)
      .builder()
      .withStreetDetailsRepository(streetDetailsRepository)
      // The build config field that needs to bet set for inclined edge level info to be stored.
      .withIncludeInclinedEdgeLevelInfo(false)
      .build();
    osmModule.buildGraph();

    assertEquals(Set.of(), getAllInclinedEdgeLevelInfoObjects(graph, streetDetailsRepository));
  }

  @Test
  void testElevatorNodeEdgeLevelInfo() {
    var n1 = new OsmNode(0, 0);
    n1.setId(1);
    var n2 = new OsmNode(0, 1);
    n2.addTag("highway", "elevator");
    n2.setId(2);
    var n3 = new OsmNode(0, 2);
    n3.setId(3);

    var way1 = new OsmWay();
    way1.setId(1);
    way1.addTag("highway", "corridor");
    way1.addTag("level", "1");
    way1.addNodeRef(1);
    way1.addNodeRef(2);

    var way2 = new OsmWay();
    way2.setId(2);
    way2.addTag("highway", "corridor");
    way2.addTag("level", "2");
    way2.addNodeRef(2);
    way2.addNodeRef(3);

    var osmProvider = new TestOsmProvider(List.of(), List.of(way1, way2), List.of(n1, n2, n3));
    var osmDb = new OsmDatabase(DataImportIssueStore.NOOP);
    osmProvider.readOsm(osmDb);
    var graph = new Graph();
    var streetDetailsRepository = new DefaultStreetDetailsRepository();
    var osmModule = OsmModuleTestFactory.of(osmProvider)
      .withGraph(graph)
      .builder()
      .withStreetDetailsRepository(streetDetailsRepository)
      .build();
    osmModule.buildGraph();

    var edgeLevelInfoSet = Set.of(new Level(1, "1"), new Level(2, "2"));
    assertEquals(edgeLevelInfoSet, getAllEdgeLevelInfoObjects(graph, streetDetailsRepository));
  }

  @Test
  void testElevatorNodeEdgeLevelInfoOnSameLevel() {
    var n1 = new OsmNode(0, 0);
    n1.setId(1);
    var n2 = new OsmNode(0, 1);
    n2.addTag("highway", "elevator");
    n2.setId(2);
    var n3 = new OsmNode(0, 2);
    n3.setId(3);

    var way1 = new OsmWay();
    way1.setId(1);
    way1.addTag("highway", "corridor");
    way1.addNodeRef(1);
    way1.addNodeRef(2);

    var way2 = new OsmWay();
    way2.setId(2);
    way2.addTag("highway", "corridor");
    way2.addNodeRef(2);
    way2.addNodeRef(3);

    var osmProvider = new TestOsmProvider(List.of(), List.of(way1, way2), List.of(n1, n2, n3));
    var osmDb = new OsmDatabase(DataImportIssueStore.NOOP);
    osmProvider.readOsm(osmDb);
    var graph = new Graph();
    var streetDetailsRepository = new DefaultStreetDetailsRepository();
    var osmModule = OsmModuleTestFactory.of(osmProvider)
      .withGraph(graph)
      .builder()
      .withStreetDetailsRepository(streetDetailsRepository)
      .build();
    osmModule.buildGraph();

    var edgeLevelInfoSet = Set.of(new Level(0, "default level"));
    assertEquals(edgeLevelInfoSet, getAllEdgeLevelInfoObjects(graph, streetDetailsRepository));
  }

  @Test
  void testElevatorWayEdgeLevelInfo() {
    var n1 = new OsmNode(0, 0);
    n1.setId(1);
    var n2 = new OsmNode(0, 1);
    n2.setId(2);

    var elevatorWay = new OsmWay();
    elevatorWay.setId(1);
    elevatorWay.addTag("highway", "elevator");
    elevatorWay.addTag("level", "0;1");
    elevatorWay.addNodeRef(1);
    elevatorWay.addNodeRef(2);

    var osmProvider = new TestOsmProvider(List.of(), List.of(elevatorWay), List.of(n1, n2));
    var osmDb = new OsmDatabase(DataImportIssueStore.NOOP);
    osmProvider.readOsm(osmDb);
    var graph = new Graph();
    var streetDetailsRepository = new DefaultStreetDetailsRepository();
    var osmModule = OsmModuleTestFactory.of(osmProvider)
      .withGraph(graph)
      .builder()
      .withStreetDetailsRepository(streetDetailsRepository)
      .build();
    osmModule.buildGraph();

    var edgeLevelInfoSet = Set.of(new Level(0, "0"), new Level(1, "1"));
    assertEquals(edgeLevelInfoSet, getAllEdgeLevelInfoObjects(graph, streetDetailsRepository));
  }

  @Test
  void testElevatorWayEdgeLevelInfoOnSameLevel() {
    var n1 = new OsmNode(0, 0);
    n1.setId(1);
    var n2 = new OsmNode(0, 1);
    n2.setId(2);

    var elevatorWay = new OsmWay();
    elevatorWay.setId(1);
    elevatorWay.addTag("highway", "elevator");
    elevatorWay.addNodeRef(1);
    elevatorWay.addNodeRef(2);

    var osmProvider = new TestOsmProvider(List.of(), List.of(elevatorWay), List.of(n1, n2));
    var osmDb = new OsmDatabase(DataImportIssueStore.NOOP);
    osmProvider.readOsm(osmDb);
    var graph = new Graph();
    var streetDetailsRepository = new DefaultStreetDetailsRepository();
    var osmModule = OsmModuleTestFactory.of(osmProvider)
      .withGraph(graph)
      .builder()
      .withStreetDetailsRepository(streetDetailsRepository)
      .build();
    osmModule.buildGraph();

    var edgeLevelInfoSet = Set.of(new Level(0, "default level"));
    assertEquals(edgeLevelInfoSet, getAllEdgeLevelInfoObjects(graph, streetDetailsRepository));
  }

  private Set<InclinedEdgeLevelInfo> getAllInclinedEdgeLevelInfoObjects(
    Graph graph,
    StreetDetailsRepository streetDetailsRepository
  ) {
    return graph
      .getEdges()
      .stream()
      .flatMap(edge ->
        streetDetailsRepository
          .findInclinedEdgeLevelInfo(edge)
          .map(Stream::of)
          .orElseGet(Stream::empty)
      )
      .collect(Collectors.toSet());
  }

  private Set<Level> getAllEdgeLevelInfoObjects(
    Graph graph,
    StreetDetailsRepository streetDetailsRepository
  ) {
    return graph
      .getEdges()
      .stream()
      .flatMap(edge ->
        streetDetailsRepository
          .findHorizontalEdgeLevelInfo(edge)
          .map(Stream::of)
          .orElseGet(Stream::empty)
      )
      .collect(Collectors.toSet());
  }
}
