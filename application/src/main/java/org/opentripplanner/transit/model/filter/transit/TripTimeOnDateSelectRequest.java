package org.opentripplanner.transit.model.filter.transit;

import java.util.ArrayList;
import java.util.List;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.model.TripTimeOnDate;
import org.opentripplanner.model.modes.AllowTransitModeFilter;
import org.opentripplanner.transit.model.basic.MainAndSubMode;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 * Represents a single selection criterion for filtering {@link TripTimeOnDate} objects.
 * Criteria within a single request are combined with AND logic: all specified criteria must
 * match for the request to match. Empty/unset criteria are ignored (match everything).
 */
public class TripTimeOnDateSelectRequest {

  private final List<FeedScopedId> agencies;
  private final List<FeedScopedId> routes;
  private final AllowTransitModeFilter transportModeFilter;

  private TripTimeOnDateSelectRequest(Builder builder) {
    this.agencies = List.copyOf(builder.agencies);
    this.routes = List.copyOf(builder.routes);
    this.transportModeFilter = builder.transportModes.isEmpty()
      ? null
      : AllowTransitModeFilter.of(builder.transportModes);
  }

  public static Builder of() {
    return new Builder();
  }

  /**
   * Returns true if the given TripTimeOnDate matches all criteria specified in this request.
   * Empty/unset criteria are ignored (treated as "match all").
   */
  public boolean matches(TripTimeOnDate tripTime) {
    var trip = tripTime.getTrip();
    var route = trip.getRoute();

    if (!agencies.isEmpty() && !agencies.contains(route.getAgency().getId())) {
      return false;
    }
    if (!routes.isEmpty() && !routes.contains(route.getId())) {
      return false;
    }
    if (
      transportModeFilter != null &&
      !transportModeFilter.match(trip.getMode(), trip.getNetexSubMode())
    ) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return ToStringBuilder.ofEmbeddedType()
      .addObj("transportModes", transportModeFilter, null)
      .addCol("agencies", agencies, List.of())
      .addObj("routes", routes, List.of())
      .toString();
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
