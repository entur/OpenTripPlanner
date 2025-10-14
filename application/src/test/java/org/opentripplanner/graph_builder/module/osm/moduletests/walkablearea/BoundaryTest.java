package org.opentripplanner.graph_builder.module.osm.moduletests.walkablearea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.graph_builder.module.osm.moduletests._support.NodeBuilder.node;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.framework.geometry.WgsCoordinate;
import org.opentripplanner.graph_builder.module.osm.OsmModule;
import org.opentripplanner.graph_builder.module.osm.moduletests._support.TestOsmProvider;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.service.osminfo.internal.DefaultOsmInfoGraphBuildRepository;
import org.opentripplanner.service.vehicleparking.internal.DefaultVehicleParkingRepository;
import org.opentripplanner.street.model.edge.AreaEdge;
import org.opentripplanner.test.support.GeoJsonIo;

/**
 * Ensures that visibility edges can be created to connect two exits at opposite sides of a platform.
 * Walkable area builder occasionally failed in that because of numerical accuracy problems.
 * <p>
 * This test duplicates the area routing errors detected in Bletchley station https://www.openstreetmap.org/relation/19170549.
 * <p>
 * Further reading: https://github.com/opentripplanner/OpenTripPlanner/issues/6809
 */

class BoundaryTest {

  @Test
  void connectExits() {
    var northWest = node(0, new WgsCoordinate(-0.7360985, 51.9962091));
    var northEast = node(1, new WgsCoordinate(-0.7360355, 51.9962165));
    var eastExit = node(2, new WgsCoordinate(-0.7357519, 51.9953057));
    var southEast = node(3, new WgsCoordinate(-0.7356841, 51.9950911));
    var southWest = node(4, new WgsCoordinate(-0.7357458, 51.9950836));
    var westExit = node(5, new WgsCoordinate(-0.7357735232317726, 51.995172067528664));

    var westOut = node(7, new WgsCoordinate(-0.7359957, 51.9953844));
    var eastOut = node(8, new WgsCoordinate(-0.7355928, 51.9952911));

    var area = List.of(northWest, northEast, eastExit, southEast, southWest, westExit, northWest);

    var provider = TestOsmProvider.of()
      .addAreaFromNodes(area)
      .addWayFromNodes(westExit, westOut)
      .addWayFromNodes(eastExit, eastOut)
      .build();

    var graph = new Graph();
    var osmModule = OsmModule.of(
      provider,
      graph,
      new DefaultOsmInfoGraphBuildRepository(),
      new DefaultVehicleParkingRepository()
    )
      .withAreaVisibility(true)
      .withMaxAreaNodes(10)
      .build();

    osmModule.buildGraph();

    var aEdges = graph.getEdgesOfType(AreaEdge.class);
    assertEquals(aEdges.getFirst().getArea().visibilityVertices().size(), 2);

    // platform is a loop of 7 points, which adds 6 area edge pairs
    // visibility edge connection adds one pair more
    assertEquals(
      14,
      graph.getEdgesOfType(AreaEdge.class).size(),
      "Incorrect number of edges, check %s".formatted(GeoJsonIo.toUrl(graph))
    );
  }
}
