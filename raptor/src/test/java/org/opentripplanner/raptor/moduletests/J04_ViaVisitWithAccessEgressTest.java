package org.opentripplanner.raptor.moduletests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.raptor._data.RaptorTestConstants.D1m;
import static org.opentripplanner.raptor._data.RaptorTestConstants.D2m;
import static org.opentripplanner.raptor._data.RaptorTestConstants.D30s;
import static org.opentripplanner.raptor._data.RaptorTestConstants.D5m;
import static org.opentripplanner.raptor._data.RaptorTestConstants.STOP_A;
import static org.opentripplanner.raptor._data.RaptorTestConstants.STOP_B;
import static org.opentripplanner.raptor._data.RaptorTestConstants.STOP_C;
import static org.opentripplanner.raptor._data.RaptorTestConstants.STOP_D;
import static org.opentripplanner.raptor._data.RaptorTestConstants.STOP_E;
import static org.opentripplanner.raptor._data.RaptorTestConstants.T00_00;
import static org.opentripplanner.raptor._data.RaptorTestConstants.T01_00;
import static org.opentripplanner.raptor._data.api.PathUtils.pathsToString;
import static org.opentripplanner.raptor._data.transit.TestAccessEgress.walk;
import static org.opentripplanner.raptor._data.transit.TestRoute.route;
import static org.opentripplanner.raptor._data.transit.TestTripSchedule.schedule;
import static org.opentripplanner.raptor.api.request.RaptorViaLocation.via;

import java.time.Duration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentripplanner.raptor.RaptorService;
import org.opentripplanner.raptor._data.transit.TestTransitData;
import org.opentripplanner.raptor._data.transit.TestTripSchedule;
import org.opentripplanner.raptor.api.request.RaptorProfile;
import org.opentripplanner.raptor.api.request.RaptorRequestBuilder;
import org.opentripplanner.raptor.configure.RaptorConfig;

/**
 * FEATURE UNDER TEST
 * <p>
 * Raptor should support via-visit searches where access or egress paths visit via locations.
 * <p>
 * This allows for scenarios like:
 * <ul>
 *   <li>Walking through multiple via locations before boarding transit</li>
 *   <li>Transferring through via locations in the egress path</li>
 *   <li>Combining access, transit, and egress to visit all required via locations</li>
 * </ul>
 */
@Disabled
class J04_ViaVisitWithAccessEgressTest {

  private final RaptorService<TestTripSchedule> raptorService = new RaptorService<>(
    RaptorConfig.defaultConfigForTest()
  );

  private RaptorRequestBuilder<TestTripSchedule> prepareRequest() {
    var builder = new RaptorRequestBuilder<TestTripSchedule>();

    builder
      .profile(RaptorProfile.MULTI_CRITERIA)
      // TODO: Currently heuristics does not work with via-visit so we turn them off
      .clearOptimizations();

    builder
      .searchParams()
      .earliestDepartureTime(T00_00)
      .latestArrivalTime(T01_00)
      .searchWindow(Duration.ofMinutes(10))
      .timetable(true);

    return builder;
  }

  @Test
  @DisplayName(
    "Access visits first via location (A), then second via location C is visited using transit"
  )
  void accessVisitsFirstViaLocation() {
    var data = new TestTransitData()
      .withRoutes(
        route("R1").timetable(
          """
           B    C    D
          0:05 0:15 0:25
          0:15 0:25 0:35
          """
        )
      );

    var requestBuilder = prepareRequest();

    requestBuilder
      .searchParams()
      // Access visits 1 via location(A), there is no check inside Raptor that location A is visited
      .addAccessPaths(walk(STOP_B, D5m).withViaLocationsVisited(1), walk(STOP_B, D1m))
      // Define two via locations - A is visited by the access and the other is visited with Raptor,
      // but both location must be pressent in the request
      .addViaLocation(via("Via-A").addViaStop(STOP_A).build())
      .addViaLocation(via("Via-C").addViaStop(STOP_C).build())
      // Egress from STOP_C
      .addEgressPaths(walk(STOP_C, D30s));

    var result = raptorService.route(requestBuilder.build(), data);

    assertEquals(
      "Walk 5m ~ B ~ BUS R1 0:05 0:15 ~ C ~ BUS R1 0:25 0:35 ~ D ~ Walk 30s [0:04 0:35:30 31m30s Tₓ1 C₁3_180]",
      pathsToString(result)
    );
  }

  @Test
  @DisplayName("Egress visits last via location - leg chain should skip last via location")
  void egressVisitsLastViaLocation() {
    var data = new TestTransitData()
      .withRoutes(
        route("R1").timetable(
          """
           A    B    C
          0:05 0:15 0:25
          0:15 0:25 0:35
          """
        ),
        // This route is just to make sure stop E is part of the Raptor state, it is not rechable
        // in the search
        route("R2", STOP_E, STOP_E).withTimetable("12:00 13:00")
      );

    var requestBuilder = prepareRequest();

    requestBuilder
      .searchParams()
      .addAccessPaths(walk(STOP_A, D30s))
      .addViaLocation(via("Via-B").addViaStop(STOP_B).build())
      .addViaLocation(via("Via-E").addViaStop(STOP_E).build())
      .addEgressPaths(walk(STOP_C, D1m).withViaLocationsVisited(1));

    var result = raptorService.route(requestBuilder.build(), data);

    assertEquals(
      "Walk 30s ~ A ~ BUS R1 0:05 0:15 ~ B ~ BUS R1 0:15 0:25 ~ C ~ Walk 1m " +
      "[0:04:30 0:26:00 21m30s Tₓ1 C₁2_580]",
      pathsToString(result)
    );
  }

  @Test
  @DisplayName("Access visits first, egress visits last - middle via location via transit")
  void accessAndEgressBothVisitViaLocations() {
    var data = new TestTransitData();

    // Setup: Origin -> ViaA (via access) -> ViaB (via transit) -> ViaC (via egress) -> Destination
    data.withRoutes(
      route("R1", STOP_A, STOP_B, STOP_C, STOP_D).withTimetable(
        schedule("0:05 0:15 0:25 0:35"),
        schedule("0:15 0:25 0:35 0:45")
      )
    );

    var requestBuilder = prepareRequest();

    requestBuilder
      .searchParams()
      // Access walks to STOP_A (ViaA), visiting 1 via location
      .addAccessPaths(walk(STOP_A, D1m).withViaLocationsVisited(1))
      // Define three via locations
      .addViaLocation(via("ViaA").addViaStop(STOP_A).build())
      .addViaLocation(via("ViaB").addViaStop(STOP_B).build())
      .addViaLocation(via("ViaC").addViaStop(STOP_C).build())
      // Egress from STOP_C to destination, visiting 1 via location (ViaC)
      .addEgressPaths(walk(STOP_C, D1m).withViaLocationsVisited(1));

    var result = raptorService.route(requestBuilder.build(), data);

    // Expected: Access visits ViaA, transit visits ViaB (and passes through ViaC), egress visits ViaC
    // The leg chain should only require visiting ViaB in the middle
    assertEquals(
      "Walk 1m ~ A ~ BUS R1 0:05 0:15 ~ B ~ BUS R1 0:15 0:25 ~ C ~ Walk 1m " +
      "[0:04:00 0:26:00 22m Tₓ1 C₁2_640]",
      pathsToString(result)
    );
  }

  @Test
  @DisplayName("Access and egress visit all via locations - no transit legs needed")
  void accessAndEgressVisitAllViaLocations() {
    var data = new TestTransitData();

    // Setup: Even though transit exists, access and egress cover all via locations
    data.withRoutes(route("R1", STOP_A, STOP_B, STOP_C).withTimetable(schedule("0:05 0:15 0:25")));

    var requestBuilder = prepareRequest();

    requestBuilder
      .searchParams()
      // Access walks through both via locations
      .addAccessPaths(walk(STOP_B, D2m).withViaLocationsVisited(2))
      // Define two via locations
      .addViaLocation(via("ViaA").addViaStop(STOP_A).build())
      .addViaLocation(via("ViaB").addViaStop(STOP_B).build())
      // Egress from STOP_B
      .addEgressPaths(walk(STOP_C, D30s));

    var result = raptorService.route(requestBuilder.build(), data);

    // Expected: Since access visits both via locations, no transit is needed
    // The path should be just: Walk to B (visiting ViaA and ViaB) -> Walk to C
    // NOTE: This test may not find a path if the simplified approach doesn't handle this edge case
    assertEquals("Walk 2m ~ B ~ Walk 30s [0:00:00 0:02:30 2m30s Tₓ0 C₁300]", pathsToString(result));
  }

  @Test
  @DisplayName("Multiple access paths with different via visits - all should be considered")
  void multipleAccessPathsWithDifferentViaVisits() {
    var data = new TestTransitData();

    // Setup: Two access paths - one direct, one via ViaA
    data.withRoutes(
      route("R1", STOP_A, STOP_B, STOP_C).withTimetable(
        schedule("0:05 0:15 0:25"),
        schedule("0:15 0:25 0:35")
      ),
      route("R2", STOP_D, STOP_B, STOP_C).withTimetable(schedule("0:03 0:18 0:28"))
    );

    var requestBuilder = prepareRequest();

    requestBuilder
      .searchParams()
      // Access 1: Direct to STOP_D, visits 0 via locations
      .addAccessPaths(walk(STOP_D, D30s))
      // Access 2: To STOP_A (ViaA), visits 1 via location
      .addAccessPaths(walk(STOP_A, D1m).withViaLocationsVisited(1))
      // Define two via locations
      .addViaLocation(via("ViaA").addViaStop(STOP_A).build())
      .addViaLocation(via("ViaB").addViaStop(STOP_B).build())
      // Egress from STOP_C
      .addEgressPaths(walk(STOP_C, D30s));

    var result = raptorService.route(requestBuilder.build(), data);

    // Expected: Should find paths using both access options
    // Path 1: Access to D, transit D->B (ViaA not visited, fails via requirement)
    // Path 2: Access to A (visiting ViaA), transit A->B (visiting ViaB), egress to C
    // Only Path 2 should be valid since it visits all via locations
    assertEquals(
      "Walk 1m ~ A ~ BUS R1 0:05 0:15 ~ B ~ BUS R1 0:15 0:25 ~ C ~ Walk 30s " +
      "[0:04:00 0:25:30 21m30s Tₓ1 C₁2_580]",
      pathsToString(result)
    );
  }

  @Test
  @DisplayName("Access visits via location, but also regular access without via visits exists")
  void mixedAccessPathsSomeWithViaVisits() {
    var data = new TestTransitData();

    // Setup: Routes from both stops
    data.withRoutes(
      route("R1", STOP_A, STOP_B, STOP_C).withTimetable(schedule("0:05 0:15 0:25")),
      route("R2", STOP_D, STOP_B, STOP_C).withTimetable(schedule("0:02 0:17 0:27"))
    );

    var requestBuilder = prepareRequest();

    requestBuilder
      .searchParams()
      // Access 1: To STOP_A (ViaA), visits 1 via location
      .addAccessPaths(walk(STOP_A, D30s).withViaLocationsVisited(1))
      // Access 2: To STOP_D (not a via location), visits 0 via locations
      .addAccessPaths(walk(STOP_D, D30s))
      // Define two via locations
      .addViaLocation(via("ViaA").addViaStop(STOP_A).build())
      .addViaLocation(via("ViaB").addViaStop(STOP_B).build())
      // Egress from STOP_C
      .addEgressPaths(walk(STOP_C, D30s));

    var result = raptorService.route(requestBuilder.build(), data);

    // Expected: Both paths should be valid
    // Path 1: Access to A (visiting ViaA), need to visit ViaB via transit
    // Path 2: Access to D (not visiting ViaA), need to visit both ViaA and ViaB via transit
    //         This path is invalid since R2 doesn't go through ViaA
    // So only Path 1 should be in the result
    assertEquals(
      "Walk 30s ~ A ~ BUS R1 0:05 0:15 ~ B ~ BUS R1 0:15 0:25 ~ C ~ Walk 30s " +
      "[0:04:30 0:25:30 21m Tₓ1 C₁2_520]",
      pathsToString(result)
    );
  }

  // Helper method to create via location
  private static org.opentripplanner.raptor.api.request.RaptorViaLocation viaLocation(
    String label,
    int... stops
  ) {
    var builder = via(label);
    for (int stop : stops) {
      builder.addViaStop(stop);
    }
    return builder.build();
  }
}
