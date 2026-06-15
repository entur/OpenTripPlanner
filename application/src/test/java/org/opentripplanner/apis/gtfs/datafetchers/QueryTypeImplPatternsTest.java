package org.opentripplanner.apis.gtfs.datafetchers;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Iterables;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentripplanner.apis.support.graphql.DataFetchingSupport;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TripInput;

class QueryTypeImplPatternsTest {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2023, 6, 3);

  private final QueryTypeImpl subject = new QueryTypeImpl();

  @Test
  void allPatternsReturnedWithoutIdsFilter() throws Exception {
    var env = buildEnvironment();
    var transitService = env.transitService();

    var result = subject
      .patterns()
      .get(DataFetchingSupport.dataFetchingEnvironment(null, Map.of(), transitService));

    assertThat(result).hasSize(transitService.listTripPatterns().size());
  }

  @Test
  void onlyMatchingPatternsReturnedWithIdsFilter() throws Exception {
    var env = buildEnvironment();
    var transitService = env.transitService();
    var pattern = transitService.listTripPatterns().iterator().next();

    var result = subject
      .patterns()
      .get(
        DataFetchingSupport.dataFetchingEnvironment(
          null,
          Map.of("ids", List.of(pattern.getId().toString())),
          transitService
        )
      );

    assertThat(result).hasSize(1);
    assertThat(Iterables.getOnlyElement(result)).isEqualTo(pattern);
  }

  @Test
  void unknownAndNullIdsAreIgnored() throws Exception {
    var env = buildEnvironment();
    var transitService = env.transitService();

    var ids = new java.util.ArrayList<String>();
    ids.add(null);
    ids.add("test:does-not-exist");

    var result = subject
      .patterns()
      .get(DataFetchingSupport.dataFetchingEnvironment(null, Map.of("ids", ids), transitService));

    assertThat(result).isEmpty();
  }

  private static TransitTestEnvironment buildEnvironment() {
    var builder = TransitTestEnvironment.of(SERVICE_DATE);
    var tripInput = TripInput.of("Trip1")
      .addStop(builder.stop("A"), "12:00:00")
      .addStop(builder.stop("B"), "12:30:00");
    return builder.addTrip(tripInput).build();
  }
}
