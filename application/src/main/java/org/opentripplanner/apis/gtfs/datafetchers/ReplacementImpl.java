package org.opentripplanner.apis.gtfs.datafetchers;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.opentripplanner.apis.gtfs.generated.GraphQLDataFetchers;
import org.opentripplanner.transit.model.network.Replacement;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;

public class ReplacementImpl implements GraphQLDataFetchers.GraphQLReplacement {

  @Override
  public DataFetcher<Boolean> isReplacement() {
    return environment -> getSource(environment).getIsReplacement();
  }

  @Override
  public DataFetcher<Iterable<TripOnServiceDate>> replacementFor() {
    return environment -> getSource(environment).getReplacementFor();
  }

  private Replacement getSource(DataFetchingEnvironment environment) {
    return environment.getSource();
  }
}
