package org.opentripplanner.raptor.moduletests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.raptor._data.RaptorTestConstants.STOP_A;
import static org.opentripplanner.raptor._data.RaptorTestConstants.STOP_B;
import static org.opentripplanner.raptor._data.RaptorTestConstants.STOP_C;
import static org.opentripplanner.raptor._data.RaptorTestConstants.T00_00;
import static org.opentripplanner.raptor._data.RaptorTestConstants.T01_00;
import static org.opentripplanner.raptor._data.api.PathUtils.pathsToString;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opentripplanner.raptor.RaptorService;
import org.opentripplanner.raptor._data.transit.TestTransitData;
import org.opentripplanner.raptor._data.transit.TestTripSchedule;
import org.opentripplanner.raptor._data.transit.TestRaptorOnBoardAccess;
import org.opentripplanner.raptor.api.request.RaptorProfile;
import org.opentripplanner.raptor.api.request.RaptorRequestBuilder;
import org.opentripplanner.raptor.configure.RaptorTestFactory;

/**
 * FEATURE UNDER TEST
 *
 * Raptor should handle access from on-board a transit vehicle.
 */
class M01_OnBoardAccessTest {
  private final TestTransitData data = new TestTransitData();

  private final RaptorService<TestTripSchedule> raptorService = RaptorTestFactory.raptorService();

  private RaptorRequestBuilder<TestTripSchedule> prepareRequest() {
    var builder = data.requestBuilder();

    builder
      .profile(RaptorProfile.MULTI_CRITERIA);

    builder
      .searchParams()
      .earliestDepartureTime(T00_00)
      .latestArrivalTime(T01_00)
      .searchWindow(Duration.ofMinutes(2))
      .timetable(true);

    return builder;
  }

  @Test
  @DisplayName("On-board access with two routes boards the correct route")
  void onBoardAccess() {
    data
      .access(new TestRaptorOnBoardAccess(STOP_B, 5 * 60, 0, 0))
      .withRoutes()
      .withTimetables(
      """
      -- R1
      A     B     C     D
      0:00  0:05  0:10  0:20
      -- R2
      A     B           D
      0:00  0:06        0:15
      """)
      .egress("D ~ Walk 30s");

    var requestBuilder = prepareRequest();

    var raptorResponse = raptorService.route(requestBuilder.build(), data);

    assertEquals(
      "B ~ BUS R1 0:05 0:20 ~ D ~ Walk 30s [0:05 0:20:30 15m30s Tₙ0 C₁1_560]",
      pathsToString(raptorResponse)
    );
  }

  @Test
  @DisplayName("On-board access with two routes boards the correct route, then transfers at the first valid stop")
  void transfer() {
    data
      .access(new TestRaptorOnBoardAccess(STOP_B, 5 * 60, 0, 0))
      .withRoutes()
      .withTimetables(
        """
        -- R1
        A     B     C     D
        0:00  0:05  0:10  0:20
        -- R2
        A     B     C     D
        0:00  0:06  0:12  0:15
        """)
      .egress("D ~ Walk 30s");

    var requestBuilder = prepareRequest();

    var raptorResponse = raptorService.route(requestBuilder.build(), data);

    assertEquals(
      """
      B ~ BUS R1 0:05 0:10 ~ C ~ BUS R2 0:12 0:15 ~ D ~ Walk 30s [0:05 0:15:30 10m30s Tₙ1 C₁1_860]
      B ~ BUS R1 0:05 0:20 ~ D ~ Walk 30s [0:05 0:20:30 15m30s Tₙ0 C₁1_560]""",
      pathsToString(raptorResponse)
    );
  }

  @ParameterizedTest(name = "Boarding at {0} minutes")
  @ValueSource(ints = { 4, 5 })
  @DisplayName("On-board access with a route with several trips boards the correct trip given by the first possible timestamp")
  void correctTrip(int departureTimeMinutes) {
    data
      .access(new TestRaptorOnBoardAccess(STOP_B, departureTimeMinutes * 60, 0, 0))
      .withRoutes()
      .withTimetables(
        """
        -- R1
        A     B     C     D
        0:00  0:02  0:05  0:10
        0:00  0:05  0:10  0:20
        0:00  0:10  0:15  0:25
        """)
      .egress("D ~ Walk 30s");

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
  @DisplayName("On-board access to a non-existing route")
  void nonExistentRoute() {
    data
      .access(new TestRaptorOnBoardAccess(STOP_B, 5 * 60, 1, 0))
      .withTimetables(
        """
        -- R1
        A     B     C     D
        0:00  0:02  0:05  0:10
        0:00  0:05  0:10  0:20
        0:00  0:10  0:15  0:25
        """)
      .egress("D ~ Walk 30s");

    var requestBuilder = prepareRequest();

    var raptorResponse = raptorService.route(requestBuilder.build(), data);

    // Since we're trying to board a route with routeIndex 1, but the only existing route pattern
    // has routeIndex 0, the result contains no paths
    assertEquals("", pathsToString(raptorResponse));
  }

  @Test
  @DisplayName("On-board access to a non-existing trip in route")
  void nonExistentTrip() {
    data
      .access(new TestRaptorOnBoardAccess(STOP_B, 16 * 60, 0, 0))
      .withTimetables(
        """
        -- R1
        A     B     D
        0:00  0:05  0:10
        0:00  0:10  0:20
        0:00  0:15  0:25
        """)
      .egress("D ~ Walk 30s");

    var requestBuilder = prepareRequest();

    var raptorResponse = raptorService.route(requestBuilder.build(), data);

    // Since we try to do on-board access starting from B at 0:16, but the latest trip passes B at
    // 0:15, the result contains no paths
    assertEquals("", pathsToString(raptorResponse));
  }

  @Test
  @DisplayName("Multiple on-board accesses")
  void multipleAccesses() {
    data
      .access(
        new TestRaptorOnBoardAccess(STOP_B, 10 * 60, 0, 0), // Dominated by C@0:15
        new TestRaptorOnBoardAccess(STOP_C, 15 * 60, 0, 0),
        new TestRaptorOnBoardAccess(STOP_A, 2 * 60, 1, 0)
      )
      .withRoutes()
      .withTimetables(
        """
        -- R1
        A     B     C     D
        0:00  0:05  0:10  0:15
        0:05  0:10  0:15  0:20
        -- R2
        A     B
        0:02  0:04
        """)
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
}
