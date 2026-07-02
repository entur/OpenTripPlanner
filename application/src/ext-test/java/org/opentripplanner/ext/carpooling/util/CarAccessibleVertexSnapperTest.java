package org.opentripplanner.ext.carpooling.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.street.model.edge.TemporaryFreeEdge.createTemporaryFreeEdge;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.routing.algorithm.GraphRoutingTest;
import org.opentripplanner.street.model.StreetTraversalPermission;
import org.opentripplanner.street.model.vertex.IntersectionVertex;
import org.opentripplanner.street.model.vertex.TemporaryStreetLocation;
import org.opentripplanner.street.search.request.StreetSearchRequest;

class CarAccessibleVertexSnapperTest extends GraphRoutingTest {

  private IntersectionVertex A;
  private IntersectionVertex B;
  private IntersectionVertex C;
  private IntersectionVertex D;

  /**
   * Deliberately small probe distances so the reachability check accepts curbs in these compact
   * test graphs, whose car edges span only tens of metres. Production distances are hundreds of
   * metres; the reachability-specific tests below construct their own snapper with graph-matched
   * distances.
   */
  private final CarAccessibleVertexSnapper snapper = new CarAccessibleVertexSnapper(20, 60);

  /**
   * Graph layout:
   * <pre>
   *   A --(pedestrian)-- B --(pedestrian)-- C --(all modes)-- D
   * </pre>
   * C and D are car-accessible (C thanks to the CD edge; D also via CD). A and B are on
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
  void alreadyCarAccessibleVertex_returnsSameVertexWithoutWalk() {
    var result = snapper.snapPickup(StreetSearchRequest.DEFAULT, D, Duration.ofMinutes(10));
    assertNotNull(result);
    assertEquals(D, result.vertex());
    assertNull(result.walkPath());
  }

  @Test
  void pedestrianOnlyVertex_withinBudget_snapsToNearestCarAccessible() {
    var result = snapper.snapPickup(StreetSearchRequest.DEFAULT, A, Duration.ofMinutes(10));
    assertNotNull(result);
    assertEquals(C, result.vertex());
    assertNotNull(result.walkPath());
    assertTrue(result.walkPath().getDuration() > 0);
  }

  @Test
  void pedestrianOnlyVertex_budgetTooTight_returnsNull() {
    var result = snapper.snapPickup(StreetSearchRequest.DEFAULT, A, Duration.ofSeconds(5));
    assertNull(result);
  }

  @Test
  void arriveBySearch_walksBackwardsAlongIncomingEdges() {
    var result = snapper.snapDropoff(StreetSearchRequest.DEFAULT, A, Duration.ofMinutes(10));
    assertNotNull(result);
    assertEquals(C, result.vertex());
    assertNotNull(result.walkPath());
    // Walk path must be chronological: starts at C (the car-accessible vertex) and ends at A.
    assertEquals(C, result.walkPath().states.getFirst().getVertex());
    assertEquals(A, result.walkPath().states.getLast().getVertex());
    assertTrue(result.walkPath().getDuration() > 0);
  }

  /**
   * A vertex with car edges in only one direction is not car-accessible: the carpool driver picks
   * up mid-route and must both arrive at and leave the pickup, so a pedestrian-zone vertex hanging
   * off a one-way drivable exit (CAR outgoing only, no incoming) and the corresponding terminus
   * (CAR incoming only, no outgoing) both fail the predicate. The snapper must skip them and walk
   * on to a vertex with car edges in both directions.
   * <p>
   * Graph: {@code S --(ped)-- V --(one-way CAR forward)--> W --(ped only)-- X --(two-way CAR)-- Y}.
   * V has outgoing CAR but no incoming; W has incoming CAR but no outgoing; X is the first
   * fully car-accessible vertex (it has the X↔Y bidirectional drivable pair).
   */
  @Test
  void halfCarAccessibleVertexIsRejected() {
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
          // X↔Y: bidirectional ALL — X gets in-CAR (from Y) and out-CAR (to Y), so X is car-accessible.
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

    var result = snapper.snapPickup(StreetSearchRequest.DEFAULT, holder[0], Duration.ofMinutes(10));

    assertNotNull(result);
    assertEquals(
      holder[3],
      result.vertex(),
      "Should skip half-car-accessible V and W and snap to X"
    );
  }

  /**
   * The snap must minimize <em>generalized walk weight</em> across reachable car-accessible
   * vertices, not raw distance and not graph-traversal order. Two car-accessible vertices are
   * reachable from S: X is geometrically closer (50m) but reached via an edge with
   * {@code walkSafetyFactor=10}, while Y is twice as far (100m) along a normal-safety edge. By
   * weight Y is cheaper (≈100 weight units vs ≈500), so the snapper must return Y. A buggy
   * implementation that returned the first car-accessible vertex encountered by distance — or
   * that paired the dominance function with a non-trivial heuristic that broke cost-ascending
   * pop order — would land on X.
   * <p>
   * <pre>
   *   S --(walk  50 m, safety x10)--> X &lt;--car--&gt; Xc        weight ~500
   *   S --(walk 100 m, safety  x1)--> Y &lt;--car--&gt; Yc        weight ~100  (cheaper)
   * </pre>
   * Both X and Y are car-accessible: their bidirectional CAR edges to Xc / Yc give them car-in
   * <em>and</em> car-out. The S→X edge is half the distance but its safety factor blows the
   * weight up; S→Y wins despite being farther.
   */
  @Test
  void snapsToMinimumWeightVertex_notNearestByDistance() {
    var holder = new IntersectionVertex[5];
    modelOf(
      new GraphRoutingTest.Builder() {
        @Override
        public void build() {
          holder[0] = intersection("S", 60.0000, 10.0000);
          holder[1] = intersection("X", 60.0000, 10.0006);
          holder[2] = intersection("Xc", 60.0000, 10.0012);
          holder[3] = intersection("Y", 60.0000, 9.9988);
          holder[4] = intersection("Yc", 60.0000, 9.9982);

          // S → X: short (50m) but penalized — walkSafetyFactor 10 makes its weight ≈ 500.
          var sx = street(
            holder[0],
            holder[1],
            50,
            StreetTraversalPermission.PEDESTRIAN,
            StreetTraversalPermission.PEDESTRIAN
          );
          sx.forEach(e -> e.setWalkSafetyFactor(10.0f));

          // S → Y: longer (100m) but normal safety — weight ≈ 100, the cheaper snap.
          street(
            holder[0],
            holder[3],
            100,
            StreetTraversalPermission.PEDESTRIAN,
            StreetTraversalPermission.PEDESTRIAN
          );

          // X ↔ Xc and Y ↔ Yc give X and Y bidirectional CAR access, making them car-accessible.
          street(
            holder[1],
            holder[2],
            50,
            StreetTraversalPermission.ALL,
            StreetTraversalPermission.ALL
          );
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

    var result = snapper.snapPickup(StreetSearchRequest.DEFAULT, holder[0], Duration.ofMinutes(10));

    assertNotNull(result);
    assertNotNull(result.walkPath(), "S is pedestrian-only — a real walk path is expected");
    assertEquals(
      holder[3],
      result.vertex(),
      "Should pick Y (low-weight, farther) over X (penalized, closer) by minimum generalized weight"
    );
  }

  /**
   * The walk A* must run with the supplied request's preferences, not the library defaults.
   * Doubling {@code walkReluctance} on otherwise-identical requests should roughly double the
   * walk path's weight
   */
  @Test
  void walkPathWeightUsesSuppliedWalkReluctance() {
    var lowReluctance = StreetSearchRequest.copyOf(StreetSearchRequest.DEFAULT)
      .withWalk(b -> b.withReluctance(1.0))
      .build();
    var highReluctance = StreetSearchRequest.copyOf(StreetSearchRequest.DEFAULT)
      .withWalk(b -> b.withReluctance(4.0))
      .build();

    var lowResult = snapper.snapPickup(lowReluctance, A, Duration.ofMinutes(10));
    var highResult = snapper.snapPickup(highReluctance, A, Duration.ofMinutes(10));

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

  /**
   * A curb on a car network that extends beyond the escape distance in both directions is accepted.
   * Graph: {@code O --(ped)-- P --(car)-- P1 --(car)-- P2}, with P–P1–P2 spanning ~111 m of
   * two-way car street; a car can both drive away from P and reach it, so the pedestrian origin O
   * snaps to P.
   */
  @Test
  void connectedCarVertex_isAccepted() {
    var v = new IntersectionVertex[4];
    modelOf(
      new GraphRoutingTest.Builder() {
        @Override
        public void build() {
          v[0] = intersection("O", 60.0000, 10.0000);
          v[1] = intersection("P", 60.0000, 10.0010);
          v[2] = intersection("P1", 60.0000, 10.0020);
          v[3] = intersection("P2", 60.0000, 10.0030);
          street(
            v[0],
            v[1],
            60,
            StreetTraversalPermission.PEDESTRIAN,
            StreetTraversalPermission.PEDESTRIAN
          );
          street(v[1], v[2], 60, StreetTraversalPermission.ALL, StreetTraversalPermission.ALL);
          street(v[2], v[3], 60, StreetTraversalPermission.ALL, StreetTraversalPermission.ALL);
        }
      }
    );

    var reachabilitySnapper = new CarAccessibleVertexSnapper(100, 250);
    var result = reachabilitySnapper.snapPickup(
      StreetSearchRequest.DEFAULT,
      v[0],
      Duration.ofMinutes(10)
    );

    assertNotNull(result);
    assertEquals(v[1], result.vertex());
  }

  /**
   * A curb whose only car edges form a tiny disconnected island — car-permitting "on paper" but no
   * car can drive to or from it beyond the island — is rejected, and the snapper walks on to a
   * genuinely reachable curb. Graph: {@code IslC --(car)-- Isl --(ped)-- O --(ped)-- Q --(car)--
   * Q1 --(car)-- Q2}. The island {@code Isl↔IslC} spans only ~33 m (below the 100 m escape
   * distance), while {@code Q–Q1–Q2} spans ~111 m; Isl is closer to O but rejected, so O snaps to
   * Q.
   */
  @Test
  void islandCarVertex_isRejected_snapsToReachableCurb() {
    var v = new IntersectionVertex[6];
    modelOf(
      new GraphRoutingTest.Builder() {
        @Override
        public void build() {
          v[0] = intersection("O", 60.0000, 10.0000);
          v[1] = intersection("Isl", 60.0000, 10.0006);
          v[2] = intersection("IslC", 60.0000, 10.0012);
          v[3] = intersection("Q", 60.0000, 9.9990);
          v[4] = intersection("Q1", 60.0000, 9.9980);
          v[5] = intersection("Q2", 60.0000, 9.9970);
          street(
            v[0],
            v[1],
            40,
            StreetTraversalPermission.PEDESTRIAN,
            StreetTraversalPermission.PEDESTRIAN
          );
          street(v[1], v[2], 40, StreetTraversalPermission.ALL, StreetTraversalPermission.ALL);
          street(
            v[0],
            v[3],
            60,
            StreetTraversalPermission.PEDESTRIAN,
            StreetTraversalPermission.PEDESTRIAN
          );
          street(v[3], v[4], 60, StreetTraversalPermission.ALL, StreetTraversalPermission.ALL);
          street(v[4], v[5], 60, StreetTraversalPermission.ALL, StreetTraversalPermission.ALL);
        }
      }
    );

    var reachabilitySnapper = new CarAccessibleVertexSnapper(100, 250);
    var result = reachabilitySnapper.snapPickup(
      StreetSearchRequest.DEFAULT,
      v[0],
      Duration.ofMinutes(10)
    );

    assertNotNull(result);
    assertEquals(
      v[3],
      result.vertex(),
      "Should skip the island Isl and snap to the connected curb Q"
    );
  }

  /**
   * A curb a car can be driven <em>to</em> but not away <em>from</em> (or vice versa) is rejected:
   * mid-route pickups require both directions. Graph (all car edges one-way, reverse pedestrian):
   * {@code U2 --> U1 --> T --> Td}, with T linked to the pedestrian origin O. A car can reach T
   * from far up the {@code U2→U1→T} chain (~111 m), but leaving T dead-ends at Td after only
   * ~67 m — below the 100 m escape distance — so the forward probe fails and T is rejected. No
   * other curb is reachable, so the snap returns {@code null}.
   */
  @Test
  void directionalDeadEndVertex_isRejected() {
    var v = new IntersectionVertex[5];
    modelOf(
      new GraphRoutingTest.Builder() {
        @Override
        public void build() {
          v[0] = intersection("O", 60.0000, 10.0000);
          v[1] = intersection("T", 60.0000, 10.0006);
          v[2] = intersection("U1", 60.0000, 10.0016);
          v[3] = intersection("U2", 60.0000, 10.0026);
          v[4] = intersection("Td", 60.0000, 9.9994);
          street(
            v[0],
            v[1],
            40,
            StreetTraversalPermission.PEDESTRIAN,
            StreetTraversalPermission.PEDESTRIAN
          );
          // One-way car toward T: U2 → U1 → T. T gains incoming car; walkers may go back (pedestrian).
          street(
            v[3],
            v[2],
            60,
            StreetTraversalPermission.ALL,
            StreetTraversalPermission.PEDESTRIAN
          );
          street(
            v[2],
            v[1],
            60,
            StreetTraversalPermission.ALL,
            StreetTraversalPermission.PEDESTRIAN
          );
          // One-way car away from T into a dead-end: T → Td. T gains outgoing car, but it leads nowhere.
          street(
            v[1],
            v[4],
            40,
            StreetTraversalPermission.ALL,
            StreetTraversalPermission.PEDESTRIAN
          );
        }
      }
    );

    var reachabilitySnapper = new CarAccessibleVertexSnapper(100, 250);
    var result = reachabilitySnapper.snapPickup(
      StreetSearchRequest.DEFAULT,
      v[0],
      Duration.ofMinutes(10)
    );

    assertNull(result, "T can be arrived at but not departed far enough; no reachable curb exists");
  }

  /**
   * A permanent vertex's reachability verdict is a function of the permanent street graph only.
   * Temporary linking is visible to every concurrent search and {@code TemporaryFreeEdge}s perform
   * no mode check, so a temporary hub joining a stranded island to the mainland offers the probe a
   * way out that no car can actually drive — and, once cached, the wrong verdict would outlive the
   * hub. The probe must ignore temporary edges: the island stays rejected while the bridge exists.
   * <p>
   * Graph: {@code IslC --(car, ~33 m)-- Isl}, {@code M1 --(car)-- M2 --(car)-- M3} (~111 m), and a
   * {@code TemporaryStreetLocation} hub with free edges in both directions to both {@code Isl} and
   * {@code M1}.
   */
  @Test
  void permanentVertexVerdict_ignoresTemporaryEdges() {
    var v = new IntersectionVertex[5];
    var hubHolder = new TemporaryStreetLocation[1];
    modelOf(
      new GraphRoutingTest.Builder() {
        @Override
        public void build() {
          v[0] = intersection("Isl", 60.0000, 10.0000);
          v[1] = intersection("IslC", 60.0000, 10.0006);
          v[2] = intersection("M1", 60.0000, 9.9994);
          v[3] = intersection("M2", 60.0000, 9.9984);
          v[4] = intersection("M3", 60.0000, 9.9974);
          street(v[0], v[1], 40, StreetTraversalPermission.ALL, StreetTraversalPermission.ALL);
          street(v[2], v[3], 60, StreetTraversalPermission.ALL, StreetTraversalPermission.ALL);
          street(v[3], v[4], 60, StreetTraversalPermission.ALL, StreetTraversalPermission.ALL);

          var hub = streetLocation("bridge", 60.0000, 9.9997);
          createTemporaryFreeEdge(v[0], hub);
          link(hub, v[2]);
          createTemporaryFreeEdge(v[2], hub);
          link(hub, v[0]);
          hubHolder[0] = hub;
        }
      }
    );

    var reachabilitySnapper = new CarAccessibleVertexSnapper(100, 250);

    assertTrue(
      reachabilitySnapper.isCarAccessible(v[2]),
      "The mainland curb M1 is genuinely car-accessible"
    );
    assertFalse(
      reachabilitySnapper.isCarAccessible(v[0]),
      "The island must stay rejected: its only way out is the mode-blind temporary bridge"
    );
  }
}
