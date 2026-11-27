package org.opentripplanner.routing.algorithm.raptoradapter.router.street;

import java.util.List;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.linking.LinkingContext;
import org.opentripplanner.service.vehiclerental.VehicleRentalService;
import org.opentripplanner.street.model.path.StreetPath;
import org.opentripplanner.street.service.StreetLimitationParametersService;

/**
 * perf-rig: the direct street search alone, as DirectStreetRouter does it. In this package
 * because GraphPathFinder is package private.
 */
public final class RigStreetSearch {

  private RigStreetSearch() {}

  /** @throws org.opentripplanner.routing.error.PathNotFoundException if there is no path */
  public static List<StreetPath> direct(
    RouteRequest request,
    LinkingContext linkingContext,
    StreetLimitationParametersService streetLimitationParametersService,
    VehicleRentalService vehicleRentalService
  ) {
    return new GraphPathFinder(
      List.of(),
      streetLimitationParametersService,
      vehicleRentalService
    ).find(request, linkingContext);
  }
}
