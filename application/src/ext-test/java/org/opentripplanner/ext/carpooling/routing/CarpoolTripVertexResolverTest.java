package org.opentripplanner.ext.carpooling.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.opentripplanner.TestOtpModel;
import org.opentripplanner.ext.carpooling.CarpoolTripTestData;
import org.opentripplanner.ext.carpooling.util.CarAccessibleVertexSnapper;
import org.opentripplanner.routing.algorithm.GraphRoutingTest;
import org.opentripplanner.routing.linking.VertexLinkerTestFactory;
import org.opentripplanner.routing.linking.internal.VertexCreationService;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.street.model.StreetTraversalPermission;
import org.opentripplanner.street.model.vertex.IntersectionVertex;

class CarpoolTripVertexResolverTest extends GraphRoutingTest {

  /**
   * Graph-matched probe distances, like in {@code CarAccessibleVertexSnapperTest}: the test
   * streets span ~100 m, so a curb must escape 50 m to count as reachable and a ~35 m island
   * cannot.
   */
  private static final CarAccessibleVertexSnapper SNAPPER = new CarAccessibleVertexSnapper(50, 200);

  /**
   * A mid-edge route point resolves to the nearer endpoint of the linked edge — a permanent
   * vertex — and one vertex is produced per route point, in route order. Graph: a two-way
   * all-modes street {@code A --(100 m)-- B}; the trip boards ~30 % along it (nearer A) and
   * alights ~80 % along it (nearer B).
   */
  @Test
  void midEdgeRoutePointsResolveToNearerEndpoints() {
    var v = new IntersectionVertex[2];
    var model = modelOf(
      new GraphRoutingTest.Builder() {
        @Override
        public void build() {
          v[0] = intersection("A", 60.0000, 10.0000);
          v[1] = intersection("B", 60.0000, 10.0018);
          street(v[0], v[1], 100, StreetTraversalPermission.ALL, StreetTraversalPermission.ALL);
        }
      }
    );

    var trip = CarpoolTripTestData.createSimpleTrip(
      new WgsCoordinate(60.0000, 10.0005),
      new WgsCoordinate(60.0000, 10.0014)
    );
    var resolved = resolverFor(model).resolve(trip);

    assertNotNull(resolved);
    assertEquals(v[0], resolved.vertices().get(0));
    assertEquals(v[1], resolved.vertices().get(1));
  }

  /**
   * A route point exactly at an intersection resolves to that vertex, without any offset.
   */
  @Test
  void routePointAtIntersectionResolvesToThatVertex() {
    var v = new IntersectionVertex[2];
    var model = modelOf(
      new GraphRoutingTest.Builder() {
        @Override
        public void build() {
          v[0] = intersection("A", 60.0000, 10.0000);
          v[1] = intersection("B", 60.0000, 10.0018);
          street(v[0], v[1], 100, StreetTraversalPermission.ALL, StreetTraversalPermission.ALL);
        }
      }
    );

    var trip = CarpoolTripTestData.createSimpleTrip(v[0].toWgsCoordinate(), v[1].toWgsCoordinate());
    var resolved = resolverFor(model).resolve(trip);

    assertNotNull(resolved);
    assertEquals(v[0], resolved.vertices().get(0));
    assertEquals(v[1], resolved.vertices().get(1));
  }

  /**
   * Resolution works on car-only streets, where no walk search could move at all: the endpoints of
   * the linked edge are tried directly against the reachability check.
   */
  @Test
  void carOnlyStreetResolvesViaEdgeEndpoints() {
    var v = new IntersectionVertex[2];
    var model = modelOf(
      new GraphRoutingTest.Builder() {
        @Override
        public void build() {
          v[0] = intersection("C1", 60.0000, 10.0000);
          v[1] = intersection("C2", 60.0000, 10.0018);
          street(v[0], v[1], 100, StreetTraversalPermission.CAR, StreetTraversalPermission.CAR);
        }
      }
    );

    var trip = CarpoolTripTestData.createSimpleTrip(
      new WgsCoordinate(60.0000, 10.0005),
      new WgsCoordinate(60.0000, 10.0014)
    );
    var resolved = resolverFor(model).resolve(trip);

    assertNotNull(resolved);
    assertEquals(v[0], resolved.vertices().get(0));
    assertEquals(v[1], resolved.vertices().get(1));
  }

  /**
   * A route point in a car-inaccessible pocket is nudged out by the walk fallback: both endpoints
   * of the linked island edge fail the reachability check, so the point resolves to the nearest
   * genuinely reachable permanent vertex outside the pocket. Graph:
   * <pre>
   *   I1 --(car, ~35 m)-- I2 --(ped, ~35 m)-- M1 --(car)-- M2 --(car)-- M3
   * </pre>
   * The boarding point sits mid {@code I1–I2} (an island too small to escape); the alighting point
   * sits mid {@code M2–M3}.
   */
  @Test
  void trappedRoutePointResolvesViaWalkFallback() {
    var v = new IntersectionVertex[5];
    var model = modelOf(
      new GraphRoutingTest.Builder() {
        @Override
        public void build() {
          v[0] = intersection("I1", 60.0000, 10.0000);
          v[1] = intersection("I2", 60.0000, 10.0006);
          v[2] = intersection("M1", 60.0000, 10.0012);
          v[3] = intersection("M2", 60.0000, 10.0022);
          v[4] = intersection("M3", 60.0000, 10.0032);
          street(v[0], v[1], 35, StreetTraversalPermission.ALL, StreetTraversalPermission.ALL);
          street(
            v[1],
            v[2],
            35,
            StreetTraversalPermission.PEDESTRIAN,
            StreetTraversalPermission.PEDESTRIAN
          );
          street(v[2], v[3], 60, StreetTraversalPermission.ALL, StreetTraversalPermission.ALL);
          street(v[3], v[4], 60, StreetTraversalPermission.ALL, StreetTraversalPermission.ALL);
        }
      }
    );

    var trip = CarpoolTripTestData.createSimpleTrip(
      new WgsCoordinate(60.0000, 10.0003),
      new WgsCoordinate(60.0000, 10.0027)
    );
    var resolved = resolverFor(model).resolve(trip);

    assertNotNull(resolved);
    assertEquals(
      v[2],
      resolved.vertices().get(0),
      "The trapped island point must be nudged to the first reachable curb M1"
    );
  }

  /**
   * A trip with any unresolvable route point resolves to {@code null}: here the boarding point
   * sits on a car-only island — its endpoints are unreachable and the walk fallback cannot
   * traverse the car-only edge either — so the whole trip is unroutable.
   */
  @Test
  void tripWithUnresolvableRoutePointResolvesToNull() {
    var v = new IntersectionVertex[4];
    var model = modelOf(
      new GraphRoutingTest.Builder() {
        @Override
        public void build() {
          v[0] = intersection("I1", 60.0000, 10.0000);
          v[1] = intersection("I2", 60.0000, 10.0006);
          v[2] = intersection("M1", 60.0100, 10.0000);
          v[3] = intersection("M2", 60.0100, 10.0018);
          street(v[0], v[1], 35, StreetTraversalPermission.CAR, StreetTraversalPermission.CAR);
          street(v[2], v[3], 100, StreetTraversalPermission.ALL, StreetTraversalPermission.ALL);
        }
      }
    );

    var trip = CarpoolTripTestData.createSimpleTrip(
      new WgsCoordinate(60.0000, 10.0003),
      new WgsCoordinate(60.0100, 10.0009)
    );

    assertNull(resolverFor(model).resolve(trip));
  }

  private static CarpoolTripVertexResolver resolverFor(TestOtpModel model) {
    return new CarpoolTripVertexResolver(
      new VertexCreationService(VertexLinkerTestFactory.of(model.graph())),
      SNAPPER
    );
  }
}
