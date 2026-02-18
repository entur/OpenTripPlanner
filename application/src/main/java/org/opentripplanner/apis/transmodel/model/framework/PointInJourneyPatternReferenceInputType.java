package org.opentripplanner.apis.transmodel.model.framework;

import static graphql.Directives.OneOfDirective;
import static graphql.Scalars.GraphQLString;

import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLScalarType;

public class PointInJourneyPatternReferenceInputType {

  public static final String FIELD_STOP_LOCATION_ID = "stopLocationId";
  public static final String FIELD_STOP_LOCATION_ID_AND_SCHEDULED_DEPARTURE_TIME =
    "stopLocationIdAndScheduledDepartureTime";

  public static GraphQLInputObjectType create(GraphQLScalarType dateTimeScalar) {
    return GraphQLInputObjectType.newInputObject()
      .name("PointInJourneyPatternReference")
      .description(
        "Identifies a point in a journey pattern. " + "Exactly one of the fields must be set."
      )
      .withDirective(OneOfDirective)
      .field(b ->
        b
          .name(FIELD_STOP_LOCATION_ID)
          .description(
            "Identifies the point by stop location ID. A stop location can be a quay, " +
              "a stop place, a multimodal stop place or a group of stop places. " +
              "This does not work if the stop location is visited more than once in the " +
              "journey pattern (e.g. ring lines); use " +
              "stopLocationIdAndScheduledDepartureTime instead."
          )
          .type(GraphQLString)
      )
      .field(b ->
        b
          .name(FIELD_STOP_LOCATION_ID_AND_SCHEDULED_DEPARTURE_TIME)
          .description(
            "Identifies the point by stop location ID and scheduled departure time. " +
              "A stop location can be a quay, a stop place, a multimodal stop place or " +
              "a group of stop places. The scheduled departure time disambiguates when " +
              "the stop location is visited more than once in the journey pattern " +
              "(e.g. ring lines)."
          )
          .type(StopLocationIdAndScheduledDepartureTimeInputType.create(dateTimeScalar))
      )
      .build();
  }
}
