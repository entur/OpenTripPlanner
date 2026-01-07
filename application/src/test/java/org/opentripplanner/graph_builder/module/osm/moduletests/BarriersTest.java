package org.opentripplanner.graph_builder.module.osm.moduletests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opentripplanner.graph_builder.issue.service.DefaultDataImportIssueStore;
import org.opentripplanner.graph_builder.issues.BarrierIntersectingHighway;
import org.opentripplanner.graph_builder.module.osm.OsmModuleTestFactory;
import org.opentripplanner.osm.TestOsmProvider;
import org.opentripplanner.osm.model.OsmNode;
import org.opentripplanner.osm.model.OsmWay;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.street.model.vertex.BarrierVertex;

public class BarriersTest {

  @Test
  void testLinearCrossingNonIntersection() {
    var way = new OsmWay();
    way.addTag("highway", "path");
    way.addNodeRef(1);
    way.addNodeRef(2);
    way.addNodeRef(3);
    way.addNodeRef(4);
    way.setId(1);

    var barrier = new OsmWay();
    barrier.addTag("barrier", "fence");
    barrier.addNodeRef(99);
    barrier.addNodeRef(2);
    barrier.addNodeRef(98);
    barrier.setId(2);

    var osmProvider = new TestOsmProvider(
      List.of(),
      List.of(way, barrier),
      Set.of(1, 2, 3, 4, 98, 99)
        .stream()
        .map(id -> {
          var node = new OsmNode((double) id / 1000, 0);
          node.setId(id);
          return node;
        })
        .toList()
    );

    var graph = new Graph();
    var issueStore = new DefaultDataImportIssueStore();

    OsmModuleTestFactory.of(osmProvider)
      .withGraph(graph)
      .builder()
      .withIssueStore(issueStore)
      .build()
      .buildGraph();

    assertEquals(3, graph.getVertices().size());
    var barrierVertices = graph.getVerticesOfType(BarrierVertex.class);
    assertEquals(0, barrierVertices.size());
    var issues = issueStore
      .listIssues()
      .stream()
      .filter(issue -> issue instanceof BarrierIntersectingHighway);
    assertEquals(1, issues.count());
  }
}
