package org.opentripplanner.apis.transmodel.model.timetable;

import graphql.Scalars;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLTypeReference;
import org.jspecify.annotations.Nullable;
import org.opentripplanner.apis.transmodel.TransmodelRequestContext;
import org.opentripplanner.ext.empiricaldelay.model.EmpiricalDelay;
import org.opentripplanner.model.TripTimeOnDate;

public class EmpiricalDelayType {

  static final String NAME = "EmpiricalDelay";
  public static final GraphQLTypeReference REF = new GraphQLTypeReference(NAME);

  private EmpiricalDelayType() {}

  public static GraphQLObjectType create() {
    return GraphQLObjectType.newObject()
      .name(NAME)
      .description(
        """
        The empirical delay indicate how late a service journey is based on historic data. What the
        min and max percentiles represent is set per deployment. For example, if set to p10 and p90,
        then 10% of the arrival will have a delay less then the `minPercentile`, and 10% will have
        a delay larger than the `maxPercentile` value.
        """
      )
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("minPercentile")
          .description("Minimum percentile")
          .type(Scalars.GraphQLInt)
          .dataFetcher(e -> (empiricalDelay(e).minPercentile()))
          .build()
      )
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("maxPercentile")
          .description("Maximum percentile")
          .type(Scalars.GraphQLInt)
          .dataFetcher(e -> (empiricalDelay(e).maxPercentile()))
          .build()
      )
      .build();
  }

  @Nullable
  public static EmpiricalDelay dataFetcherForTripTimeOnDate(DataFetchingEnvironment environment) {
    TripTimeOnDate parent = environment.getSource();
    TransmodelRequestContext ctx = environment.getContext();
    var service = ctx.getEmpiricalDelayService();

    if (parent == null || service == null) {
      return null;
    }
    return service
      .findEmpiricalDelay(
        parent.getTrip().getId(),
        parent.getServiceDay(),
        parent.getStopPosition()
      )
      .orElse(null);
  }

  private static EmpiricalDelay empiricalDelay(DataFetchingEnvironment environment) {
    return environment.getSource();
  }
}
