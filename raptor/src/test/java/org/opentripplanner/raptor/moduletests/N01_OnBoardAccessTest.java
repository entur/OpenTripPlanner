package org.opentripplanner.raptor.moduletests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opentripplanner.raptor._data.RaptorTestConstants.STOP_A;
import static org.opentripplanner.raptor._data.RaptorTestConstants.STOP_B;
import static org.opentripplanner.raptor._data.RaptorTestConstants.STOP_C;
import static org.opentripplanner.raptor._data.RaptorTestConstants.T00_00;
import static org.opentripplanner.raptor._data.RaptorTestConstants.T01_00;
import static org.opentripplanner.raptor._data.api.PathUtils.pathsToString;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentripplanner.raptor.RaptorService;
import org.opentripplanner.raptor._data.transit.TestAccessEgress;
import org.opentripplanner.raptor._data.transit.TestRaptorOnBoardAccess;
import org.opentripplanner.raptor._data.transit.TestTransitData;
import org.opentripplanner.raptor._data.transit.TestTripSchedule;
import org.opentripplanner.raptor.api.request.RaptorProfile;
import org.opentripplanner.raptor.api.request.RaptorRequestBuilder;
import org.opentripplanner.raptor.configure.RaptorTestFactory;

/**
 * FEATURE UNDER TEST
 * Raptor should handle access from on-board a transit vehicle. When given such access as input,
 * resulting paths should include a boarding at the stop given in the access, but only for the
 * specific route and trip. Apart from boarding cost, the cost of the access leg should be
 * considered 'free'.
 */
class N01_OnBoardAccessTest {

  private final TestTransitData data = new TestTransitData();

  private final RaptorService<TestTripSchedule> raptorService = RaptorTestFactory.raptorService();

  private RaptorRequestBuilder<TestTripSchedule> prepareRequest() {
    var builder = data.requestBuilder();

    builder.profile(RaptorProfile.MULTI_CRITERIA);

    builder
      .searchParams()
      .earliestDepartureTime(T00_00)
      .latestArrivalTime(T01_00)
      .searchWindow(Duration.ofMinutes(60))
      .timetable(true);

    return builder;
  }

  @Test
  @DisplayName("On-board access with two routes boards the correct route")
  void onBoardAccess() {
    data
      .withRoutes()
      .withTimetables(
        """
        -- R1
        A     B     C     D
        0:00  0:05  0:10  0:20
        -- R2
        A     B           D
        0:00  0:06        0:15
        """
      );
    var trip = data.getRoute(0).getTripSchedule(0);
    data.access(new TestRaptorOnBoardAccess(trip, 1, STOP_B, 0)).egress("D ~ Walk 30s");

    var requestBuilder = prepareRequest();

    var raptorResponse = raptorService.route(requestBuilder.build(), data);

    assertEquals(
      "B ~ BUS R1 0:05 0:20 ~ D ~ Walk 30s [0:05 0:20:30 15m30s Tₙ0 C₁1_560]",
      pathsToString(raptorResponse)
    );
  }

  @Test
  @DisplayName(
    "On-board access with two routes boards the correct route, then transfers at the first valid stop"
  )
  void transfer() {
    data
      .withRoutes()
      .withTimetables(
        """
        -- R1
        A     B     C     D
        0:00  0:05  0:10  0:20
        -- R2
        A     B     C     D
        0:00  0:06  0:12  0:15
        """
      );

    var trip = data.getRoute(0).getTripSchedule(0);
    data.access(new TestRaptorOnBoardAccess(trip, 1, STOP_B, 0)).egress("D ~ Walk 30s");

    var requestBuilder = prepareRequest();

    var raptorResponse = raptorService.route(requestBuilder.build(), data);

    assertEquals(
      """
      B ~ BUS R1 0:05 0:10 ~ C ~ BUS R2 0:12 0:15 ~ D ~ Walk 30s [0:05 0:15:30 10m30s Tₙ1 C₁1_860]
      B ~ BUS R1 0:05 0:20 ~ D ~ Walk 30s [0:05 0:20:30 15m30s Tₙ0 C₁1_560]""",
      pathsToString(raptorResponse)
    );
  }

  @Test
  @DisplayName("On-board access does not allow invalid boardings or transfers")
  void noInvalidTransfers() {
    data
      .withRoutes()
      .withTimetables(
        """
        -- R1
        A     B     C
        0:00  0:05  0:10
        -- R2
                    C      D
                    0:12   0:20
        -- R3
        A     B     C     D
        0:00  0:06  0:09  0:15
        """
      );

    var trip = data.getRoute(0).getTripSchedule(0);
    data.access(new TestRaptorOnBoardAccess(trip, 1, STOP_B, 0)).egress("D ~ Walk 30s");

    var requestBuilder = prepareRequest();

    var raptorResponse = raptorService.route(requestBuilder.build(), data);

    assertEquals(
      "B ~ BUS R1 0:05 0:10 ~ C ~ BUS R2 0:12 0:20 ~ D ~ Walk 30s [0:05 0:20:30 15m30s Tₙ1 C₁2_160]",
      pathsToString(raptorResponse)
    );
  }

  @Test
  @DisplayName("On-board access on a ring-line starts from the provided stop position")
  void ringLineBoardsCorrectStopPosition() {
    data
      .withRoutes()
      .withTimetables(
        """
        -- R1
        A     B     C     D     A     B     C     D
        0:00  0:05  0:10  0:20  0:30  0:35  0:40  0:50
        """
      );
    var trip = data.getRoute(0).getTripSchedule(0);
    data.access(new TestRaptorOnBoardAccess(trip, 5, STOP_B, 0)).egress("D ~ Walk 30s");

    var requestBuilder = prepareRequest();

    var raptorResponse = raptorService.route(requestBuilder.build(), data);

    assertEquals(
      "B ~ BUS R1 0:35 0:50 ~ D ~ Walk 30s [0:35 0:50:30 15m30s Tₙ0 C₁1_560]",
      pathsToString(raptorResponse)
    );
  }

  @Test
  @DisplayName(
    "On-board access with a route with several trips boards the correct trip given by the trip index"
  )
  void correctTrip() {
    data
      .withRoutes()
      .withTimetables(
        """
        -- R1
        A     B     C     D
        0:00  0:02  0:05  0:10
        0:00  0:05  0:10  0:20
        0:00  0:10  0:15  0:25
        """
      );
    var trip = data.getRoute(0).getTripSchedule(1);
    data.access(new TestRaptorOnBoardAccess(trip, 1, STOP_B, 0)).egress("D ~ Walk 30s");

    var requestBuilder = prepareRequest();

    var raptorResponse = raptorService.route(requestBuilder.build(), data);

    // Since the access has a boarding time of 0:05 at B, we select the second trip in the pattern
    assertEquals(
      """
      B ~ BUS R1 0:05 0:20 ~ D ~ Walk 30s [0:05 0:20:30 15m30s Tₙ0 C₁1_560]""",
      pathsToString(raptorResponse)
    );
  }

  @Test
  @DisplayName("On-board access to a non-existing route results in exception")
  void nonExistentRoute() {
    data.withTimetables(
      """
      -- R1
      A     B     C     D
      0:00  0:02  0:05  0:10
      0:00  0:05  0:10  0:20
      0:00  0:10  0:15  0:25
      """
    );
    var trip = data.getRoute(1).getTripSchedule(1);
    data.access(new TestRaptorOnBoardAccess(trip, 1, STOP_B, 0)).egress("D ~ Walk 30s");

    var requestBuilder = prepareRequest();

    assertThrows(IndexOutOfBoundsException.class, () ->
      raptorService.route(requestBuilder.build(), data)
    );
  }

  @Test
  @DisplayName("On-board access to a non-existing trip in route results in exception")
  void nonExistentTrip() {
    data.withTimetables(
      """
      -- R1
      A     B     D
      0:00  0:05  0:10
      0:00  0:10  0:20
      0:00  0:15  0:25
      """
    );
    var trip = data.getRoute(0).getTripSchedule(3);
    data.access(new TestRaptorOnBoardAccess(trip, 1, STOP_B, 0)).egress("D ~ Walk 30s");

    var requestBuilder = prepareRequest();

    assertThrows(IndexOutOfBoundsException.class, () ->
      raptorService.route(requestBuilder.build(), data)
    );
  }

  @Test
  @DisplayName("Multiple on-board accesses yields a pareto set of non-dominated paths")
  void multipleAccesses() {
    data.withTimetables(
      """
      -- R1
      A     B     C     D
      0:00  0:05  0:10  0:15
      0:05  0:10  0:15  0:20
      -- R2
      A     B
      0:02  0:04
      """
    );
    var trip01 = data.getRoute(0).getTripSchedule(1);
    var trip10 = data.getRoute(1).getTripSchedule(0);

    data
      .access(
        // Dominated by trip 1 @ C
        new TestRaptorOnBoardAccess(trip01, 1, STOP_B, 0),
        // Pareto-optimal
        new TestRaptorOnBoardAccess(trip01, 2, STOP_C, 0),
        // Pareto-optimal
        new TestRaptorOnBoardAccess(trip10, 0, STOP_A, 0)
      )
      .egress("D ~ Walk 30s");

    var requestBuilder = prepareRequest();

    var raptorResponse = raptorService.route(requestBuilder.build(), data);

    // Only accesses that yield a non-dominated path are included in the result
    assertEquals(
      """
      A ~ BUS R2 0:02 0:04 ~ B ~ BUS R1 0:05 0:15 ~ D ~ Walk 30s [0:02 0:15:30 13m30s Tₙ1 C₁2_040]
      C ~ BUS R1 0:15 0:20 ~ D ~ Walk 30s [0:15 0:20:30 5m30s Tₙ0 C₁960]""",
      pathsToString(raptorResponse)
    );
  }

  @Test
  @DisplayName("Mixing walk and on-board accesses yields a pareto set of non-dominated paths")
  void walkAndOnBoard() {
    data
      .withRoutes()
      .withTimetables(
        """
        -- R1
        A     B     C     D
        0:00  0:05  0:10  0:15
        -- R2
                    E     D
                    0:10  0:12
        """
      );

    var trip = data.getRoute(0).getTripSchedule(0);
    data
      .access(
        // Walk to E to catch a trip that arrives earlier
        TestAccessEgress.of("Walk 5m ~ E"),
        // Or stay on board
        new TestRaptorOnBoardAccess(trip, 1, STOP_B, 0)
      )
      .egress("D ~ Walk 30s");

    var requestBuilder = prepareRequest();

    var raptorResponse = raptorService.route(requestBuilder.build(), data);

    // Only accesses that yield a non-dominated path are included in the result
    assertEquals(
      """
      Walk 5m ~ E ~ BUS R2 0:10 0:12 ~ D ~ Walk 30s [0:05 0:12:30 7m30s Tₙ0 C₁1_380]
      B ~ BUS R1 0:05 0:15 ~ D ~ Walk 30s [0:05 0:15:30 10m30s Tₙ0 C₁1_260]""",
      pathsToString(raptorResponse)
    );
  }

  @Test
  @DisplayName("Range query results in multiple consecutive paths")
  void rangeQuery() {
    data
      .withRoutes()
      .withTimetables(
        """
        -- R1
        A     B        C     D
        0:00  0:05:05  0:10  0:15
        0:05  0:10     0:15  0:20
        """
      );
    var trip0 = data.getRoute(0).getTripSchedule(0);
    var trip1 = data.getRoute(0).getTripSchedule(1);
    data
      .access(
        new TestRaptorOnBoardAccess(trip0, 1, STOP_B, 0),
        new TestRaptorOnBoardAccess(trip1, 1, STOP_B, 0)
      )
      .egress("D ~ Walk 30s");

    var requestBuilder = prepareRequest();

    var raptorResponse = raptorService.route(requestBuilder.build(), data);

    // The result of the range query should include a path for each access
    assertEquals(
      """
      B ~ BUS R1 0:05:05 0:15 ~ D ~ Walk 30s [0:05:05 0:15:30 10m25s Tₙ0 C₁1_255]
      B ~ BUS R1 0:10 0:20 ~ D ~ Walk 30s [0:10 0:20:30 10m30s Tₙ0 C₁1_260]""",
      pathsToString(raptorResponse)
    );
  }
}
