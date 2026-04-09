package org.opentripplanner.standalone.server.warmup;

import graphql.ExecutionInput;
import graphql.GraphQL;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.opentripplanner.apis.gtfs.GraphQLRequestContext;
import org.opentripplanner.apis.support.graphql.OtpDataFetcherExceptionHandler;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GtfsWarmupQueryExecutor implements WarmupQueryExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(GtfsWarmupQueryExecutor.class);

  // Access and egress modes paired so each combination is valid per the GTFS schema.
  // CAR_PARKING is only valid as an access mode, not as an egress mode.
  private static final List<String> ACCESS_MODES = List.of("WALK", "BICYCLE", "CAR_PARKING");
  private static final List<String> EGRESS_MODES = List.of("WALK", "BICYCLE", "WALK");

  @Override
  public int modeCombinationCount() {
    return ACCESS_MODES.size();
  }

  @Override
  public void execute(
    OtpServerRequestContext context,
    WgsCoordinate from,
    WgsCoordinate to,
    boolean arriveBy,
    int modeIndex
  ) {
    var query = buildQuery(from, to, arriveBy, modeIndex);

    var schema = context.gtfsSchema();
    var requestContext = GraphQLRequestContext.ofServerContext(context);

    var input = ExecutionInput.newExecutionInput()
      .query(query)
      .context(requestContext)
      .variables(Collections.emptyMap())
      .locale(Locale.US)
      .build();

    var graphQL = GraphQL.newGraphQL(schema)
      .defaultDataFetcherExceptionHandler(new OtpDataFetcherExceptionHandler())
      .build();
    var result = graphQL.execute(input);
    if (!result.getErrors().isEmpty()) {
      LOG.debug("Warmup query had GraphQL errors: {}", result.getErrors());
    }
  }

  String buildQuery(WgsCoordinate from, WgsCoordinate to, boolean arriveBy, int modeIndex) {
    var accessMode = ACCESS_MODES.get(modeIndex % ACCESS_MODES.size());
    var egressMode = EGRESS_MODES.get(modeIndex % EGRESS_MODES.size());

    var now = Instant.now();
    var dateTime = now
      .atOffset(java.time.ZoneOffset.UTC)
      .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    var dateTimeArg = arriveBy
      ? "dateTime: { latestArrival: \"" + dateTime + "\" }"
      : "dateTime: { earliestDeparture: \"" + dateTime + "\" }";

    return String.format(
      Locale.ROOT,
      """
      {
        planConnection(
          origin: { location: { coordinate: { latitude: %f, longitude: %f } } }
          destination: { location: { coordinate: { latitude: %f, longitude: %f } } }
          %s
          modes: { transit: { access: [%s], egress: [%s] } }
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
      """,
      from.latitude(),
      from.longitude(),
      to.latitude(),
      to.longitude(),
      dateTimeArg,
      accessMode,
      egressMode
    );
  }
}
