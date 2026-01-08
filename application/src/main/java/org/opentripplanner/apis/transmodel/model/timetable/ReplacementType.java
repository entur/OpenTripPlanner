package org.opentripplanner.apis.transmodel.model.timetable;

import graphql.Scalars;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLTypeReference;

public class ReplacementType {

  private static final String NAME = "Replacement";

  private static final GraphQLTypeReference DATED_SERVICE_JOURNEY_REF = new GraphQLTypeReference(
    "DatedServiceJourney"
  );

  public GraphQLObjectType create() {
    return GraphQLObjectType.newObject()
      .name(NAME)
      .description("Container for replacement properties for a TripOnServiceDate.")
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("isReplacement")
          .type(new GraphQLNonNull(Scalars.GraphQLBoolean))
          .description("Is this trip a replacement?")
          .build()
      )
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("replacementFor")
          .type(new GraphQLList(new GraphQLNonNull(DATED_SERVICE_JOURNEY_REF)))
          .description(
            "What TripOnServiceDates is this trip a replacement for? Only available for NeTEx-sourced data."
          )
          .build()
      )
      .build();
  }
}
