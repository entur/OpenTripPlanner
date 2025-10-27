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
          .name("pageDepartureTimeStart")
          .description(
            """
            The start-time of the search-window/page for trip departure times.

            The search-window/page start and end time describe the time-window the search is performed
            in. All results in the window is expected to be inside the given window. When navigating
            to the next/previous window the new window might overlap.

            **Merging in other trips**

            Other trips(separate search) can be merged in if:
            - The trip departure time is between `pageDepartureTimeStart` and `pageDepartureTimeEnd`.
            - The result set is empty, or
            - the trip sorts before the last trip returned. If the trip sorts after the last trip it
              should be merged into the next page.
            """
          )
          .type(dateTimeScalar)
          .dataFetcher(e -> ((TripSearchMetadata) e.getSource()).pageDepartureTimeStart())
          .build()
      )
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("pageDepartureTimeEnd")
          .description(
            "The end-time of the search-window/page for trip departure times. See `pageDepartureTimeStart`"
          )
          .type(dateTimeScalar)
          .dataFetcher(e -> ((TripSearchMetadata) e.getSource()).pageDepartureTimeEnd())
          .build()
      )
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
          .deprecate("This not needed for debugging, and is misleading if the window is cropped.")
          .type(new GraphQLNonNull(Scalars.GraphQLInt))
          .dataFetcher(e ->
            ((TripSearchMetadata) e.getSource()).raptorSearchWindowUsed().toMinutes()
          )
          .build()
      )
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("nextDateTime")
          .description("This will not be available after March 2026!")
          .deprecate("Use pageCursor instead")
          .type(dateTimeScalar)
          .dataFetcher(e -> ((TripSearchMetadata) e.getSource()).nextDateTime())
          .build()
      )
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("prevDateTime")
          .description("This will not be available after March 2026!")
          .deprecate("Use pageCursor instead")
          .type(dateTimeScalar)
          .dataFetcher(e -> ((TripSearchMetadata) e.getSource()).prevDateTime())
          .build()
      )
      .build();
  }
}
