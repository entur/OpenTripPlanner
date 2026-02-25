package org.opentripplanner.updater.trip.handlers;

import javax.annotation.Nullable;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.organization.Agency;
import org.opentripplanner.transit.model.organization.Operator;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.TripCreationInfo;
import org.rutebanken.netex.model.BusSubmodeEnumeration;
import org.rutebanken.netex.model.RailSubmodeEnumeration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SIRI-specific route creation strategy.
 * Reproduces the agency resolution, route creation, and submode derivation logic
 * from the old {@code AddedTripBuilder}.
 */
public class SiriRouteCreationStrategy implements RouteCreationStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(SiriRouteCreationStrategy.class);

  private final String feedId;

  public SiriRouteCreationStrategy(String feedId) {
    this.feedId = feedId;
  }

  @Override
  public Result<RouteResolution, UpdateError> resolveOrCreateRoute(
    TripCreationInfo tripCreationInfo,
    TransitEditorService transitService
  ) {
    FeedScopedId routeId = tripCreationInfo.routeId();

    // Try to find existing route
    if (routeId != null) {
      Route existingRoute = transitService.getRoute(routeId);
      if (existingRoute != null) {
        LOG.debug("ADD_TRIP: Using existing route {}", routeId);
        return Result.success(new RouteResolution(existingRoute, false));
      }
    }

    // Route not found - need to create one
    // First resolve operator
    Operator operator = resolveOperator(tripCreationInfo, transitService);

    // Resolve agency using SIRI algorithm:
    // 1. Find any route operated by the same operator
    // 2. Fall back to replaced route's agency
    Agency agency = resolveAgency(operator, tripCreationInfo, transitService);
    if (agency == null) {
      return Result.failure(
        new UpdateError(
          tripCreationInfo.tripId(),
          UpdateError.UpdateErrorType.CANNOT_RESOLVE_AGENCY
        )
      );
    }

    // Create route ID (use routeId from tripCreationInfo, or tripId as fallback)
    FeedScopedId effectiveRouteId = routeId != null ? routeId : tripCreationInfo.tripId();

    // Resolve submode based on replaced route
    String submode = resolveTransitSubMode(tripCreationInfo, transitService);

    // Create the route
    var builder = Route.of(effectiveRouteId);
    builder.withAgency(agency);

    if (tripCreationInfo.shortName() != null) {
      builder.withShortName(tripCreationInfo.shortName());
    }
    // longName is required as fallback when shortName is null
    builder.withLongName(NonLocalizedString.ofNullable(effectiveRouteId.getId()));

    TransitMode mode = tripCreationInfo.mode() != null ? tripCreationInfo.mode() : TransitMode.BUS;
    builder.withMode(mode);

    if (submode != null) {
      builder.withNetexSubmode(submode);
    }

    if (operator != null) {
      builder.withOperator(operator);
    }

    Route route = builder.build();
    LOG.debug("ADD_TRIP: Created new SIRI route {}", effectiveRouteId);
    return Result.success(new RouteResolution(route, true));
  }

  @Nullable
  private Operator resolveOperator(
    TripCreationInfo tripCreationInfo,
    TransitEditorService transitService
  ) {
    if (tripCreationInfo.operatorId() != null) {
      return transitService.getOperator(tripCreationInfo.operatorId());
    }
    return null;
  }

  /**
   * Resolve agency using the SIRI algorithm from AddedTripBuilder:
   * 1. Scan all routes for one with the same operator, use its agency
   * 2. Fall back to the replaced route's agency
   */
  @Nullable
  private Agency resolveAgency(
    @Nullable Operator operator,
    TripCreationInfo tripCreationInfo,
    TransitEditorService transitService
  ) {
    // Try to find agency via operator's routes
    if (operator != null) {
      var agencyFromOperator = transitService
        .listRoutes()
        .stream()
        .filter(r -> r != null && r.getOperator() != null && r.getOperator().equals(operator))
        .findFirst()
        .map(Route::getAgency)
        .orElse(null);
      if (agencyFromOperator != null) {
        return agencyFromOperator;
      }
    }

    // Fall back to replaced route's agency
    if (tripCreationInfo.replacedRouteId() != null) {
      Route replacedRoute = transitService.getRoute(tripCreationInfo.replacedRouteId());
      if (replacedRoute != null) {
        return replacedRoute.getAgency();
      }
    }

    return null;
  }

  /**
   * Resolve submode based on the trip's mode and the replaced route's mode.
   * Matches the old AddedTripBuilder.resolveTransitSubMode() logic.
   */
  @Nullable
  private String resolveTransitSubMode(
    TripCreationInfo tripCreationInfo,
    TransitEditorService transitService
  ) {
    if (tripCreationInfo.replacedRouteId() == null) {
      return tripCreationInfo.submode();
    }

    Route replacedRoute = transitService.getRoute(tripCreationInfo.replacedRouteId());
    if (replacedRoute == null || replacedRoute.getMode() != TransitMode.RAIL) {
      return tripCreationInfo.submode();
    }

    TransitMode tripMode = tripCreationInfo.mode();
    if (tripMode == null) {
      return tripCreationInfo.submode();
    }

    return switch (tripMode) {
      case RAIL -> RailSubmodeEnumeration.REPLACEMENT_RAIL_SERVICE.value();
      case BUS -> BusSubmodeEnumeration.RAIL_REPLACEMENT_BUS.value();
      default -> tripCreationInfo.submode();
    };
  }
}
