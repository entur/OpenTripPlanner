package org.opentripplanner.standalone.server.warmup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.validation.Validator;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner._support.time.ZoneIds;
import org.opentripplanner.api.model.transit.DefaultFeedIdMapper;
import org.opentripplanner.apis.gtfs.SchemaFactory;
import org.opentripplanner.apis.support.graphql.injectdoc.ApiDocumentationProfile;
import org.opentripplanner.apis.transmodel.TransmodelGraphQLSchemaFactory;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TransitTuningParametersTestFactory;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.street.geometry.WgsCoordinate;

/**
 * Validates that the warmup GraphQL queries are syntactically and structurally valid
 * against the actual schemas. This catches field renames, removed arguments, or
 * invalid enum values at build time rather than at runtime.
 */
class WarmupQueryValidationTest {

  private static final GraphQLSchema GTFS_SCHEMA = SchemaFactory.createSchemaWithDefaultInjection(
    RouteRequest.defaultValue()
  );

  private static final GraphQLSchema TRANSMODEL_SCHEMA = new TransmodelGraphQLSchemaFactory(
    RouteRequest.defaultValue(),
    ZoneIds.OSLO,
    TransitTuningParametersTestFactory.forTest(),
    new DefaultFeedIdMapper(),
    ApiDocumentationProfile.DEFAULT
  ).create();

  private static final WgsCoordinate FROM = new WgsCoordinate(59.9139, 10.7522);
  private static final WgsCoordinate TO = new WgsCoordinate(59.95, 10.76);

  static Stream<Arguments> modeAndDirectionCombinations() {
    return Stream.of(
      Arguments.of(0, false),
      Arguments.of(0, true),
      Arguments.of(1, false),
      Arguments.of(1, true),
      Arguments.of(2, false),
      Arguments.of(2, true)
    );
  }

  @ParameterizedTest(name = "TransModel query: modeIndex={0}, arriveBy={1}")
  @MethodSource("modeAndDirectionCombinations")
  void transmodelQueriesAreValid(int modeIndex, boolean arriveBy) {
    var executor = new TransmodelWarmupQueryExecutor();
    var query = executor.buildQuery(FROM, TO, arriveBy, modeIndex);
    var errors = new Validator().validateDocument(
      TRANSMODEL_SCHEMA,
      new Parser().parse(query),
      Locale.US
    );
    assertEquals(0, errors.size(), errors.toString());
  }

  @ParameterizedTest(name = "GTFS query: modeIndex={0}, arriveBy={1}")
  @MethodSource("modeAndDirectionCombinations")
  void gtfsQueriesAreValid(int modeIndex, boolean arriveBy) {
    var executor = new GtfsWarmupQueryExecutor();
    var query = executor.buildQuery(FROM, TO, arriveBy, modeIndex);
    var errors = new Validator().validateDocument(
      GTFS_SCHEMA,
      new Parser().parse(query),
      Locale.US
    );
    assertEquals(0, errors.size(), errors.toString());
  }
}
