package org.opentripplanner.ext.updater.trip.unified.gtfs;

import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.model.command.RouteCreationInfo;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripCreationInfo;
import org.opentripplanner.gtfs.mapping.TransitModeMapper;
import org.opentripplanner.updater.trip.gtfs.model.AddedRoute;
import org.opentripplanner.updater.trip.gtfs.model.TripUpdate;

/**
 * Parses the descriptive fields a GTFS-RT added trip is created with - the trip itself and,
 * through the MFDZ extensions, the route it should run on.
 */
final class TripCreationInfoParser {

  private TripCreationInfoParser() {}

  static TripCreationInfo parse(FeedScopedId tripId, TripUpdate tripUpdate) {
    var builder = TripCreationInfo.builder(tripId);

    // Get route ID from trip update
    var routeId = tripUpdate.routeId().orElse(null);

    if (routeId != null) {
      builder.withRouteId(routeId);
    }

    tripUpdate.tripShortName().ifPresent(builder::withTripShortName);

    // Extract route creation info from MFDZ extensions
    var addedRoute = AddedRoute.ofTripDescriptor(tripUpdate);
    if (routeId != null && (addedRoute.routeUrl() != null || addedRoute.routeLongName() != null)) {
      var agencyId = addedRoute.agencyId() != null
        ? new FeedScopedId(tripId.getFeedId(), addedRoute.agencyId())
        : null;
      var mode = TransitModeMapper.mapMode(addedRoute.routeType());
      var routeCreationInfo = new RouteCreationInfo(
        addedRoute.routeLongName(),
        mode,
        null,
        null,
        addedRoute.routeUrl(),
        agencyId,
        addedRoute.routeType()
      );
      builder.withRouteCreationInfo(routeCreationInfo);
    }

    return builder.build();
  }
}
