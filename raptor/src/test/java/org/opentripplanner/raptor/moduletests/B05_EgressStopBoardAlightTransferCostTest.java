package org.opentripplanner.raptor.moduletests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.raptor.moduletests.support.RaptorModuleTestConfig.multiCriteria;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.raptor.RaptorService;
import org.opentripplanner.raptor._data.RaptorTestConstants;
import org.opentripplanner.raptor._data.transit.TestAccessEgress;
import org.opentripplanner.raptor._data.transit.TestTransitData;
import org.opentripplanner.raptor._data.transit.TestTripSchedule;
import org.opentripplanner.raptor.api.request.RaptorRequestBuilder;
import org.opentripplanner.raptor.configure.RaptorConfig;
import org.opentripplanner.raptor.moduletests.support.ModuleTestDebugLogging;
import org.opentripplanner.raptor.moduletests.support.RaptorModuleTestCase;

/**
 * FEATURE UNDER TEST
 * <p>
 * This verifies that the stopBoardAlightTransferCost is not applied for egress legs. If this is not
 * correctly handled by the heuristics optimization, the cheapest journey could be discarded.
 */
public class B05_EgressStopBoardAlightTransferCostTest implements RaptorTestConstants {

  private final TestTransitData data = new TestTransitData();
  private final RaptorRequestBuilder<TestTripSchedule> requestBuilder = data.requestBuilder();
  private final RaptorService<TestTripSchedule> raptorService = new RaptorService<>(
    RaptorConfig.defaultConfigForTest()
  );

  @BeforeEach
  void setup() {
    data.withTimetables(
      """
      B     C
      0:10  0:14
      --
      C     D
      0:18  0:20
      """
    );

    data.withTransferCost(0).withBoardCost(0);
    data.withStopBoardAlightTransferCost(STOP_D, 60000);

    requestBuilder
      .searchParams()
      .addAccessPaths(TestAccessEgress.free(STOP_B))
      .addEgressPaths(
        // This will be the fastest
        TestAccessEgress.walk(STOP_C, D5m),
        // This will be the cheapest
        TestAccessEgress.walk(STOP_D, D20s)
      )
      .earliestDepartureTime(T00_00)
      .latestArrivalTime(T00_30);

    ModuleTestDebugLogging.setupDebugLogging(data);
  }

  static List<RaptorModuleTestCase> testCases() {
    return RaptorModuleTestCase.of()
      .add(
        multiCriteria(),
        // We should get both the fastest and the c1-cheapest results
        // The stopBoardAlightTransferCost should not be applied to the egress leg from STOP_D
        "B ~ BUS R1 0:10 0:14 ~ C ~ Walk 5m [0:10 0:19 9m Tₙ0 C₁840]",
        "B ~ BUS R1 0:10 0:14 ~ C ~ BUS R2 0:18 0:20 ~ D ~ Walk 20s [0:10 0:20:20 10m20s Tₙ1 C₁640]"
      )
      .build();
  }

  @ParameterizedTest
  @MethodSource("testCases")
  void testRaptor(RaptorModuleTestCase testCase) {
    assertEquals(testCase.expected(), testCase.run(raptorService, data, requestBuilder));
  }
}
