package org.opentripplanner.ext.updater.trip.unified.gtfs;

import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.factory.RouteCreationStrategy;
import org.opentripplanner.ext.updater.trip.unified.model.command.RouteCreationInfo;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripCreationInfo;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.organization.Agency;
import org.opentripplanner.transit.model.organization.Operator;
import org.opentripplanner.transit.service.TransitEditorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GTFS-RT-specific route creation strategy.
 * Reproduces the route lookup, creation, and fallback agency logic from the old {@code RouteFactory}.
 */
public class GtfsRtRouteCreationStrategy implements RouteCreationStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(GtfsRtRouteCreationStrategy.class);

  private final String feedId;

  public GtfsRtRouteCreationStrategy(String feedId) {
    this.feedId = Objects.requireNonNull(feedId);
  }

  /**
   * GTFS-RT names no operator for an added trip, so the resolved operator is always null here and
   * the created route carries none. It classifies an added trip no further than its route either,
   * so no submode is derived.
   */
  @Override
  public RouteResolution resolveOrCreateRoute(
    TripCreationInfo tripCreationInfo,
    @Nullable Operator operator,
    TransitEditorService transitService
  ) {
    FeedScopedId tripId = tripCreationInfo.tripId();
    FeedScopedId routeId = tripCreationInfo.routeId();

    // Try to find existing route
    if (routeId != null) {
      Route existingRoute = transitService.getRoute(routeId);
      if (existingRoute != null) {
        LOG.debug("ADD_TRIP: Using existing route {}", routeId);
        return new RouteResolution(existingRoute, false, null);
      }

      // Route not found - create using routeCreationInfo if available
      if (tripCreationInfo.routeCreationInfo() != null) {
        return new RouteResolution(
          createRouteWithInfo(
            routeId,
            tripId,
            tripCreationInfo.routeCreationInfo(),
            transitService
          ),
          true,
          null
        );
      }

      // No routeCreationInfo - create fallback route with routeId
      return new RouteResolution(createFallbackRoute(routeId, transitService), true, null);
    }

    // No route ID at all - create fallback route using trip ID
    return new RouteResolution(createFallbackRoute(tripId, transitService), true, null);
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
