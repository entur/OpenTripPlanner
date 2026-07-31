package org.opentripplanner.ext.updater.trip.unified.siri;

import javax.annotation.Nullable;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.factory.RouteCreationStrategy;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripCreationInfo;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.organization.Agency;
import org.opentripplanner.transit.model.organization.Operator;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
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

  @Override
  public RouteResolution resolveOrCreateRoute(
    TripCreationInfo tripCreationInfo,
    @Nullable Operator operator,
    TransitEditorService transitService
  ) {
    FeedScopedId routeId = tripCreationInfo.routeId();

    // The submode is derived whether or not a route has to be created: it classifies the trip.
    String submode = resolveTransitSubMode(tripCreationInfo, transitService);

    // Try to find existing route
    if (routeId != null) {
      Route existingRoute = transitService.getRoute(routeId);
      if (existingRoute != null) {
        LOG.debug("ADD_TRIP: Using existing route {}", routeId);
        return new RouteResolution(existingRoute, false, submode);
      }
    }

    // Resolve agency using SIRI algorithm:
    // 1. Find any route operated by the same operator
    // 2. Fall back to replaced route's agency
    Agency agency = resolveAgency(operator, tripCreationInfo, transitService);
    if (agency == null) {
      throw UpdateException.of(tripCreationInfo.tripId(), UpdateErrorType.CANNOT_RESOLVE_AGENCY);
    }

    // Create route ID (use routeId from tripCreationInfo, or tripId as fallback)
    FeedScopedId effectiveRouteId = routeId != null ? routeId : tripCreationInfo.tripId();

    // Create the route
    var builder = Route.of(effectiveRouteId);
    builder.withAgency(agency);

    // The line the created trip runs on is named by the name the message publishes for it.
    if (tripCreationInfo.publishedLineName() != null) {
      builder.withShortName(tripCreationInfo.publishedLineName());
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
    return new RouteResolution(route, true, submode);
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
   * The submode of an extra journey is a rail-replacement classification: a journey standing in for
   * a rail line runs a replacement service, whatever its own mode says. Nothing else classifies it,
   * so every other journey gets no submode of its own and inherits the one of its line.
   * <p>
   * The line the journey is classified against is the one its ExternalLineRef names, or - when it
   * names none - the line the journey itself runs on, as legacy
   * {@code AddedTripBuilder} resolves it ({@code externalLineRef().orElse(lineRef)}).
   */
  @Nullable
  private String resolveTransitSubMode(
    TripCreationInfo tripCreationInfo,
    TransitEditorService transitService
  ) {
    var classifyingRouteId = tripCreationInfo.replacedRouteId() != null
      ? tripCreationInfo.replacedRouteId()
      : tripCreationInfo.routeId();
    if (classifyingRouteId == null) {
      return null;
    }

    Route classifyingRoute = transitService.getRoute(classifyingRouteId);
    if (classifyingRoute == null || classifyingRoute.getMode() != TransitMode.RAIL) {
      return null;
    }

    TransitMode tripMode = tripCreationInfo.mode();
    if (tripMode == null) {
      return null;
    }

    return switch (tripMode) {
      case RAIL -> RailSubmodeEnumeration.REPLACEMENT_RAIL_SERVICE.value();
      case BUS -> BusSubmodeEnumeration.RAIL_REPLACEMENT_BUS.value();
      default -> null;
    };
  }
}
