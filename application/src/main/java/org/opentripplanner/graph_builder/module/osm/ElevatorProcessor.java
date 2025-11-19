package org.opentripplanner.graph_builder.module.osm;

import gnu.trove.list.TLongList;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
  private final VertexFactory vertexFactory;
  private final Consumer<String> osmEntityDurationIssueConsumer;
  private final DataImportIssueStore issueStore;

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
   * +==+~~X
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
   * +==+~~X
   * |
   * +==+~~X
   *
   * +  GenericVertex
   * X  EndpointVertex
   * ~~ FreeEdge
   * == ElevatorBoardEdge/ElevatorAlightEdge
   * |  ElevatorHopEdge
   */
  public ElevatorProcessor(
    DataImportIssueStore issueStore,
    OsmDatabase osmdb,
    VertexGenerator vertexGenerator,
    Graph graph
  ) {
    this.osmdb = osmdb;
    this.vertexGenerator = vertexGenerator;
    this.vertexFactory = new VertexFactory(graph);
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
   * Add nodes with tag highway=elevator to graph as elevators.
   * <p>
   * Needs to be called after elevatorNodes have been created in vertexGenerator.
   */
  public void buildElevatorEdgesFromElevatorNodes() {
    for (Long nodeId : vertexGenerator.elevatorNodes().keySet()) {
      OsmNode node = osmdb.getNode(nodeId);
      Map<OsmElevatorKey, OsmElevatorVertex> vertices = vertexGenerator.elevatorNodes().get(nodeId);
      Map<OsmElevatorKey, OsmLevel> verticeLevels = vertexGenerator.elevatorNodeLevels();

      // Do not create unnecessary ElevatorAlightEdges and ElevatorHopEdges.
      // TODO create issue
      if (vertices.size() < 2) continue;

      List<OsmElevatorKey> osmElevatorKeys = new ArrayList<>(vertices.keySet());
      // Sort to make logic correct and create a deterministic order.
      osmElevatorKeys.sort(
        Comparator.comparing((OsmElevatorKey key) -> verticeLevels.get(key))
          .thenComparing(OsmElevatorKey::entityId)
          .thenComparing(OsmElevatorKey::osmEntityType)
      );
      ArrayList<Vertex> onboardVertices = new ArrayList<>();
      for (OsmElevatorKey key : osmElevatorKeys) {
        OsmElevatorVertex sourceVertex = vertices.get(key);
        OsmLevel level = verticeLevels.get(key);
        createElevatorVertices(onboardVertices, sourceVertex, sourceVertex.getLabelString(), level);
      }

      var wheelchair = node.explicitWheelchairAccessibility();
      long travelTime = node
        .getDuration(osmEntityDurationIssueConsumer)
        .map(Duration::toSeconds)
        .orElse(-1L);
      createElevatorHopEdges(
        onboardVertices,
        osmElevatorKeys.stream().map(key -> verticeLevels.get(key)).toList(),
        wheelchair,
        !node.isBicycleDenied(),
        (int) travelTime
      );
      LOG.debug("Created elevator edges for node {}", node.getId());
    }
  }

  /**
   * Add way with tag highway=elevator to graph as elevator.
   */
  public void buildElevatorEdgeFromElevatorWay(OsmWay elevatorWay) {
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
        new CouldNotApplyMultiLevelInfoToElevatorWay(elevatorWay, nodeLevels.size(), nodes.size())
      );
      nodeLevels = Collections.nCopies(nodes.size(), OsmLevelFactory.DEFAULT);
    }

    ArrayList<Vertex> onboardVertices = new ArrayList<>();
    for (int i = 0; i < nodes.size(); i++) {
      Long node = nodes.get(i);
      var sourceVertex = vertexGenerator.intersectionNodes().get(node);
      OsmLevel level = nodeLevels.get(i);
      createElevatorVertices(
        onboardVertices,
        sourceVertex,
        elevatorWay.getId() + "_" + i + "_" + sourceVertex.getLabelString(),
        level
      );
    }

    var wheelchair = elevatorWay.explicitWheelchairAccessibility();
    long travelTime = elevatorWay
      .getDuration(osmEntityDurationIssueConsumer)
      .map(Duration::toSeconds)
      .orElse(-1L);
    createElevatorHopEdges(
      onboardVertices,
      nodeLevels,
      wheelchair,
      !elevatorWay.isBicycleDenied(),
      (int) travelTime
    );
    LOG.debug("Created elevator edges for way {}", elevatorWay.getId());
  }

  private void createElevatorVertices(
    ArrayList<Vertex> onboardVertices,
    IntersectionVertex sourceVertex,
    String label,
    OsmLevel level
  ) {
    ElevatorVertex onboardVertex = vertexFactory.elevator(sourceVertex, label);

    ElevatorBoardEdge.createElevatorBoardEdge(sourceVertex, onboardVertex);
    ElevatorAlightEdge.createElevatorAlightEdge(
      onboardVertex,
      sourceVertex,
      // TODO this will be removed in a later PR and moved to the StreetDetailsService
      new NonLocalizedString(level.name())
    );

    // accumulate onboard vertices to so they can be connected by hop edges later
    onboardVertices.add(onboardVertex);
  }

  private static void createElevatorHopEdges(
    ArrayList<Vertex> onboardVertices,
    List<OsmLevel> onboardVertexLevels,
    Accessibility wheelchair,
    boolean bicycleAllowed,
    int travelTime
  ) {
    // -1 because we loop over onboardVertices two at a time
    for (int i = 0, vSize = onboardVertices.size() - 1; i < vSize; i++) {
      Vertex from = onboardVertices.get(i);
      Vertex to = onboardVertices.get(i + 1);
      OsmLevel fromLevel = onboardVertexLevels.get(i);
      OsmLevel toLevel = onboardVertexLevels.get(i + 1);

      // default permissions: pedestrian, wheelchair, check tag bicycle=yes
      StreetTraversalPermission permission = bicycleAllowed
        ? StreetTraversalPermission.PEDESTRIAN_AND_BICYCLE
        : StreetTraversalPermission.PEDESTRIAN;

      ElevatorHopEdge.bidirectional(
        from,
        to,
        permission,
        wheelchair,
        Math.abs(toLevel.level() - fromLevel.level()),
        travelTime
      );
    }
  }

  public boolean isElevatorWay(OsmWay way) {
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
