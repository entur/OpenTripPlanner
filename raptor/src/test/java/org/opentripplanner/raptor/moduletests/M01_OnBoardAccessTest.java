package org.opentripplanner.raptor.moduletests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.raptor._data.RaptorTestConstants.STOP_B;
import static org.opentripplanner.raptor._data.RaptorTestConstants.T00_00;
import static org.opentripplanner.raptor._data.RaptorTestConstants.T01_00;
import static org.opentripplanner.raptor._data.api.PathUtils.pathsToString;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
      .earliestDepartureTime(T00_00) // What should this be?
      .latestArrivalTime(T01_00)
      .searchWindow(Duration.ofMinutes(2))
      .timetable(true);

    return builder;
  }

  @Test
  @DisplayName("On-board access with two routes boards the correct route")
  void onBoardAccess() {
    data
      .access(new TestRaptorOnBoardAccess(STOP_B, 0, 0))
      .withRoutes()
      .withTimetables(
      """
      -- R1
      A     B     C     D
      0:00  0:05  0:10  0:20
      0:00  0:10  0:15  0:25
      -- R2
      A     B           D
      0:00  0:05        0:15
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
      .access(new TestRaptorOnBoardAccess(STOP_B, 0, 0))
      .withRoutes()
      .withTimetables(
        """
        -- R1
        A     B     C     D
        0:00  0:05  0:10  0:20
        0:00  0:10  0:15  0:25
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
}
