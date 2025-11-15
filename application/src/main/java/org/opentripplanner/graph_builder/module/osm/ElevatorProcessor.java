package org.opentripplanner.graph_builder.module.osm;

import com.google.common.collect.Multimap;
import gnu.trove.list.TLongList;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.opentripplanner.framework.i18n.NonLocalizedString;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.graph_builder.issue.api.Issue;
import org.opentripplanner.graph_builder.issues.CouldNotApplyMultiLevelInfoToElevatorWay;
import org.opentripplanner.osm.model.OsmLevel;
import org.opentripplanner.osm.model.OsmLevelFactory;
import org.opentripplanner.osm.model.OsmNode;
import org.opentripplanner.osm.model.OsmWay;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.street.model.StreetTraversalPermission;
import org.opentripplanner.street.model.edge.ElevatorAlightEdge;
import org.opentripplanner.street.model.edge.ElevatorBoardEdge;
import org.opentripplanner.street.model.edge.ElevatorHopEdge;
import org.opentripplanner.street.model.vertex.ElevatorVertex;
import org.opentripplanner.street.model.vertex.IntersectionVertex;
import org.opentripplanner.street.model.vertex.OsmElevatorVertex;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.model.vertex.VertexFactory;
import org.opentripplanner.transit.model.basic.Accessibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains the logic for extracting elevator data from OSM and converting it to edges.
 * <p>
 * It depends heavily on the idiosyncratic processing of the OSM data in {@link OsmModule}
 * which is the reason this is not a public class.
 */
class ElevatorProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(ElevatorProcessor.class);

  private final OsmDatabase osmdb;
  private final VertexGenerator vertexGenerator;
  private final Consumer<String> osmEntityDurationIssueConsumer;
  private final DataImportIssueStore issueStore;

  public ElevatorProcessor(
    DataImportIssueStore issueStore,
    OsmDatabase osmdb,
    VertexGenerator vertexGenerator
  ) {
    this.osmdb = osmdb;
    this.vertexGenerator = vertexGenerator;
    this.osmEntityDurationIssueConsumer = v ->
      issueStore.add(
        Issue.issue(
          "InvalidDuration",
          "Duration for osm node {} is not a valid duration: '{}'; the value is ignored.",
          v
        )
      );
    this.issueStore = issueStore;
  }

  /**
   * This method builds elevator egdes.
   * <p>
   * Elevators have three types of edges: ElevatorAlightEdges, ElevatorHopEdges, and
   * ElevatorBoardEdges. The build process involves creating FreeEdges to disconnect from the
   * graph, GenericVertices to serve as attachment points, and ElevatorBoardEdges and
   * ElevatorAlightEdges to connect future ElevatorHopEdges to.
   * <p>
   * With two connected ways to a node (which can be on the same level), after building the
   * ElevatorAlightEdge and ElevatorBoardEdge the graph will look like this (side view):
   *
   * +==+~~X
   *
   * +--+~~X
   *
   * +  GenericVertex
   * X  EndpointVertex
   * ~~ FreeEdge
   * == ElevatorBoardEdge/ElevatorAlightEdge
   * <p>
   * Another loop fills in the ElevatorHopEdges. After filling in the ElevatorHopEdges when a node
   * has 3 connected ways the graph will look like this (side view):
   *
   * +==+~~X
   * |
   * +--+~~X
   * |
   * +--+~~X
   *
   * +  GenericVertex
   * X  EndpointVertex
   * ~~ FreeEdge
   * == ElevatorBoardEdge/ElevatorAlightEdge
   * |  ElevatorHopEdge
   */
  public void buildElevatorEdges(Graph graph) {
    buildElevatorEdgesFromElevatorNodes(graph);
    buildElevatorEdgesFromElevatorWays(graph);
  }

  /**
   * Add nodes with tag highway=elevator to graph as elevators.
   */
  private void buildElevatorEdgesFromElevatorNodes(Graph graph) {
    for (Long nodeId : vertexGenerator.multiLevelNodes().keySet()) {
      OsmNode node = osmdb.getNode(nodeId);
      Multimap<OsmLevel, OsmElevatorVertex> vertices = vertexGenerator
        .multiLevelNodes()
        .get(nodeId);

      // Do not create unnecessary ElevatorAlightEdges and ElevatorHopEdges.
      if (vertices.size() < 2) continue;

      List<OsmLevel> levels = vertices.keySet().stream().sorted().toList();
      ArrayList<Vertex> onboardVertices = new ArrayList<>();
      for (OsmLevel level : levels) {
        for (OsmElevatorVertex sourceVertex : vertices.get(level)) {
          createElevatorVertices(
            graph,
            onboardVertices,
            sourceVertex,
            sourceVertex.getLabelString(),
            level
          );
        }
      }

      var wheelchair = node.explicitWheelchairAccessibility();
      long travelTime = node
        .getDuration(osmEntityDurationIssueConsumer)
        .map(Duration::toSeconds)
        .orElse(-1L);
      createElevatorHopEdges(
        onboardVertices,
        wheelchair,
        !node.isBicycleDenied(),
        levels.size(),
        (int) travelTime
      );
      LOG.debug("Created elevator edges for node {}", node.getId());
    }
  }

  /**
   * Add ways with tag highway=elevator to graph as elevators.
   */
  private void buildElevatorEdgesFromElevatorWays(Graph graph) {
    osmdb
      .getWays()
      .stream()
      .filter(this::isElevatorWay)
      .forEach(elevatorWay -> {
        List<OsmLevel> nodeLevels = osmdb.getLevelsForEntity(elevatorWay);
        List<Long> nodes = Arrays.stream(elevatorWay.getNodeRefs().toArray())
          .filter(
            nodeRef ->
              vertexGenerator.intersectionNodes().containsKey(nodeRef) &&
              vertexGenerator.intersectionNodes().get(nodeRef) != null
          )
          .boxed()
          .toList();

        if (nodeLevels.size() != nodes.size()) {
          issueStore.add(
            new CouldNotApplyMultiLevelInfoToElevatorWay(
              elevatorWay,
              nodeLevels.size(),
              nodes.size()
            )
          );
          nodeLevels = Collections.nCopies(nodes.size(), OsmLevelFactory.DEFAULT);
        }

        ArrayList<Vertex> onboardVertices = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
          Long node = nodes.get(i);
          var sourceVertex = vertexGenerator.intersectionNodes().get(node);
          String sourceVertexLabel = sourceVertex.getLabelString();
          createElevatorVertices(
            graph,
            onboardVertices,
            sourceVertex,
            elevatorWay.getId() + "_" + sourceVertexLabel + "_" + i,
            nodeLevels.get(i)
          );
        }

        var wheelchair = elevatorWay.explicitWheelchairAccessibility();
        int levels = nodes.size();
        long travelTime = elevatorWay
          .getDuration(osmEntityDurationIssueConsumer)
          .map(Duration::toSeconds)
          .orElse(-1L);
        createElevatorHopEdges(
          onboardVertices,
          wheelchair,
          !elevatorWay.isBicycleDenied(),
          levels,
          (int) travelTime
        );
        LOG.debug("Created elevator edges for way {}", elevatorWay.getId());
      });
  }

  private static void createElevatorVertices(
    Graph graph,
    ArrayList<Vertex> onboardVertices,
    IntersectionVertex sourceVertex,
    String label,
    OsmLevel level
  ) {
    var factory = new VertexFactory(graph);
    ElevatorVertex onboardVertex = factory.elevator(sourceVertex, label, level.level());

    ElevatorBoardEdge.createElevatorBoardEdge(sourceVertex, onboardVertex);
    ElevatorAlightEdge.createElevatorAlightEdge(
      onboardVertex,
      sourceVertex,
      new NonLocalizedString(level.name())
    );

    // accumulate onboard vertices to so they can be connected by hop edges later
    onboardVertices.add(onboardVertex);
  }

  private static void createElevatorHopEdges(
    ArrayList<Vertex> onboardVertices,
    Accessibility wheelchair,
    boolean bicycleAllowed,
    int levels,
    int travelTime
  ) {
    // -1 because we loop over onboardVertices two at a time
    for (int i = 0, vSize = onboardVertices.size() - 1; i < vSize; i++) {
      Vertex from = onboardVertices.get(i);
      Vertex to = onboardVertices.get(i + 1);

      // default permissions: pedestrian, wheelchair, check tag bicycle=yes
      StreetTraversalPermission permission = bicycleAllowed
        ? StreetTraversalPermission.PEDESTRIAN_AND_BICYCLE
        : StreetTraversalPermission.PEDESTRIAN;

      if (travelTime > -1 && levels > 0) {
        ElevatorHopEdge.bidirectional(from, to, permission, wheelchair, levels, travelTime);
      } else {
        ElevatorHopEdge.bidirectional(from, to, permission, wheelchair);
      }
    }
  }

  private boolean isElevatorWay(OsmWay way) {
    if (!way.isElevator()) {
      return false;
    }

    if (osmdb.isAreaWay(way.getId())) {
      return false;
    }

    TLongList nodeRefs = way.getNodeRefs();
    // A way whose first and last node are the same is probably an area, skip that.
    // https://www.openstreetmap.org/way/503412863
    // https://www.openstreetmap.org/way/187719215
    return nodeRefs.get(0) != nodeRefs.get(nodeRefs.size() - 1);
  }
}
