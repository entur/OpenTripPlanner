package org.opentripplanner.transit.model.filter.transit;

import java.util.ArrayList;
import java.util.List;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.model.TripTimeOnDate;
import org.opentripplanner.model.modes.AllowTransitModeFilter;
import org.opentripplanner.transit.model.basic.MainAndSubMode;
import org.opentripplanner.transit.model.filter.expr.AndMatcher;
import org.opentripplanner.transit.model.filter.expr.EqualityMatcher;
import org.opentripplanner.transit.model.filter.expr.GenericUnaryMatcher;
import org.opentripplanner.transit.model.filter.expr.Matcher;
import org.opentripplanner.transit.model.filter.expr.OrMatcher;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 * Represents a single selection criterion for filtering {@link TripTimeOnDate} objects.
 * Criteria within a single request are combined with AND logic: all specified criteria must
 * match for the request to match. Empty/unset criteria are ignored (match everything).
 */
public class TripTimeOnDateSelectRequest {

  private final Matcher<TripTimeOnDate> matcher;

  private TripTimeOnDateSelectRequest(Builder builder) {
    var transportModeFilter = builder.transportModes.isEmpty()
      ? null
      : AllowTransitModeFilter.of(builder.transportModes);

    this.matcher = AndMatcher.of(
      List.of(
        !builder.agencies.isEmpty()
          ? OrMatcher.of(
              builder.agencies
                .stream()
                .map(agency ->
                  (Matcher<TripTimeOnDate>) new EqualityMatcher<>(
                    "agency",
                    agency,
                    (TripTimeOnDate tripTime) -> tripTime.getTrip().getRoute().getAgency().getId()
                  )
                )
                .toList()
            )
          : Matcher.everything(),
        !builder.routes.isEmpty()
          ? OrMatcher.of(
              builder.routes
                .stream()
                .map(route ->
                  (Matcher<TripTimeOnDate>) new EqualityMatcher<>(
                    "route",
                    route,
                    (TripTimeOnDate tripTime) -> tripTime.getTrip().getRoute().getId()
                  )
                )
                .toList()
            )
          : Matcher.everything(),
        transportModeFilter != null
          ? new GenericUnaryMatcher<>("transportMode", (TripTimeOnDate tripTime) ->
              transportModeFilter.match(
                tripTime.getTrip().getMode(),
                tripTime.getTrip().getNetexSubMode()
              )
            )
          : Matcher.everything()
      )
    );
  }

  public static Builder of() {
    return new Builder();
  }

  /**
   * Returns true if the given TripTimeOnDate matches all criteria specified in this request.
   * Empty/unset criteria are ignored (treated as "match all").
   */
  public boolean matches(TripTimeOnDate tripTime) {
    return matcher.match(tripTime);
  }

  @Override
  public String toString() {
    return ToStringBuilder.ofEmbeddedType().addObj("matcher", matcher).toString();
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
