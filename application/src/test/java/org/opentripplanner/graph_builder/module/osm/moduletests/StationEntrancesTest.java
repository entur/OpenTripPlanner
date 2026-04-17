package org.opentripplanner.graph_builder.module.osm.moduletests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.graph_builder.module.osm.OsmModuleTestFactory;
import org.opentripplanner.osm.TestOsmProvider;
import org.opentripplanner.osm.model.OsmMemberType;
import org.opentripplanner.osm.model.OsmNode;
import org.opentripplanner.osm.model.OsmRelation;
import org.opentripplanner.osm.model.OsmRelationMember;
import org.opentripplanner.osm.model.OsmWay;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.street.model.vertex.OsmVertex;
import org.opentripplanner.street.model.vertex.StationEntranceVertex;

public class StationEntrancesTest {

  private static final Graph GRAPH = new Graph();
  private static final OsmNode ENTRANCE_IN_STOP_AREA = new OsmNode(0, 0);

  static {
    ENTRANCE_IN_STOP_AREA.setId(1);
    ENTRANCE_IN_STOP_AREA.addTag("entrance", "yes");
  }

  private static final OsmNode ENTRANCE_OUTSIDE_STOP_AREA = new OsmNode(1, 1);

  static {
    ENTRANCE_OUTSIDE_STOP_AREA.setId(2);
    ENTRANCE_OUTSIDE_STOP_AREA.addTag("entrance", "yes");
  }

  private static final OsmNode SUBWAY_ENTRANCE_OUTSIDE_STOP_AREA = new OsmNode(2, 2);

  static {
    SUBWAY_ENTRANCE_OUTSIDE_STOP_AREA.setId(3);
    SUBWAY_ENTRANCE_OUTSIDE_STOP_AREA.addTag("railway", "subway_entrance");
    SUBWAY_ENTRANCE_OUTSIDE_STOP_AREA.addTag("entrance", "yes");
  }

  private static final OsmWay FOOTWAY = new OsmWay();

  static {
    FOOTWAY.addNodeRef(1);
    FOOTWAY.addNodeRef(2);
    FOOTWAY.addNodeRef(3);
    FOOTWAY.addTag("highway", "footway");
  }

  private static final OsmRelation STOP_AREA = new OsmRelation();

  static {
    STOP_AREA.addTag("type", "public_transport");
    STOP_AREA.addTag("public_transport", "stop_area");

    var member = new OsmRelationMember();
    member.setType(OsmMemberType.NODE);
    member.setRef(1);
    STOP_AREA.addMember(member);
  }

  @BeforeAll
  public static void setUp() {
    var osmProvider = new TestOsmProvider(
      List.of(STOP_AREA),
      List.of(FOOTWAY),
      List.of(ENTRANCE_IN_STOP_AREA, ENTRANCE_OUTSIDE_STOP_AREA, SUBWAY_ENTRANCE_OUTSIDE_STOP_AREA)
    );

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
