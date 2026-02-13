package org.opentripplanner.transit.model.filter.transit;

import java.util.List;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.model.TripTimeOnDate;
import org.opentripplanner.transit.api.request.TripTimeOnDateRequest;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.filter.expr.EqualityMatcher;
import org.opentripplanner.transit.model.filter.expr.ExpressionBuilder;
import org.opentripplanner.transit.model.filter.expr.Matcher;
import org.opentripplanner.transit.model.filter.expr.OrMatcher;

/**
 * A factory for creating matchers for TripOnServiceDates.
 * <p>
 * This factory is used to create matchers for {@link org.opentripplanner.model.TripTimeOnDate} objects based on a request.
 * The resulting matcher can be used to filter a list of TripOnServiceDate objects.
 */
public class TripTimeOnDateMatcherFactory {

  /**
   * Creates a matcher for TripTimeOnDate.
   * <p>
   * When the request contains transit filters, the filter-based matching is used which supports
   * the select/not pattern from {@link TripTimeOnDateFilterRequest}. Otherwise, the flat
   * include/exclude filter values are used.
   */
  public static Matcher<TripTimeOnDate> of(TripTimeOnDateRequest request) {
    if (!request.transitFilters().isEmpty()) {
      return ofSelectorBasedTransitFilters(request.transitFilters());
    }
    return ofFlatFilters(request);
  }

  /**
   * Creates a matcher from the flat include/exclude filter values on the request.
   */
  private static Matcher<TripTimeOnDate> ofFlatFilters(TripTimeOnDateRequest request) {
    ExpressionBuilder<TripTimeOnDate> expr = ExpressionBuilder.of();

    expr.atLeastOneMatch(request.includeAgencies(), TripTimeOnDateMatcherFactory::agencyId);
    expr.atLeastOneMatch(request.includeRoutes(), TripTimeOnDateMatcherFactory::routeId);
    expr.atLeastOneMatch(request.includeModes(), TripTimeOnDateMatcherFactory::mode);
    expr.matchesNone(request.excludeAgencies(), TripTimeOnDateMatcherFactory::agencyId);
    expr.matchesNone(request.excludeRoutes(), TripTimeOnDateMatcherFactory::routeId);
    expr.matchesNone(request.excludeModes(), TripTimeOnDateMatcherFactory::mode);
    return expr.build();
  }

  /**
   * Creates a matcher from a list of {@link TripTimeOnDateFilterRequest} objects.
   * A TripTimeOnDate matches if it matches at least one of the filters (OR between filters).
   */
  static Matcher<TripTimeOnDate> ofSelectorBasedTransitFilters(
    List<TripTimeOnDateFilterRequest> filters
  ) {
    List<Matcher<TripTimeOnDate>> filterMatchers = filters
      .stream()
      .<Matcher<TripTimeOnDate>>map(filter -> filter::matches)
      .toList();

    if (filterMatchers.isEmpty()) {
      return Matcher.everything();
    }
    return OrMatcher.of(filterMatchers);
  }

  private static Matcher<TripTimeOnDate> agencyId(FeedScopedId id) {
    return new EqualityMatcher<>("agency", id, t -> t.getTrip().getRoute().getAgency().getId());
  }

  private static Matcher<TripTimeOnDate> routeId(FeedScopedId id) {
    return new EqualityMatcher<>("route", id, t -> t.getTrip().getRoute().getId());
  }

  private static Matcher<TripTimeOnDate> mode(TransitMode mode) {
    return new EqualityMatcher<>("mode", mode, t -> t.getTrip().getRoute().getMode());
  }
}
