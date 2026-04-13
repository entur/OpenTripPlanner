package org.opentripplanner.standalone.configure.warmup;

import graphql.ExecutionInput;
import graphql.GraphQL;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.opentripplanner.apis.gtfs.GraphQLRequestContext;
import org.opentripplanner.apis.support.graphql.OtpDataFetcherExceptionHandler;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GtfsWarmupQueryExecutor implements WarmupQueryExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(GtfsWarmupQueryExecutor.class);

  // Access and egress modes paired so each combination is valid per the GTFS schema.
  // BICYCLE is excluded because it requires matching transfer mode.
  // CAR_PARKING is only valid as an access mode, not as an egress mode.
  private static final List<String> ACCESS_MODES = List.of("WALK", "CAR_PARKING");
  private static final List<String> EGRESS_MODES = List.of("WALK", "WALK");

  static final String QUERY = """
    query(
      $fromLat: CoordinateValue!, $fromLon: CoordinateValue!,
      $toLat: CoordinateValue!, $toLon: CoordinateValue!,
      $dateTime: PlanDateTimeInput!,
      $accessMode: PlanAccessMode!, $egressMode: PlanEgressMode!
    ) {
      planConnection(
        origin: { location: { coordinate: { latitude: $fromLat, longitude: $fromLon } } }
        destination: { location: { coordinate: { latitude: $toLat, longitude: $toLon } } }
        dateTime: $dateTime
        modes: { transit: { access: [$accessMode], egress: [$egressMode] } }
      ) {
        edges {
          node {
            start
            end
            legs {
              mode
              duration
              from { name lat lon }
              to { name lat lon }
              route { shortName }
              legGeometry { points }
            }
          }
        }
      }
    }
    """;

  private final GraphQL graphQL;
  private final GraphQLRequestContext requestContext;

  GtfsWarmupQueryExecutor(OtpServerRequestContext context) {
    this.requestContext = GraphQLRequestContext.ofServerContext(context);
    this.graphQL = GraphQL.newGraphQL(context.gtfsSchema())
      .defaultDataFetcherExceptionHandler(new OtpDataFetcherExceptionHandler())
      .build();
  }

  @Override
  public int modeCombinationCount() {
    return ACCESS_MODES.size();
  }

  @Override
  public boolean execute(WgsCoordinate from, WgsCoordinate to, boolean arriveBy, int modeIndex) {
    var variables = buildVariables(from, to, arriveBy, modeIndex);

    var input = ExecutionInput.newExecutionInput()
      .query(QUERY)
      .context(requestContext)
      .variables(variables)
      .locale(Locale.US)
      .build();

    var result = graphQL.execute(input);
    if (!result.getErrors().isEmpty()) {
      LOG.warn("Warmup query had GraphQL errors: {}", result.getErrors());
      return false;
    }
    return true;
  }

  static Map<String, Object> buildVariables(
    WgsCoordinate from,
    WgsCoordinate to,
    boolean arriveBy,
    int modeIndex
  ) {
    var now = Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    var dateTime = arriveBy ? Map.of("latestArrival", now) : Map.of("earliestDeparture", now);

    return Map.ofEntries(
      Map.entry("fromLat", from.latitude()),
      Map.entry("fromLon", from.longitude()),
      Map.entry("toLat", to.latitude()),
      Map.entry("toLon", to.longitude()),
      Map.entry("dateTime", dateTime),
      Map.entry("accessMode", ACCESS_MODES.get(modeIndex % ACCESS_MODES.size())),
      Map.entry("egressMode", EGRESS_MODES.get(modeIndex % EGRESS_MODES.size()))
    );
  }
}
