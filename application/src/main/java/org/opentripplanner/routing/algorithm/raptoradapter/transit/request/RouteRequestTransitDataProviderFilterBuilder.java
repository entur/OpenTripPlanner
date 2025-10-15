package org.opentripplanner.routing.algorithm.raptoradapter.transit.request;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.routing.api.request.request.filter.TransitFilter;
import org.opentripplanner.transit.model.framework.FeedScopedId;

public class RouteRequestTransitDataProviderFilterBuilder {

  private boolean requireBikesAllowed = false;

  private boolean requireCarsAllowed = false;

  private boolean requireWheelchairAccessibleTrips = false;

  private boolean requireWheelchairAccessibleStops = false;

  private boolean includePlannedCancellations = false;

  private boolean includeRealtimeCancellations = false;

  /**
   * This is stored as an array, as they are iterated over for each trip when filtering transit
   * data. Iterator creation is relatively expensive compared to iterating over a short array.
   */
  private List<TransitFilter> filters = new ArrayList<>();

  private Collection<FeedScopedId> bannedTrips = List.of();

  public static RouteRequestTransitDataProviderFilterBuilder ofRequest(RouteRequest request) {
    var wheelchairEnabled = request.journey().wheelchair();
    var wheelchair = request.preferences().wheelchair();

    var builder = new RouteRequestTransitDataProviderFilterBuilder()
      .withRequireBikesAllowed(request.journey().transfer().mode() == StreetMode.BIKE)
      .withRequireCarsAllowed(request.journey().transfer().mode() == StreetMode.CAR)
      .withRequireWheelchairAccessibleTrips(
        wheelchairEnabled && wheelchair.trip().onlyConsiderAccessible()
      )
      .withRequireWheelchairAccessibleStops(
        wheelchairEnabled && wheelchair.stop().onlyConsiderAccessible()
      )
      .withIncludePlannedCancellations(
        request.preferences().transit().includePlannedCancellations()
      )
      .withIncludeRealtimeCancellations(
        request.preferences().transit().includeRealtimeCancellations()
      )
      .withBannedTrips(request.journey().transit().bannedTrips())
      .withFilters(request.journey().transit().filters());
    return builder;
  }

  public RouteRequestTransitDataProviderFilterBuilder withRequireBikesAllowed(
    boolean requireBikesAllowed
  ) {
    this.requireBikesAllowed = requireBikesAllowed;
    return this;
  }

  public RouteRequestTransitDataProviderFilterBuilder withRequireCarsAllowed(
    boolean requireCarsAllowed
  ) {
    this.requireCarsAllowed = requireCarsAllowed;
    return this;
  }

  public RouteRequestTransitDataProviderFilterBuilder withRequireWheelchairAccessibleTrips(
    boolean requireWheelchairAccessibleTrips
  ) {
    this.requireWheelchairAccessibleTrips = requireWheelchairAccessibleTrips;
    return this;
  }

  public RouteRequestTransitDataProviderFilterBuilder withRequireWheelchairAccessibleStops(
    boolean requireWheelchairAccessibleStops
  ) {
    this.requireWheelchairAccessibleStops = requireWheelchairAccessibleStops;
    return this;
  }

  public RouteRequestTransitDataProviderFilterBuilder withIncludePlannedCancellations(
    boolean includePlannedCancellations
  ) {
    this.includePlannedCancellations = includePlannedCancellations;
    return this;
  }

  public RouteRequestTransitDataProviderFilterBuilder withIncludeRealtimeCancellations(
    boolean includeRealtimeCancellations
  ) {
    this.includeRealtimeCancellations = includeRealtimeCancellations;
    return this;
  }

  public RouteRequestTransitDataProviderFilterBuilder withBannedTrips(
    Collection<FeedScopedId> bannedTrips
  ) {
    this.bannedTrips = bannedTrips;
    return this;
  }

  public RouteRequestTransitDataProviderFilterBuilder withFilters(List<TransitFilter> filters) {
    this.filters = new ArrayList<>(filters);
    return this;
  }

  public RouteRequestTransitDataProviderFilterBuilder addFilter(TransitFilter filter) {
    this.filters.add(filter);
    return this;
  }

  public boolean hasSubmodeFilters() {
    return filters.stream().anyMatch(TransitFilter::isSubModePredicate);
  }

  public boolean requireBikesAllowed() {
    return requireBikesAllowed;
  }

  public boolean requireCarsAllowed() {
    return requireCarsAllowed;
  }

  public boolean requireWheelchairAccessibleTrips() {
    return requireWheelchairAccessibleTrips;
  }

  public boolean requireWheelchairAccessibleStops() {
    return requireWheelchairAccessibleStops;
  }

  public boolean includePlannedCancellations() {
    return includePlannedCancellations;
  }

  public boolean includeRealtimeCancellations() {
    return includeRealtimeCancellations;
  }

  public Collection<FeedScopedId> bannedTrips() {
    return bannedTrips;
  }

  public List<TransitFilter> filters() {
    return filters;
  }

  public RouteRequestTransitDataProviderFilter build() {
    return new RouteRequestTransitDataProviderFilter(this);
  }
}
