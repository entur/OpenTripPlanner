package org.opentripplanner.updater.trip.handlers;

import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.organization.Agency;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.RouteCreationInfo;
import org.opentripplanner.updater.trip.model.TripCreationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GTFS-RT-specific route creation strategy.
 * Reproduces the route lookup, creation, and fallback agency logic from the old {@code RouteFactory}.
 */
public class GtfsRtRouteCreationStrategy implements RouteCreationStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(GtfsRtRouteCreationStrategy.class);

  private final String feedId;

  @Nullable
  private final Function<FeedScopedId, Route> routeCache;

  public GtfsRtRouteCreationStrategy(
    String feedId,
    @Nullable Function<FeedScopedId, Route> routeCache
  ) {
    this.feedId = Objects.requireNonNull(feedId);
    this.routeCache = routeCache;
  }

  @Override
  public Result<Route, UpdateError> resolveOrCreateRoute(
    TripCreationInfo tripCreationInfo,
    TransitEditorService transitService
  ) {
    FeedScopedId tripId = tripCreationInfo.tripId();
    FeedScopedId routeId = tripCreationInfo.routeId();

    // Try to find existing route
    if (routeId != null) {
      Route existingRoute = findRoute(routeId, transitService);
      if (existingRoute != null) {
        LOG.debug("ADD_TRIP: Using existing route {}", routeId);
        return Result.success(existingRoute);
      }

      // Route not found - create using routeCreationInfo if available
      if (tripCreationInfo.routeCreationInfo() != null) {
        return Result.success(
          createRouteWithInfo(routeId, tripId, tripCreationInfo.routeCreationInfo(), transitService)
        );
      }

      // No routeCreationInfo - create fallback route with routeId
      return Result.success(createFallbackRoute(routeId, transitService));
    }

    // No route ID at all - create fallback route using trip ID
    return Result.success(createFallbackRoute(tripId, transitService));
  }

  @Nullable
  private Route findRoute(FeedScopedId routeId, TransitEditorService transitService) {
    if (routeCache != null) {
      Route cachedRoute = routeCache.apply(routeId);
      if (cachedRoute != null) {
        return cachedRoute;
      }
    }
    return transitService.getRoute(routeId);
  }

  /**
   * Create a route with full RouteCreationInfo metadata.
   * Matches the old RouteFactory.createRoute() with addedRouteExtension data.
   */
  private Route createRouteWithInfo(
    FeedScopedId routeId,
    FeedScopedId tripId,
    RouteCreationInfo routeCreationInfo,
    TransitEditorService transitService
  ) {
    var builder = Route.of(routeId);

    // Agency resolution: try from routeCreationInfo, fall back to dummy
    Agency agency = null;
    if (routeCreationInfo.agencyId() != null) {
      agency = transitService.findAgency(routeCreationInfo.agencyId()).orElse(null);
    }
    if (agency == null) {
      agency = fallbackAgency(transitService);
    }
    builder.withAgency(agency);

    // Set gtfsType
    if (routeCreationInfo.gtfsType() != null) {
      builder.withGtfsType(routeCreationInfo.gtfsType());
    }

    // Set mode
    if (routeCreationInfo.mode() != null) {
      builder.withMode(routeCreationInfo.mode());
    }

    // Set name
    I18NString name = NonLocalizedString.ofNullable(routeCreationInfo.routeName());
    if (name == null) {
      name = new NonLocalizedString(tripId.toString());
    }
    builder.withLongName(name);

    // Set URL
    if (routeCreationInfo.url() != null) {
      builder.withUrl(routeCreationInfo.url());
    }

    Route route = builder.build();
    LOG.debug("ADD_TRIP: Created new GTFS-RT route {}", routeId);
    return route;
  }

  /**
   * Create a fallback route with minimal information.
   * Matches the old RouteFactory fallback path.
   */
  private Route createFallbackRoute(FeedScopedId id, TransitEditorService transitService) {
    I18NString longName = NonLocalizedString.ofNullable(id.getId());
    Route route = Route.of(id)
      .withAgency(fallbackAgency(transitService))
      .withGtfsType(3)
      .withMode(TransitMode.BUS)
      .withLongName(longName)
      .build();
    LOG.debug("ADD_TRIP: Created fallback GTFS-RT route {}", id);
    return route;
  }

  /**
   * Create a synthetic fallback agency.
   * Matches the old RouteFactory.fallbackAgency().
   */
  private Agency fallbackAgency(TransitEditorService transitService) {
    return Agency.of(new FeedScopedId(feedId, "autogenerated-gtfs-rt-added-route"))
      .withName("Agency automatically added by GTFS-RT update")
      .withTimezone(transitService.getTimeZone().toString())
      .build();
  }
}
