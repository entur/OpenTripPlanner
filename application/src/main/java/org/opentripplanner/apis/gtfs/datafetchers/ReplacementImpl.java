package org.opentripplanner.apis.gtfs.datafetchers;

import graphql.schema.DataFetcher;
import org.opentripplanner.apis.gtfs.generated.GraphQLDataFetchers;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;

public class ReplacementImpl implements GraphQLDataFetchers.GraphQLReplacement {

  @Override
  public DataFetcher<Boolean> isReplacement() {
    return null;
  }

  @Override
  public DataFetcher<Iterable<TripOnServiceDate>> replacementFor() {
    return null;
  }
}
