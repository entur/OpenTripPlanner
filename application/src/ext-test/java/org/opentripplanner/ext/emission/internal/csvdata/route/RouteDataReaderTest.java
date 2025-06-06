package org.opentripplanner.ext.emission.internal.csvdata.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.ext.emission.EmissionTestData;
import org.opentripplanner.graph_builder.issue.service.DefaultDataImportIssueStore;
import org.opentripplanner.transit.model.framework.FeedScopedId;

class RouteDataReaderTest implements EmissionTestData {

  // We explicit set the feed-id in this test, we do NOT use the feed-id in the teed-info.txt.
  // This way, we test that the feed-id can be overridden.
  private static final String FEED_ID = "F";
  private static final FeedScopedId ROUTE_D_1001 = new FeedScopedId(
    FEED_ID,
    ROUTE_ID_GD_1001.getId()
  );
  private static final FeedScopedId ROUTE_F_R1 = new FeedScopedId(FEED_ID, ROUTE_ID_EM_R1.getId());

  private final DefaultDataImportIssueStore issueStore = new DefaultDataImportIssueStore();
  private final RouteDataReader subject = new RouteDataReader(issueStore);

  @Test
  void testCo2EmissionsFromGtfsDataSource() throws FileNotFoundException {
    var emissions = subject.read(gtfsWithEmissionFile(), FEED_ID);
    assertEquals(0.006, emissions.get(ROUTE_D_1001).co2().asDouble(), 0.0001);
    assertEquals(3, emissions.size());
    var issues = issueStore.listIssues();

    var expected = List.of(
      "The double value '-0.001' for avg_co2_per_vehicle_per_km is outside expected range [0.0, 100000.0): '1004,-0.001,1' (@line:5)",
      "The double value '0.0' for avg_passenger_count is outside expected range [0.001, 10000.0): '1005,1,0' (@line:6)",
      "Value for 'avg_passenger_count' is missing: '1006,1,' (@line:7)",
      "Value for 'avg_co2_per_vehicle_per_km' is missing: '1007,,1' (@line:8)",
      "Value for 'route_id' is missing: ',1,1' (@line:9)"
    );
    for (int i = 0; i < expected.size(); i++) {
      assertEquals(expected.get(i), issues.get(i).getMessage());
    }
    assertEquals(expected.size(), issues.size());
  }

  @Test
  void testCo2EmissionsFromFeedDataSource() throws FileNotFoundException {
    var emissions = subject.read(emissionOnRoutes(), FEED_ID);
    assertEquals(0.006, emissions.get(ROUTE_F_R1).co2().asDouble(), 0.0001);
    assertTrue(issueStore.listIssues().isEmpty(), () -> issueStore.toString());
    assertEquals(2, emissions.size());
  }

  @Test
  void handleMissingDdataSource() {
    var emissions = subject.read(emissionMissingFile(), FEED_ID);
    assertTrue(emissions.isEmpty());
  }

  @Test
  void ignoreDataSourceIfHeadersDoesNotMatch() {
    var emissions = subject.read(emissionOnTripLegs(), FEED_ID);
    assertTrue(emissions.isEmpty());
  }
}
