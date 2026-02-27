package org.opentripplanner.apis.transmodel.model.stop;

import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;

public class EstimatedCallFilterInputType {

  public static final GraphQLInputObjectType INPUT_TYPE = GraphQLInputObjectType.newInputObject()
    .name("EstimatedCallFilterInput")
    .description("A collection of selectors for what estimated calls should be included / excluded")
    .field(
      GraphQLInputObjectField.newInputObjectField()
        .name("select")
        .description(
          "A list of selectors for what estimated calls should be included. " +
            "In order to be accepted a call has to match with at least one selector. " +
            "An empty list means that everything should be allowed. "
        )
        .type(new GraphQLList(new GraphQLNonNull(EstimatedCallSelectInputType.INPUT_TYPE)))
        .build()
    )
    .field(
      GraphQLInputObjectField.newInputObjectField()
        .name("not")
        .description(
          "A list of selectors for what estimated calls should be excluded during the search. " +
            "If a call matches with at least one selector it will be excluded."
        )
        .type(new GraphQLList(new GraphQLNonNull(EstimatedCallSelectInputType.INPUT_TYPE)))
        .build()
    )
    .build();
}
