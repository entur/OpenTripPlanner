package org.opentripplanner.apis.transmodel.model.framework;

import static graphql.Directives.OneOfDirective;
import static graphql.Scalars.GraphQLString;

import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLScalarType;

public class PointInJourneyPatternReferenceInputType {

  public static final String FIELD_STOP_ID = "stopId";
  public static final String FIELD_STOP_ID_AND_SCHEDULED_DEPARTURE_TIME =
    "stopIdAndScheduledDepartureTime";

  public static GraphQLInputObjectType create(GraphQLScalarType dateTimeScalar) {
    return GraphQLInputObjectType.newInputObject()
      .name("PointInJourneyPatternReference")
      .description(
        "Identifies a point in a journey pattern. " + "Exactly one of the fields must be set."
      )
      .withDirective(OneOfDirective)
      .field(b ->
        b
          .name(FIELD_STOP_ID)
          .description(
            "Identifies the point by stop ID. " +
              "This does not work if the stop is visited more than once in the " +
              "journey pattern (e.g. ring lines); use stopIdAndScheduledDepartureTime instead."
          )
          .type(GraphQLString)
      )
      .field(b ->
        b
          .name(FIELD_STOP_ID_AND_SCHEDULED_DEPARTURE_TIME)
          .description(
            "Identifies the point by stop ID and scheduled departure time. " +
              "The scheduled departure time disambiguates when the stop is visited " +
              "more than once in the journey pattern (e.g. ring lines)."
          )
          .type(StopIdAndScheduledDepartureTimeInputType.create(dateTimeScalar))
      )
      .build();
  }
}
