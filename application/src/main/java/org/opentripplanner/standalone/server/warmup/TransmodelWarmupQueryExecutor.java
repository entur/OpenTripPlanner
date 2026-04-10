package org.opentripplanner.standalone.server.warmup;

import graphql.ExecutionInput;
import graphql.GraphQL;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.opentripplanner.apis.support.graphql.OtpDataFetcherExceptionHandler;
import org.opentripplanner.apis.transmodel.TransmodelRequestContext;
import org.opentripplanner.apis.transmodel.support.AbortOnUnprocessableRequestExecutionStrategy;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TransmodelWarmupQueryExecutor implements WarmupQueryExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(TransmodelWarmupQueryExecutor.class);

  // Access and egress modes paired so each combination is realistic.
  // car_park is only valid as access mode; use foot for egress in that case.
  private static final List<String> ACCESS_MODES = List.of("foot", "bicycle", "car_park");
  private static final List<String> EGRESS_MODES = List.of("foot", "bicycle", "foot");

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

    var schema = context.transmodelSchema();
    var requestContext = new TransmodelRequestContext(
      context,
      context.routingService(),
      context.transitService(),
      context.empiricalDelayService()
    );

    var input = ExecutionInput.newExecutionInput()
      .query(query)
      .context(requestContext)
      .root(context)
      .variables(Collections.emptyMap())
      .build();

    try (var strategy = new AbortOnUnprocessableRequestExecutionStrategy()) {
      var graphQL = GraphQL.newGraphQL(schema)
        .queryExecutionStrategy(strategy)
        .defaultDataFetcherExceptionHandler(new OtpDataFetcherExceptionHandler())
        .build();
      var result = graphQL.execute(input);
      if (!result.getErrors().isEmpty()) {
        LOG.debug("Warmup query had GraphQL errors: {}", result.getErrors());
      }
    }
  }

  String buildQuery(WgsCoordinate from, WgsCoordinate to, boolean arriveBy, int modeIndex) {
    var accessMode = ACCESS_MODES.get(modeIndex % ACCESS_MODES.size());
    var egressMode = EGRESS_MODES.get(modeIndex % EGRESS_MODES.size());

    return String.format(
      Locale.ROOT,
      """
      {
        trip(
          from: { coordinates: { latitude: %f, longitude: %f } }
          to: { coordinates: { latitude: %f, longitude: %f } }
          arriveBy: %s
          modes: { accessMode: %s, egressMode: %s }
        ) {
          tripPatterns {
            duration
            legs {
              mode
              duration
              fromPlace { name }
              toPlace { name }
              line { publicCode }
              pointsOnLink { points }
            }
          }
        }
      }
      """,
      from.latitude(),
      from.longitude(),
      to.latitude(),
      to.longitude(),
      arriveBy,
      accessMode,
      egressMode
    );
  }
}
