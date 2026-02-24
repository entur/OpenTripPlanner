package org.opentripplanner.transit.model.filter.transit;

import java.util.ArrayList;
import java.util.List;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.api.model.FilterValues;
import org.opentripplanner.transit.model.basic.MainAndSubMode;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 * Represents a single selection criterion for filtering
 * {@link org.opentripplanner.model.TripTimeOnDate} objects.
 * Criteria within a single request are combined with AND logic: all specified criteria must
 * match for the request to match. Empty/unset criteria are ignored (match everything).
 */
public class TripTimeOnDateSelectRequest {

  private final FilterValues<FeedScopedId> agencies;
  private final FilterValues<FeedScopedId> routes;
  private final FilterValues<MainAndSubMode> transportModes;

  private TripTimeOnDateSelectRequest(Builder builder) {
    this.agencies = FilterValues.ofEmptyIsEverything("agencies", builder.agencies);
    this.routes = FilterValues.ofEmptyIsEverything("routes", builder.routes);
    this.transportModes = FilterValues.ofEmptyIsEverything(
      "transportModes",
      builder.transportModes
    );
  }

  public static Builder of() {
    return new Builder();
  }

  public FilterValues<FeedScopedId> agencies() {
    return agencies;
  }

  public FilterValues<FeedScopedId> routes() {
    return routes;
  }

  public FilterValues<MainAndSubMode> transportModes() {
    return transportModes;
  }

  @Override
  public String toString() {
    var builder = ToStringBuilder.ofEmbeddedType();
    if (!agencies.includeEverything()) {
      builder.addCol("agencies", agencies.get());
    }
    if (!routes.includeEverything()) {
      builder.addCol("routes", routes.get());
    }
    if (!transportModes.includeEverything()) {
      builder.addCol("transportModes", transportModes.get());
    }
    return builder.toString();
  }

  public static class Builder {

    private List<FeedScopedId> agencies = new ArrayList<>();
    private List<FeedScopedId> routes = new ArrayList<>();
    private List<MainAndSubMode> transportModes = new ArrayList<>();

    public Builder withAgencies(List<FeedScopedId> agencies) {
      this.agencies = agencies;
      return this;
    }

    public Builder withRoutes(List<FeedScopedId> routes) {
      this.routes = routes;
      return this;
    }

    public Builder withTransportModes(List<MainAndSubMode> transportModes) {
      this.transportModes = transportModes;
      return this;
    }

    public TripTimeOnDateSelectRequest build() {
      return new TripTimeOnDateSelectRequest(this);
    }
  }
}
