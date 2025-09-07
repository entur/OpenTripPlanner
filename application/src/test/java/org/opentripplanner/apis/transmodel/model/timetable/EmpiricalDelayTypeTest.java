package org.opentripplanner.apis.transmodel.model.timetable;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import graphql.Scalars;
import graphql.schema.GraphQLObjectType;
import org.junit.jupiter.api.Test;

class EmpiricalDelayTypeTest {

  private static final GraphQLObjectType subject = EmpiricalDelayType.create();

  @Test
  void create() {
    var subject = EmpiricalDelayType.create();

    assertEquals(EmpiricalDelayType.NAME, subject.getName());
    assertThat(subject.getDescription()).isNotEmpty();

    var minPercentile = subject.getFieldDefinition("minPercentile");
    assertNotNull(minPercentile);
    assertEquals(Scalars.GraphQLInt, minPercentile.getType());

    var maxPercentile = subject.getFieldDefinition("maxPercentile");
    assertNotNull(maxPercentile);
    assertEquals(Scalars.GraphQLInt, maxPercentile.getType());
  }

  @Test
  void dataFetcherForTripTimeOnDate() {}
}
