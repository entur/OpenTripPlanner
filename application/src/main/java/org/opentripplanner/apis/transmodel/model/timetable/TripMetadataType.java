package org.opentripplanner.apis.transmodel.model.timetable;

import graphql.Scalars;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import org.opentripplanner.routing.api.response.TripSearchMetadata;

public class TripMetadataType {

  private TripMetadataType() {}

  public static GraphQLObjectType create(GraphQLScalarType dateTimeScalar) {
    return GraphQLObjectType.newObject()
      .name("TripSearchData")
      .description("Trips search metadata.")
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("searchWindowUsed")
          .description(
            "This is the time window used by the raptor search. The input searchWindow " +
            "is an optional parameter and is dynamically assigned if not set. OTP might " +
            "override the value if it is too small or too large. When paging OTP adjusts " +
            "it to the appropriate size, depending on the number of itineraries found in " +
            "the current search window. The scaling of the search window ensures faster " +
            "paging and limits resource usage. The unit is minutes."
          )
          .deprecate("This not needed for debugging, and is missleading if the window is croped.")
          .type(new GraphQLNonNull(Scalars.GraphQLInt))
          .dataFetcher(e ->
            ((TripSearchMetadata) e.getSource()).raptorSearchWindowUsed().toMinutes()
          )
          .build()
      )
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("nextDateTime")
          .description("This will not be available after Match 2026!")
          .deprecate("Use pageCursor instead")
          .type(dateTimeScalar)
          .dataFetcher(e -> ((TripSearchMetadata) e.getSource()).nextDateTime().toEpochMilli())
          .build()
      )
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("prevDateTime")
          .description("This will not be available after Match 2026!")
          .deprecate("Use pageCursor instead")
          .type(dateTimeScalar)
          .dataFetcher(e -> ((TripSearchMetadata) e.getSource()).prevDateTime().toEpochMilli())
          .build()
      )
      .build();
  }
}
