package org.opentripplanner.apis.gtfs.datafetchers;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.opentripplanner.apis.gtfs.generated.GraphQLDataFetchers;
import org.opentripplanner.apis.gtfs.model.StopCallForTripOnServiceDate;
import org.opentripplanner.model.TripTimeOnDate;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;

public class StopCallForTripOnServiceDateImpl
  implements GraphQLDataFetchers.GraphQLStopCallForTripOnServiceDate {

  @Override
  public DataFetcher<TripTimeOnDate> stopCall() {
    return environment -> getSource(environment).stopCall();
  }

  @Override
  public DataFetcher<TripOnServiceDate> tripOnServiceDate() {
    return environment -> getSource(environment).tripOnServiceDate();
  }

  private StopCallForTripOnServiceDate getSource(DataFetchingEnvironment environment) {
    return environment.getSource();
  }
}
