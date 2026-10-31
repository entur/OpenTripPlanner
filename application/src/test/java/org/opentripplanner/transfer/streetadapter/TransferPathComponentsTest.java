package org.opentripplanner.transfer.streetadapter;

import static com.google.common.truth.Truth.assertThat;
import static org.opentripplanner.core.model.id.FeedScopedIdForTestFactory.id;
import static org.opentripplanner.street.model.StreetModelForTest.intersectionVertex;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;
import org.opentripplanner.place.api.NearbyStop;
import org.opentripplanner.raptor.data.transfers.regular.TransferValueFunction;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.preference.RoutingPreferencesBuilder;
import org.opentripplanner.routing.linking.TransitStopVertexBuilderFactory;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.street.model.StreetMode;
import org.opentripplanner.street.model.StreetModelForTest;
import org.opentripplanner.street.model.StreetTraversalPermission;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.edge.PathwayEdge;
import org.opentripplanner.street.model.edge.StreetEdge;
import org.opentripplanner.street.model.edge.StreetElevationExtensionBuilder;
import org.opentripplanner.street.model.edge.StreetTransitStopLink;
import org.opentripplanner.street.model.vertex.LabelledIntersectionVertex;
import org.opentripplanner.street.model.vertex.StreetVertex;
import org.opentripplanner.street.search.intersection_model.ConstantIntersectionTraversalCalculator;
import org.opentripplanner.street.search.request.StreetSearchRequest;
import org.opentripplanner.streetadapter.StreetSearchRequestMapper;
import org.opentripplanner.transit.transfer.regular.spi.TransferSearch;

/**
 * The duration and the cost computed from the components of a path must be the same as the ones
 * found by traversing the edges, for other preferences than the ones the components were created
 * with.
 */
class TransferPathComponentsTest {

  private static final int TOLERANCE_SECONDS = 1;

  private final Graph graph = new Graph();

  private static List<Edge> edges() {
    var a = intersectionVertex("A", 59.0, 10.0);
    var b = intersectionVertex("B", 59.0005, 10.0);
    var c = intersectionVertex("C", 59.0005, 10.001);
    var d = intersectionVertex("D", 59.001, 10.001);
    var e = intersectionVertex("E", 59.001, 10.002);
    var flat = StreetModelForTest.streetEdge(a, b, 55, StreetTraversalPermission.ALL);
    var stairs = StreetModelForTest.streetEdgeBuilder(
      b,
      c,
      12,
      StreetTraversalPermission.PEDESTRIAN,
      true
    ).buildAndConnect();
    var slow = StreetModelForTest.streetEdgeBuilder(c, d, 80, StreetTraversalPermission.ALL)
      .withCarSpeed(5.0f)
      .buildAndConnect();
    var last = StreetModelForTest.streetEdge(d, e, 70, StreetTraversalPermission.ALL);
    return List.of(flat, stairs, slow, last);
  }

  /**
   * A path with an uphill and a downhill edge that have a car speed, so the speed limit of a bike
   * on them is the car speed times the slope factor of the edge, which differs from edge to edge.
   */
  private static List<Edge> hillyEdges() {
    var a = intersectionVertex("A", 59.0, 10.0);
    var b = intersectionVertex("B", 59.0005, 10.0);
    var c = intersectionVertex("C", 59.0005, 10.001);
    var d = intersectionVertex("D", 59.001, 10.001);
    var e = intersectionVertex("E", 59.001, 10.002);
    var flat = StreetModelForTest.streetEdge(a, b, 55, StreetTraversalPermission.ALL);
    var up = hilly(b, c, 80, 2.0f, 0.12);
    var down = hilly(c, d, 90, 3.5f, -0.09);
    var steep = hilly(d, e, 70, 6.0f, 0.2);
    return List.of(flat, up, down, steep);
  }

  private static StreetEdge hilly(
    StreetVertex from,
    StreetVertex to,
    double length,
    float carSpeed,
    double slope
  ) {
    var edge = StreetModelForTest.streetEdgeBuilder(from, to, length, StreetTraversalPermission.ALL)
      .withCarSpeed(carSpeed)
      .buildAndConnect();
    var profile = new PackedCoordinateSequence.Double(new Coordinate[] {
      new Coordinate(0, 0),
      new Coordinate(length, length * slope),
    });
    StreetElevationExtensionBuilder.of(edge)
      .withElevationProfile(profile)
      .withComputed(false)
      .build()
      .ifPresent(edge::setElevationExtension);
    return edge;
  }

  /**
   * A path with traffic lights in every intersection (so every intersection costs a turn), and
   * optionally stop links at both ends, a pathway instead of the stairs, and a cost extension.
   */
  private static List<Edge> edgesWith(boolean links, boolean pathway, boolean costExtension) {
    var a = new LabelledIntersectionVertex("A", 10.0, 59.0, true, true);
    var b = new LabelledIntersectionVertex("B", 10.0, 59.0005, true, true);
    var c = new LabelledIntersectionVertex("C", 10.001, 59.0005, true, true);
    var d = new LabelledIntersectionVertex("D", 10.001, 59.001, true, true);
    var e = new LabelledIntersectionVertex("E", 10.002, 59.001, true, true);
    var flat = StreetModelForTest.streetEdge(a, b, 55, StreetTraversalPermission.ALL);
    if (costExtension) {
      flat.setCostExtension((state, length, mode) -> 3.0);
    }
    Edge middle = pathway
      ? PathwayEdge.createPathwayEdge(b, c, null, 0, 40.0, 0, 0, false)
      : StreetModelForTest.streetEdgeBuilder(
          b,
          c,
          12,
          StreetTraversalPermission.PEDESTRIAN,
          true
        ).buildAndConnect();
    var slow = StreetModelForTest.streetEdgeBuilder(c, d, 80, StreetTraversalPermission.ALL)
      .withCarSpeed(5.0f)
      .buildAndConnect();
    var last = StreetModelForTest.streetEdge(d, e, 70, StreetTraversalPermission.ALL);
    var edges = new ArrayList<Edge>();
    if (links) {
      edges.add(
        StreetTransitStopLink.createStreetTransitStopLink(
          TransitStopVertexBuilderFactory.ofStop(10, new Coordinate(10.0, 59.0)).build(),
          (StreetVertex) a
        )
      );
    }
    edges.addAll(List.of(flat, middle, slow, last));
    if (links) {
      edges.add(
        StreetTransitStopLink.createStreetTransitStopLink(
          (StreetVertex) e,
          TransitStopVertexBuilderFactory.ofStop(11, new Coordinate(10.002, 59.001)).build()
        )
      );
    }
    return edges;
  }

  private static StreetSearchRequest requestWith(
    StreetMode mode,
    ConstantIntersectionTraversalCalculator constant
  ) {
    var routeRequest = RouteRequest.defaultValue()
      .copyOf()
      .withJourney(jb -> jb.withTransfer(mode))
      .buildDefault();
    return StreetSearchRequestMapper.map(routeRequest)
      .withFromEnvelope(null)
      .withToEnvelope(null)
      .withArriveBy(false)
      .withStartTime(Instant.EPOCH)
      .withMode(mode)
      .withIntersectionTraversalCalculator(constant)
      .build();
  }

  @ParameterizedTest
  @ValueSource(doubles = { 0.6, 1.0, 1.35, 2.2 })
  void stopLinksAndTrafficLightsGiveTheCriteriaOfTheTraversalForWalking(double speed) {
    var path = new NearbyStop(id(2), 217, edgesWith(true, false, false), null);
    var components = search(StreetMode.WALK, p -> {}).components(path);
    assertThat(components).isNotSameInstanceAs(TransferSearch.NOT_DERIVABLE);
    var other = search(StreetMode.WALK, p ->
      p.withWalk(w -> w.withSpeed(speed).withReluctance(2.5).withSafetyFactor(0.3))
    );
    assertSameAsTraversal(other, path, components);
  }

  @ParameterizedTest
  @ValueSource(doubles = { 2.0, 3.0, 4.5, 6.0, 8.0 })
  void stopLinksAndTrafficLightsGiveTheCriteriaOfTheTraversalForBicycles(double speed) {
    var path = new NearbyStop(id(2), 217, edgesWith(true, false, false), null);
    var components = search(StreetMode.BIKE, p -> {}).components(path);
    assertThat(components).isNotSameInstanceAs(TransferSearch.NOT_DERIVABLE);
    var other = search(StreetMode.BIKE, p ->
      p.withBike(b -> b.withSpeed(speed).withReluctance(2.0))
    );
    assertSameAsTraversal(other, path, components);
  }

  @ParameterizedTest
  @ValueSource(strings = { "WALK", "BIKE" })
  void aPathWithAPathwayIsNotDerivable(String mode) {
    // The duration of a pathway without a traversal time is distance / walk speed, and its cost
    // is multiplied by the walk reluctance: it is not a constant
    var path = new NearbyStop(id(2), 217, edgesWith(false, true, false), null);
    var search = search(StreetMode.valueOf(mode), p -> {});
    assertThat(search.components(path)).isSameInstanceAs(TransferSearch.NOT_DERIVABLE);
    // The exact criteria change with the walk speed and reluctance, so a constant would be wrong
    var slow = search(StreetMode.WALK, p -> p.withWalk(w -> w.withSpeed(0.5).withReluctance(4.0)));
    var normal = search(StreetMode.WALK, p -> {});
    assertThat(slow.computePathCriteria(path).orElseThrow().c1()).isGreaterThan(
      normal.computePathCriteria(path).orElseThrow().c1() * 2
    );
  }

  @ParameterizedTest
  @ValueSource(strings = { "WALK", "BIKE" })
  void aPathWithACostExtensionIsNotDerivable(String mode) {
    var path = new NearbyStop(id(2), 217, edgesWith(true, false, true), null);
    assertThat(search(StreetMode.valueOf(mode), p -> {}).components(path)).isSameInstanceAs(
      TransferSearch.NOT_DERIVABLE
    );
  }

  @Test
  void aTurnOfAnIntersectionModelThatIsNotSimpleIsNotDerivable() {
    var path = new NearbyStop(id(2), 217, edgesWith(true, false, false), null);
    for (var mode : List.of(StreetMode.WALK, StreetMode.BIKE)) {
      for (double duration : new double[] { 0.0, 5.0 }) {
        // Traffic lights are not charged by the constant model, so even with 0 s the lights of the
        // path would be wrong in the formulas of the components
        var request = requestWith(mode, new ConstantIntersectionTraversalCalculator(duration));
        var components =
          mode == StreetMode.WALK
            ? TransferPathComponents.walk(path, request)
            : TransferPathComponents.bike(path, request);
        assertThat(components).isSameInstanceAs(TransferSearch.NOT_DERIVABLE);
      }
    }
  }

  @Test
  void aConstantIntersectionModelIsDerivableWhenNoTurnCostsAnything() {
    // No lights: the intersections between two edges are free flowing, no turn is costed
    var path = new NearbyStop(id(2), 217, edges(), null);
    var request = requestWith(StreetMode.WALK, new ConstantIntersectionTraversalCalculator(0.0));
    assertThat(TransferPathComponents.walk(path, request)).isNotSameInstanceAs(
      TransferSearch.NOT_DERIVABLE
    );
  }

  @ParameterizedTest
  @ValueSource(doubles = { 2.0, 5.0, 8.0 })
  void aBicyclePathWithoutEdgesIsTheDistanceOverTheSpeed(double speed) {
    var path = new NearbyStop(id(2), 300, List.of(), null);
    var base = search(StreetMode.BIKE, p -> {});
    var other = search(StreetMode.BIKE, p -> p.withBike(b -> b.withSpeed(speed)));
    assertSameAsTraversal(other, path, base.components(path));
  }

  private DefaultTransferSearch search(StreetMode mode, Consumer<RoutingPreferencesBuilder> prefs) {
    graph.addVertex(TransitStopVertexBuilderFactory.ofStop(1, new Coordinate(10.0, 59.0)).build());
    graph.index();
    var request = RouteRequest.defaultValue()
      .copyOf()
      .withJourney(jb -> jb.withTransfer(mode))
      .withPreferences(prefs)
      .buildDefault();
    return new DefaultTransferSearch(
      graph,
      null,
      stopId -> true,
      stopId -> true,
      stopId -> Set.of(),
      request
    );
  }

  @ParameterizedTest
  @ValueSource(doubles = { 0.6, 1.0, 1.35, 2.2 })
  void walkingComponentsGiveTheCriteriaOfTheTraversal(double speed) {
    var path = new NearbyStop(id(2), 217, edges(), null);
    var base = search(StreetMode.WALK, p -> {});
    var components = base.components(path);
    var other = search(StreetMode.WALK, p ->
      p.withWalk(w ->
        w.withSpeed(speed).withReluctance(3.5).withStairsReluctance(5.0).withSafetyFactor(0.4)
      )
    );
    assertSameAsTraversal(other, path, components);
  }

  @ParameterizedTest
  @ValueSource(doubles = { 2.0, 3.0, 4.5, 6.0, 8.0 })
  void bicycleComponentsGiveTheCriteriaOfTheTraversal(double speed) {
    var path = new NearbyStop(id(2), 217, edges(), null);
    var base = search(StreetMode.BIKE, p -> {});
    var components = base.components(path);
    var other = search(StreetMode.BIKE, p ->
      p.withBike(b -> b.withSpeed(speed).withReluctance(3.0))
    );
    assertSameAsTraversal(other, path, components);
  }

  @ParameterizedTest
  @ValueSource(doubles = { 2.5, 3.0, 4.0, 5.0, 6.5, 8.0, 9.0 })
  void slopesChangeTheSpeedLimitOfABikeOnAnEdge(double speed) {
    var edges = hillyEdges();
    // The test is only meaningful if the slope changes the effective distance of an edge
    assertThat(
      edges
        .stream()
        .filter(StreetEdge.class::isInstance)
        .map(StreetEdge.class::cast)
        .anyMatch(e -> e.getEffectiveBikeDistance() > 1.05 * e.getDistanceMeters())
    ).isTrue();
    var path = new NearbyStop(id(2), 295, edges, null);
    var components = search(StreetMode.BIKE, p -> {}).components(path);
    assertThat(components).isNotSameInstanceAs(TransferSearch.NOT_DERIVABLE);
    var other = search(StreetMode.BIKE, p ->
      p.withBike(b -> b.withSpeed(speed).withReluctance(2.5))
    );
    assertSameAsTraversal(other, path, components);
  }

  @ParameterizedTest
  @ValueSource(doubles = { 0.5, 1.4 })
  void aPathWithoutEdgesIsTheDistanceOverTheSpeed(double speed) {
    var path = new NearbyStop(id(2), 300, List.of(), null);
    var base = search(StreetMode.WALK, p -> {});
    var other = search(StreetMode.WALK, p -> p.withWalk(w -> w.withSpeed(speed)));
    assertSameAsTraversal(other, path, base.components(path));
  }

  @ParameterizedTest
  @ValueSource(strings = { "CAR" })
  void carsAreNotSupported(String mode) {
    var path = new NearbyStop(id(2), 217, edges(), null);
    var search = search(StreetMode.valueOf(mode), p -> {});
    assertThat(search.components(path)).isNull();
    assertThat(search.valueFunction()).isNull();
  }

  private static void assertSameAsTraversal(
    DefaultTransferSearch search,
    NearbyStop path,
    float[] components
  ) {
    var function = search.valueFunction();
    assertThat(function).isNotNull();
    var expected = search.computePathCriteria(path).orElseThrow();
    long packed = function.evaluate(components, 0, components.length);
    assertThat(
      Math.abs(TransferValueFunction.durationInSeconds(packed) - expected.durationInSeconds())
    ).isAtMost(TOLERANCE_SECONDS);
    assertThat(Math.abs(TransferValueFunction.c1(packed) - expected.c1())).isAtMost(
      TOLERANCE_SECONDS * 100
    );
  }
}
