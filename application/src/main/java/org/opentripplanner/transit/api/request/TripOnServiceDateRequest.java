package org.opentripplanner.transit.api.request;

import java.time.LocalDate;
import java.util.List;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.api.model.FilterValues;
import org.opentripplanner.transit.model.basic.MainAndSubMode;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.filter.transit.TripOnServiceDateFilterRequest;
import org.opentripplanner.transit.model.timetable.TripAlteration;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;

/**
 * A request for trips on certain service dates.
 * </p>
 * This request is used to retrieve {@link TripOnServiceDate}s that match the provided filter
 * values.
 */
public class TripOnServiceDateRequest {

  private final FilterValues<LocalDate> includeServiceDates;
  private final FilterValues<FeedScopedId> includeAgencies;
  private final FilterValues<FeedScopedId> includeRoutes;
  private final FilterValues<FeedScopedId> includeServiceJourneys;
  private final FilterValues<FeedScopedId> includeReplacementFor;
  private final FilterValues<String> includeNetexInternalPlanningCodes;
  private final FilterValues<TripAlteration> includeAlterations;
  private final List<TripOnServiceDateFilterRequest> filters;

  TripOnServiceDateRequest(
    FilterValues<LocalDate> includeServiceDates,
    FilterValues<FeedScopedId> includeAgencies,
    FilterValues<FeedScopedId> includeRoutes,
    FilterValues<FeedScopedId> includeServiceJourneys,
    FilterValues<FeedScopedId> includeReplacementFor,
    FilterValues<String> includeNetexInternalPlanningCodes,
    FilterValues<TripAlteration> includeAlterations,
    List<TripOnServiceDateFilterRequest> filters
  ) {
    this.includeServiceDates = includeServiceDates;
    this.includeAgencies = includeAgencies;
    this.includeRoutes = includeRoutes;
    this.includeServiceJourneys = includeServiceJourneys;
    this.includeReplacementFor = includeReplacementFor;
    this.includeNetexInternalPlanningCodes = includeNetexInternalPlanningCodes;
    this.includeAlterations = includeAlterations;
    this.filters = filters;
  }

  public static TripOnServiceDateRequestBuilder of() {
    return new TripOnServiceDateRequestBuilder();
  }

  public FilterValues<FeedScopedId> includeAgencies() {
    return includeAgencies;
  }

  public FilterValues<FeedScopedId> includeRoutes() {
    return includeRoutes;
  }

  public FilterValues<FeedScopedId> includeServiceJourneys() {
    return includeServiceJourneys;
  }

  public FilterValues<FeedScopedId> includeReplacementFor() {
    return includeReplacementFor;
  }

  public FilterValues<String> includeNetexInternalPlanningCodes() {
    return includeNetexInternalPlanningCodes;
  }

  public FilterValues<TripAlteration> includeAlterations() {
    return includeAlterations;
  }

  public FilterValues<LocalDate> includeServiceDates() {
    return includeServiceDates;
  }

  public FilterValues<TransitMode> includeModes() {
    List<TransitMode> modesToInclude = null;
    if (!filters.isEmpty()) {
      var includes = filters.getFirst().select();
      if (includes != null && !includes.getFirst().transportModes().includeEverything()) {
        modesToInclude = includes
          .getFirst()
          .transportModes()
          .get()
          .stream()
          .map(MainAndSubMode::mainMode)
          .toList();
      }
    }
    return FilterValues.ofNullIsEverything("modesToInclude", modesToInclude);
  }

  public FilterValues<TransitMode> excludeModes() {
    List<TransitMode> modesToExclude = null;
    if (!filters.isEmpty()) {
      var excludes = filters.getFirst().not();

      if (excludes != null && !excludes.getFirst().transportModes().includeEverything()) {
        modesToExclude = excludes
          .getFirst()
          .transportModes()
          .get()
          .stream()
          .map(MainAndSubMode::mainMode)
          .toList();
      }
    }
    return FilterValues.ofNullIsEverything("modesToExclude", modesToExclude);
  }

  public List<TripOnServiceDateFilterRequest> filters() {
    return filters;
  }
}
