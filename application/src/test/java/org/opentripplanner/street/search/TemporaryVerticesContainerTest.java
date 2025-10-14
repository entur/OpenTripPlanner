package org.opentripplanner.street.search;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.transit.model._data.TimetableRepositoryForTest.id;

import com.google.common.collect.ImmutableMultimap;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.opentripplanner.framework.geometry.WgsCoordinate;
import org.opentripplanner.graph_builder.module.linking.TestVertexLinker;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.linking.LinkingContextFactory;
import org.opentripplanner.routing.linking.LinkingContextRequest;
import org.opentripplanner.routing.linking.TemporaryVerticesContainer;
import org.opentripplanner.street.model._data.StreetModelForTest;
import org.opentripplanner.street.model.edge.StreetStationCentroidLink;
import org.opentripplanner.street.model.vertex.StationCentroidVertex;
import org.opentripplanner.street.model.vertex.TransitStopVertex;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.transit.model._data.TimetableRepositoryForTest;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.site.Station;

class TemporaryVerticesContainerTest {

  private static final WgsCoordinate CENTER = new WgsCoordinate(0, 0);
  private static final int DISTANCE = 20;

  private static final FeedScopedId ALPHA_ID = id("alpha");
  private static final FeedScopedId OMEGA_ID = id("omega");

  private final TimetableRepositoryForTest testModel = TimetableRepositoryForTest.of();

  private final Station stationAlpha = testModel
    .station("alpha")
    .withId(ALPHA_ID)
    .withCoordinate(CENTER)
    .withShouldRouteToCentroid(true)
    .build();

  private final RegularStop stopA = testModel
    .stop("A")
    .withCoordinate(CENTER.moveEastMeters(DISTANCE))
    .build();
  private final RegularStop stopB = testModel
    .stop("B")
    .withCoordinate(CENTER.moveSouthMeters(DISTANCE))
    .build();
  private final RegularStop stopC = testModel
    .stop("C")
    .withCoordinate(CENTER.moveWestMeters(DISTANCE))
    .build();
  private final RegularStop stopD = testModel
    .stop("D")
    .withCoordinate(CENTER.moveNorthMeters(DISTANCE))
    .build();
  private final Graph graph = buildGraph(stationAlpha, stopA, stopB, stopC, stopD);
  private final LinkingContextFactory linkingContextFactory = new LinkingContextFactory(
    graph,
    TestVertexLinker.of(graph)
  );

  @Test
  void coordinates() {
    var container = new TemporaryVerticesContainer();
    var from = GenericLocation.fromCoordinate(stopA.getLat(), stopA.getLon());
    var to = GenericLocation.fromCoordinate(stopD.getLat(), stopD.getLon());
    var request = LinkingContextRequest.of()
      .withFrom(from)
      .withTo(to)
      .withDirectMode(StreetMode.WALK)
      .build();
    var linkingContext = linkingContextFactory.create(container, request);
    assertThat(linkingContext.findVertices(from)).hasSize(1);
    assertThat(linkingContext.findVertices(to)).hasSize(1);
  }

  @Test
  void stopId() {
    var stopLinkingContextFactory = new LinkingContextFactory(
      graph,
      TestVertexLinker.of(graph),
      Set::of
    );
    var container = new TemporaryVerticesContainer();
    var from = stopToLocation(stopA);
    var to = stopToLocation(stopB);
    var request = LinkingContextRequest.of()
      .withFrom(from)
      .withTo(to)
      .withDirectMode(StreetMode.WALK)
      .build();
    var linkingContext = stopLinkingContextFactory.create(container, request);
    assertEquals(stopA, toStop(linkingContext.findVertices(from)));
    assertEquals(stopB, toStop(linkingContext.findVertices(to)));
  }

  @Test
  void stationId() {
    var mapping = ImmutableMultimap.<FeedScopedId, FeedScopedId>builder()
      .putAll(OMEGA_ID, stopC.getId(), stopD.getId())
      .build();
    var stopLinkingContextFactory = new LinkingContextFactory(
      graph,
      TestVertexLinker.of(graph),
      mapping::get
    );
    var container = new TemporaryVerticesContainer();
    var from = GenericLocation.fromStopId("station", OMEGA_ID.getFeedId(), OMEGA_ID.getId());
    var request = LinkingContextRequest.of()
      .withFrom(from)
      .withTo(stopToLocation(stopB))
      .withDirectMode(StreetMode.WALK)
      .build();
    var linkingContext = stopLinkingContextFactory.create(container, request);
    assertThat(toStops(linkingContext.findVertices(from))).containsExactly(stopC, stopD);
  }

  @Test
  void centroid() {
    var container = new TemporaryVerticesContainer();
    var from = GenericLocation.fromStopId("station", ALPHA_ID.getFeedId(), ALPHA_ID.getId());
    var request = LinkingContextRequest.of()
      .withFrom(from)
      .withTo(stopToLocation(stopB))
      .withDirectMode(StreetMode.WALK)
      .build();
    var linkingContext = linkingContextFactory.create(container, request);
    var fromVertices = List.copyOf(linkingContext.findVertices(from));
    assertThat(fromVertices).hasSize(1);

    var station = ((StationCentroidVertex) fromVertices.getFirst()).getStation();
    assertEquals(station, this.stationAlpha);
  }

  private static Graph buildGraph(Station station, RegularStop... stops) {
    var graph = new Graph();
    var center = StreetModelForTest.intersectionVertex(CENTER.asJtsCoordinate());
    graph.addVertex(center);
    var centroidVertex = new StationCentroidVertex(station);
    graph.addVertex(centroidVertex);
    StreetStationCentroidLink.createStreetStationLink(centroidVertex, center);
    Arrays.stream(stops).forEach(s -> {
      graph.addVertex(TransitStopVertex.of().withStop(s).build());
      var vertex = StreetModelForTest.intersectionVertex(s.getCoordinate().asJtsCoordinate());
      StreetModelForTest.streetEdge(vertex, center);
      graph.addVertex(vertex);
    });
    graph.index();
    graph.calculateConvexHull();
    return graph;
  }

  private static RegularStop toStop(Set<? extends Vertex> fromVertices) {
    assertThat(fromVertices).hasSize(1);
    return ((TransitStopVertex) List.copyOf(fromVertices).getFirst()).getStop();
  }

  private static Set<RegularStop> toStops(Set<? extends Vertex> fromVertices) {
    return fromVertices
      .stream()
      .map(v -> ((TransitStopVertex) v).getStop())
      .collect(Collectors.toUnmodifiableSet());
  }

  private GenericLocation stopToLocation(RegularStop s) {
    return GenericLocation.fromStopId(
      s.getName().toString(),
      s.getId().getFeedId(),
      s.getId().getId()
    );
  }
}
