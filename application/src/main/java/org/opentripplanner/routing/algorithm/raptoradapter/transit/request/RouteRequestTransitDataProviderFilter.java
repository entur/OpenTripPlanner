package org.opentripplanner.routing.algorithm.raptoradapter.transit.request;

import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.routing.api.request.request.filter.TransitFilter;
import org.opentripplanner.transit.model.basic.Accessibility;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.transit.model.network.BikeAccess;
import org.opentripplanner.transit.model.network.CarAccess;
import org.opentripplanner.transit.model.network.RoutingTripPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;

public class RouteRequestTransitDataProviderFilter implements TransitDataProviderFilter {

  private final boolean requireBikesAllowed;

  private final boolean requireCarsAllowed;

  private final boolean requireWheelchairAccessibleTrips;

  private final boolean requireWheelchairAccessibleStops;

  private final boolean includePlannedCancellations;

  private final boolean includeRealtimeCancellations;

  /**
   * This is stored as an array, as they are iterated over for each trip when filtering transit
   * data. Iterator creation is relatively expensive compared to iterating over a short array.
   */
  private final TransitFilter[] filters;

  private final Set<FeedScopedId> bannedTrips;

  private final boolean hasSubModeFilters;

  public RouteRequestTransitDataProviderFilter(RouteRequest request) {
    var wheelchairEnabled = request.journey().wheelchair();
    var wheelchairPreferences = request.preferences().wheelchair();

    this.requireBikesAllowed = request.journey().transfer().mode() == StreetMode.BIKE;
    this.requireCarsAllowed = request.journey().transfer().mode() == StreetMode.CAR;
    this.requireWheelchairAccessibleTrips =
      wheelchairEnabled && wheelchairPreferences.trip().onlyConsiderAccessible();
    this.requireWheelchairAccessibleStops =
      wheelchairEnabled && wheelchairPreferences.stop().onlyConsiderAccessible();
    this.includePlannedCancellations = request
      .preferences()
      .transit()
      .includePlannedCancellations();
    this.includeRealtimeCancellations = request
      .preferences()
      .transit()
      .includeRealtimeCancellations();
    this.bannedTrips = Set.copyOf(request.journey().transit().bannedTrips());

    var filters = request.journey().transit().filters();
    this.filters = filters.toArray(TransitFilter[]::new);
    this.hasSubModeFilters = filters.stream().anyMatch(TransitFilter::isSubModePredicate);
  }

  // This constructor is used only for testing
  public RouteRequestTransitDataProviderFilter(
    boolean requireBikesAllowed,
    boolean requireCarsAllowed,
    boolean requireWheelchairAccessibleTrips,
    boolean requireWheelchairAccessibleStops,
    boolean includePlannedCancellations,
    boolean includeRealtimeCancellations,
    Set<FeedScopedId> bannedTrips,
    List<TransitFilter> filters
  ) {
    this.requireBikesAllowed = requireBikesAllowed;
    this.requireCarsAllowed = requireCarsAllowed;
    this.requireWheelchairAccessibleTrips = requireWheelchairAccessibleTrips;
    this.requireWheelchairAccessibleStops = requireWheelchairAccessibleStops;
    this.includePlannedCancellations = includePlannedCancellations;
    this.includeRealtimeCancellations = includeRealtimeCancellations;
    this.bannedTrips = bannedTrips;
    this.filters = filters.toArray(TransitFilter[]::new);
    this.hasSubModeFilters = filters.stream().anyMatch(TransitFilter::isSubModePredicate);
  }

  public static BikeAccess bikeAccessForTrip(Trip trip) {
    if (trip.getBikesAllowed() != BikeAccess.UNKNOWN) {
      return trip.getBikesAllowed();
    }

    return trip.getRoute().getBikesAllowed();
  }

  @Override
  @Nullable
  public Predicate<TripTimes> createTripFilter(TripPattern tripPattern) {
    for (TransitFilter filter : filters) {
      if (filter.matchTripPattern(tripPattern)) {
        var applyTripTimesFilters = hasSubModeFilters && tripPattern.getContainsMultipleModes();
        return tripTimes -> tripTimesPredicate(tripTimes, applyTripTimesFilters);
      }
    }
    return null;
  }

  private boolean tripTimesPredicate(TripTimes tripTimes, boolean applyTripTimesFilters) {
    final Trip trip = tripTimes.getTrip();

    if (requireBikesAllowed && bikeAccessForTrip(trip) != BikeAccess.ALLOWED) {
      return false;
    }

    if (requireCarsAllowed && trip.getCarsAllowed() != CarAccess.ALLOWED) {
      return false;
    }

    if (
      requireWheelchairAccessibleTrips &&
      tripTimes.getWheelchairAccessibility() != Accessibility.POSSIBLE
    ) {
      return false;
    }

    if (!includePlannedCancellations) {
      if (trip.getNetexAlteration().isCanceledOrReplaced()) {
        return false;
      }
    }

    if (!includeRealtimeCancellations) {
      if (tripTimes.isCanceled()) {
        return false;
      }
    }

    if (bannedTrips.contains(trip.getId())) {
      return false;
    }

    // Trip has to match with at least one predicate in order to be included in search. We only have
    // to this if we have mode specific filters, and not all trips on the pattern have the same
    // mode, since that's the only thing that is trip specific
    if (applyTripTimesFilters) {
      for (TransitFilter f : filters) {
        if (f.matchTripTimes(tripTimes)) {
          return true;
        }
      }
      return false;
    }

    return true;
  }

  @Override
  public BitSet filterAvailableStops(
    RoutingTripPattern tripPattern,
    BitSet boardingPossible,
    BoardAlight boardAlight
  ) {
    var result = boardingPossible;

    // if the user wants to include realtime cancellations, include cancelled stops as available
    if (includeRealtimeCancellations) {
      var pattern = tripPattern.getPattern();
      var nStops = pattern.numberOfStops();
      result = new BitSet(nStops);

      for (int i = 0; i < nStops; i++) {
        PickDrop pickDrop =
          switch (boardAlight) {
            case BOARD -> pattern.getBoardType(i);
            case ALIGHT -> pattern.getAlightType(i);
          };
        result.set(i, pickDrop.isRoutable() || pickDrop.is(PickDrop.CANCELLED));
      }
    }

    if (requireWheelchairAccessibleStops) {
      result = (BitSet) result.clone();
      // Use the and bitwise operator to add false flag to all stops that are not accessible by wheelchair
      result.and(tripPattern.getWheelchairAccessible());

      return result;
    }
    return result;
  }
}
