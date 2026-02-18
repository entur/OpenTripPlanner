package org.opentripplanner.apis.transmodel.model.framework;

import graphql.Scalars;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLScalarType;

public class StopLocationIdAndScheduledDepartureTimeInputType {

  public static GraphQLInputObjectType create(GraphQLScalarType dateTimeScalar) {
    return GraphQLInputObjectType.newInputObject()
      .name("StopLocationIdAndScheduledDepartureTime")
      .description(
        "Identifies a point in a journey pattern by stop location ID and scheduled departure " +
          "time. A stop location can be a quay, a stop place, a multimodal stop place or a " +
          "group of stop places. The scheduled departure time is used for disambiguation when " +
          "the stop is visited more than once in the journey pattern (e.g. ring lines)."
      )
      .field(
        GraphQLInputObjectField.newInputObjectField()
          .name("stopLocationId")
          .description(
            "The stop location ID. A stop location can be a quay, a stop place, " +
              "a multimodal stop place or a group of stop places."
          )
          .type(new GraphQLNonNull(Scalars.GraphQLString))
          .build()
      )
      .field(
        GraphQLInputObjectField.newInputObjectField()
          .name("scheduledDepartureTime")
          .description(
            "The scheduled departure time at this stop as a DateTime, " +
              "corresponding to the aimedDepartureTime on EstimatedCall."
          )
          .type(new GraphQLNonNull(dateTimeScalar))
          .build()
      )
      .build();
  }
}
