package org.opentripplanner.perfrig;

import org.jspecify.annotations.Nullable;
import org.opentripplanner.apis.transmodel.TransmodelGraphQLRequestContext;
import org.opentripplanner.ext.empiricaldelay.EmpiricalDelayService;
import org.opentripplanner.place.NearbyPlaceFinder;
import org.opentripplanner.place.NearbyStopFinder;
import org.opentripplanner.place.api.NearbyStop;
import org.opentripplanner.routing.api.RoutingService;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.linking.LinkingContextFactory;
import org.opentripplanner.routing.services.TransitAlertService;
import org.opentripplanner.service.streetdetails.StreetDetailsService;
import org.opentripplanner.service.vehicleparking.VehicleParkingService;
import org.opentripplanner.service.vehiclerental.VehicleRentalService;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.street.service.StreetLimitationParametersService;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.transit.transfer.regular.RegularTransferRepositorySnapshot;

/** The production request context, with the routing service replaced. */
final class RigTransmodelContext implements TransmodelGraphQLRequestContext {

  private final TransmodelGraphQLRequestContext delegate;
  private final RoutingService routingService;

  RigTransmodelContext(TransmodelGraphQLRequestContext delegate, RoutingService routingService) {
    this.delegate = delegate;
    this.routingService = routingService;
  }

  @Override
  public RoutingService routingService() {
    return routingService;
  }

  @Override
  public TransitService transitService() {
    return delegate.transitService();
  }

  @Override
  public TransitAlertService transitAlertService() {
    return delegate.transitAlertService();
  }

  @Override
  @Nullable
  public EmpiricalDelayService empiricalDelayService() {
    return delegate.empiricalDelayService();
  }

  @Override
  public RouteRequest defaultRouteRequest() {
    return delegate.defaultRouteRequest();
  }

  @Override
  public VehicleRentalService vehicleRentalService() {
    return delegate.vehicleRentalService();
  }

  @Override
  public VehicleParkingService vehicleParkingService() {
    return delegate.vehicleParkingService();
  }

  @Override
  public Graph graph() {
    return delegate.graph();
  }

  @Override
  public RegularTransferRepositorySnapshot<NearbyStop, RouteRequest> regularTransfers() {
    return delegate.regularTransfers();
  }

  @Override
  public StreetDetailsService streetDetailsService() {
    return delegate.streetDetailsService();
  }

  @Override
  public LinkingContextFactory linkingContextFactory() {
    return delegate.linkingContextFactory();
  }

  @Override
  public StreetLimitationParametersService streetLimitationParametersService() {
    return delegate.streetLimitationParametersService();
  }

  @Override
  public NearbyPlaceFinder nearbyPlaceFinder() {
    return delegate.nearbyPlaceFinder();
  }

  @Override
  public NearbyStopFinder nearbyStopFinder() {
    return delegate.nearbyStopFinder();
  }
}
