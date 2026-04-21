package org.opentripplanner.graph_builder.module.osm.moduletests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.graph_builder.module.osm.OsmModuleTestFactory;
import org.opentripplanner.osm.TestOsmProvider;
import org.opentripplanner.osm.model.NodeBuilder;
import org.opentripplanner.osm.model.OsmNode;
import org.opentripplanner.osm.model.OsmRelation;
import org.opentripplanner.osm.model.RelationBuilder;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.street.model.vertex.OsmVertex;
import org.opentripplanner.street.model.vertex.StationEntranceVertex;

public class StationEntrancesTest {

  private static final Graph GRAPH = new Graph();
  private static final OsmNode ENTRANCE_IN_STOP_AREA = NodeBuilder.of(1, new WgsCoordinate(0, 0))
    .withTag("entrance", "yes")
    .build();
  private static final OsmNode ENTRANCE_OUTSIDE_STOP_AREA = NodeBuilder.of(
    2,
    new WgsCoordinate(1, 1)
  )
    .withTag("entrance", "yes")
    .build();

  private static final OsmNode SUBWAY_ENTRANCE_OUTSIDE_STOP_AREA = NodeBuilder.of(
    3,
    new WgsCoordinate(2, 2)
  )
    .withTag("railway", "subway_entrance")
    .withTag("entrance", "yes")
    .build();

  private static final OsmRelation STOP_AREA = RelationBuilder.ofType("public_transport")
    .withTag("public_transport", "stop_area")
    .withNodeMember(ENTRANCE_IN_STOP_AREA.getId(), "")
    .build();

  @BeforeAll
  public static void setUp() {
    var osmProvider = TestOsmProvider.of()
      .addWayFromNodes(
        way -> way.addTag("highway", "footway"),
        ENTRANCE_IN_STOP_AREA,
        ENTRANCE_OUTSIDE_STOP_AREA,
        SUBWAY_ENTRANCE_OUTSIDE_STOP_AREA
      )
      .addRelation(STOP_AREA)
      .build();

    OsmModuleTestFactory.of(osmProvider)
      .withGraph(GRAPH)
      .builder()
      .withIncludeOsmStationEntrances(true)
      .withIssueStore(DataImportIssueStore.NOOP)
      .build()
      .buildGraph();
  }

  @Test
  void entranceInStopArea() {
    assertInstanceOf(StationEntranceVertex.class, getVertexForOsmNode(ENTRANCE_IN_STOP_AREA));
  }

  @Test
  void entranceOutsideStopArea() {
    assertFalse(getVertexForOsmNode(ENTRANCE_OUTSIDE_STOP_AREA) instanceof StationEntranceVertex);
  }

  @Test
  void stationEntranceOutsideStopArea() {
    assertInstanceOf(
      StationEntranceVertex.class,
      getVertexForOsmNode(SUBWAY_ENTRANCE_OUTSIDE_STOP_AREA)
    );
  }

  private OsmVertex getVertexForOsmNode(OsmNode node) {
    var vertices = GRAPH.getVerticesOfType(OsmVertex.class);
    return vertices
      .stream()
      .filter(v -> v.nodeId() == node.getId())
      .findFirst()
      .orElseThrow();
  }
}
