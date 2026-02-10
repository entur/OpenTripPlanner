package org.opentripplanner.apis.transmodel.model.framework;

import static graphql.Directives.OneOfDirective;
import static graphql.Scalars.GraphQLString;

import graphql.schema.GraphQLInputObjectType;

public class TripOnDateReferenceInputType {

  public static final String FIELD_TRIP_ID_ON_SERVICE_DATE = "tripIdOnServiceDate";
  public static final String FIELD_TRIP_ON_DATE_ID = "tripOnDateId";

  public static final GraphQLInputObjectType INPUT_TYPE = GraphQLInputObjectType.newInputObject()
    .name("TripOnDateReference")
    .description(
      "Identifies a specific trip on a specific service date. " +
        "Exactly one of the fields must be set."
    )
    .withDirective(OneOfDirective)
    .field(b ->
      b
        .name(FIELD_TRIP_ID_ON_SERVICE_DATE)
        .description("Identifies the trip by trip ID and service date.")
        .type(TripIdOnServiceDateInputType.INPUT_TYPE)
    )
    .field(b ->
      b
        .name(FIELD_TRIP_ON_DATE_ID)
        .description(
          "Identifies the trip by a dated service journey ID " +
            "(e.g. from NeTEx data where a trip on a date has a unique ID)."
        )
        .type(GraphQLString)
    )
    .build();
}
