package org.opentripplanner.apis.transmodel.model.framework;

import graphql.Scalars;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLScalarType;

public class OnBoardLocationInputType {

  public static GraphQLInputObjectType create(GraphQLScalarType dateTimeScalar) {
    return GraphQLInputObjectType.newInputObject()
      .name("OnBoardLocationInput")
      .description(
        "Identifies a position on-board a specific transit trip. " +
          "Used to start a trip planning search from on-board a vehicle."
      )
      .field(
        GraphQLInputObjectField.newInputObjectField()
          .name("datedServiceJourneyReference")
          .description(
            "Identifies the trip and service date, either by trip ID and service date " +
              "or by a dated service journey ID."
          )
          .type(new GraphQLNonNull(DatedServiceJourneyReferenceInputType.INPUT_TYPE))
          .build()
      )
      .field(
        GraphQLInputObjectField.newInputObjectField()
          .name("stopId")
          .description(
            "The stop at which the traveler is considered to be boarding. " +
              "Used together with the trip to identify the exact stop position in the pattern."
          )
          .type(new GraphQLNonNull(Scalars.GraphQLString))
          .build()
      )
      .field(
        GraphQLInputObjectField.newInputObjectField()
          .name("scheduledDepartureTime")
          .description(
            "Disambiguates the stop position in the pattern in the case of ring lines " +
              "where the same stop is visited more than once. If provided, the stop whose " +
              "scheduled departure time matches is used."
          )
          .type(dateTimeScalar)
          .build()
      )
      .build();
  }
}
