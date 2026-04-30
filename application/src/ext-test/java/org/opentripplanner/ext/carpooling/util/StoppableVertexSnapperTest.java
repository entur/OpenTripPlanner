package org.opentripplanner.ext.carpooling.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.routing.algorithm.GraphRoutingTest;
import org.opentripplanner.street.model.StreetTraversalPermission;
import org.opentripplanner.street.model.vertex.IntersectionVertex;
import org.opentripplanner.street.search.request.StreetSearchRequest;

class StoppableVertexSnapperTest extends GraphRoutingTest {

  private IntersectionVertex A;
  private IntersectionVertex B;
  private IntersectionVertex C;
  private IntersectionVertex D;

  /**
   * Graph layout:
   * <pre>
   *   A --(pedestrian)-- B --(pedestrian)-- C --(all modes)-- D
   * </pre>
   * C and D are stoppable by a car (C thanks to the CD edge; D also via CD). A and B are on
   * pedestrian-only edges and cannot be reached by a driver.
   */
  @BeforeEach
  void setUp() {
    modelOf(
      new GraphRoutingTest.Builder() {
        @Override
        public void build() {
          A = intersection("A", 59.9139, 10.7522);
          B = intersection("B", 59.9139, 10.7530);
          C = intersection("C", 59.9139, 10.7540);
          D = intersection("D", 59.9139, 10.7548);

          street(
            A,
            B,
            50,
            StreetTraversalPermission.PEDESTRIAN,
            StreetTraversalPermission.PEDESTRIAN
          );
          street(
            B,
            C,
            70,
            StreetTraversalPermission.PEDESTRIAN,
            StreetTraversalPermission.PEDESTRIAN
          );
          street(C, D, 50, StreetTraversalPermission.ALL, StreetTraversalPermission.ALL);
        }
      }
    );
  }

  @Test
  void alreadyStoppableVertex_returnsSameVertexWithoutWalk() {
    var result = StoppableVertexSnapper.snapPickup(
      StreetSearchRequest.DEFAULT,
      D,
      Duration.ofMinutes(10)
    );
    assertNotNull(result);
    assertEquals(D, result.vertex());
    assertNull(result.walkPath());
  }

  @Test
  void pedestrianOnlyVertex_withinBudget_snapsToNearestStoppable() {
    var result = StoppableVertexSnapper.snapPickup(
      StreetSearchRequest.DEFAULT,
      A,
      Duration.ofMinutes(10)
    );
    assertNotNull(result);
    assertEquals(C, result.vertex());
    assertNotNull(result.walkPath());
    assertTrue(result.walkPath().getDuration() > 0);
  }

  @Test
  void pedestrianOnlyVertex_budgetTooTight_returnsNull() {
    var result = StoppableVertexSnapper.snapPickup(
      StreetSearchRequest.DEFAULT,
      A,
      Duration.ofSeconds(5)
    );
    assertNull(result);
  }

  @Test
  void arriveBySearch_walksBackwardsAlongIncomingEdges() {
    var result = StoppableVertexSnapper.snapDropoff(
      StreetSearchRequest.DEFAULT,
      A,
      Duration.ofMinutes(10)
    );
    assertNotNull(result);
    assertEquals(C, result.vertex());
    assertNotNull(result.walkPath());
    // Walk path must be chronological: starts at C (the stoppable vertex) and ends at A.
    assertEquals(C, result.walkPath().states.getFirst().getVertex());
    assertEquals(A, result.walkPath().states.getLast().getVertex());
    assertTrue(result.walkPath().getDuration() > 0);
  }

  /**
   * A vertex with car edges in only one direction is not stoppable: the carpool driver picks up
   * mid-route and must both arrive at and leave the pickup, so a pedestrian-zone vertex hanging
   * off a one-way drivable exit (CAR outgoing only, no incoming) and the corresponding terminus
   * (CAR incoming only, no outgoing) both fail the predicate. The snapper must skip them and walk
   * on to a vertex with car edges in both directions.
   * <p>
   * Graph: {@code S --(ped)-- V --(one-way CAR forward)--> W --(ped only)-- X --(two-way CAR)-- Y}.
   * V has outgoing CAR but no incoming; W has incoming CAR but no outgoing; X is the first
   * fully stoppable vertex (it has the X↔Y bidirectional drivable pair).
   */
  @Test
  void halfStoppableVertexIsRejected() {
    var holder = new IntersectionVertex[5];
    modelOf(
      new GraphRoutingTest.Builder() {
        @Override
        public void build() {
          holder[0] = intersection("S", 60.0000, 10.0000);
          holder[1] = intersection("V", 60.0000, 10.0010);
          holder[2] = intersection("W", 60.0000, 10.0020);
          holder[3] = intersection("X", 60.0000, 10.0030);
          holder[4] = intersection("Y", 60.0000, 10.0040);

          street(
            holder[0],
            holder[1],
            50,
            StreetTraversalPermission.PEDESTRIAN,
            StreetTraversalPermission.PEDESTRIAN
          );
          // V→W: forward allows cars, reverse pedestrian only. V has out-CAR only; W has in-CAR only.
          street(
            holder[1],
            holder[2],
            50,
            StreetTraversalPermission.ALL,
            StreetTraversalPermission.PEDESTRIAN
          );
          // W↔X: pedestrian both ways, so the walker can keep going but neither side gains CAR here.
          street(
            holder[2],
            holder[3],
            50,
            StreetTraversalPermission.PEDESTRIAN,
            StreetTraversalPermission.PEDESTRIAN
          );
          // X↔Y: bidirectional ALL — X gets in-CAR (from Y) and out-CAR (to Y), so X is stoppable.
          street(
            holder[3],
            holder[4],
            50,
            StreetTraversalPermission.ALL,
            StreetTraversalPermission.ALL
          );
        }
      }
    );

    var result = StoppableVertexSnapper.snapPickup(
      StreetSearchRequest.DEFAULT,
      holder[0],
      Duration.ofMinutes(10)
    );

    assertNotNull(result);
    assertEquals(holder[3], result.vertex(), "Should skip half-stoppable V and W and snap to X");
  }

  /**
   * Edges flagged {@code motorVehicleNoThruTraffic} (e.g. OSM {@code access=destination} on a
   * residential street) still allow cars locally; the no-thru state machine only blocks a
   * traversal that enters such a zone from outside and then tries to leave on a normal edge.
   * A carpool driver whose own trip origin or destination is inside the same noThru zone routes
   * correctly through it, so the snapper must not pre-emptively reject these vertices — doing so
   * would silently drop intra-neighbourhood matches.
   * <p>
   * Graph: {@code S --(ped)-- V --(CAR noThru both ways)-- W}. V is the closest car-permitting
   * vertex; the snap should land there even though its CAR edges are flagged noThru.
   */
  @Test
  void noThruTrafficVertexIsAccepted() {
    var holder = new IntersectionVertex[3];
    modelOf(
      new GraphRoutingTest.Builder() {
        @Override
        public void build() {
          holder[0] = intersection("S", 60.0000, 10.0000);
          holder[1] = intersection("V", 60.0000, 10.0010);
          holder[2] = intersection("W", 60.0000, 10.0020);

          street(
            holder[0],
            holder[1],
            50,
            StreetTraversalPermission.PEDESTRIAN,
            StreetTraversalPermission.PEDESTRIAN
          );
          // V↔W: cars allowed but local-access only — both edges flagged noThru below.
          var noThruEdges = street(
            holder[1],
            holder[2],
            50,
            StreetTraversalPermission.ALL,
            StreetTraversalPermission.ALL
          );
          noThruEdges.forEach(e -> e.setMotorVehicleNoThruTraffic(true));
        }
      }
    );

    var result = StoppableVertexSnapper.snapPickup(
      StreetSearchRequest.DEFAULT,
      holder[0],
      Duration.ofMinutes(10)
    );

    assertNotNull(result);
    assertEquals(
      holder[1],
      result.vertex(),
      "noThru CAR edges in both directions should still count as stoppable"
    );
  }

  /**
   * The walk A* must run with the supplied request's preferences, not the library defaults.
   * Doubling {@code walkReluctance} on otherwise-identical requests should roughly double the
   * walk path's weight; the buggy variant ignored the parameter and returned the same weight
   * regardless.
   */
  @Test
  void walkPathWeightUsesSuppliedWalkReluctance() {
    var lowReluctance = StreetSearchRequest.copyOf(StreetSearchRequest.DEFAULT)
      .withWalk(b -> b.withReluctance(1.0))
      .build();
    var highReluctance = StreetSearchRequest.copyOf(StreetSearchRequest.DEFAULT)
      .withWalk(b -> b.withReluctance(4.0))
      .build();

    var lowResult = StoppableVertexSnapper.snapPickup(lowReluctance, A, Duration.ofMinutes(10));
    var highResult = StoppableVertexSnapper.snapPickup(highReluctance, A, Duration.ofMinutes(10));

    assertNotNull(lowResult);
    assertNotNull(highResult);
    assertNotNull(lowResult.walkPath());
    assertNotNull(highResult.walkPath());
    double lowWeight = lowResult.walkPath().getWeight();
    double highWeight = highResult.walkPath().getWeight();
    assertTrue(lowWeight > 1, "Expected non-trivial low-reluctance weight, got " + lowWeight);
    assertTrue(highWeight > 1, "Expected non-trivial high-reluctance weight, got " + highWeight);
    assertTrue(
      highWeight > lowWeight * 2,
      "Weight should scale with walk reluctance: low=" + lowWeight + ", high=" + highWeight
    );
  }
}
