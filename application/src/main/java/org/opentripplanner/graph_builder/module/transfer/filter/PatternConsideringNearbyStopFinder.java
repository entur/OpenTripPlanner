package org.opentripplanner.graph_builder.module.transfer.filter;

import java.util.List;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.graph_builder.module.nearbystops.NearbyStopFinder;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.request.StreetRequest;
import org.opentripplanner.routing.graphfinder.NearbyStop;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.transit.service.TransitService;

public class PatternConsideringNearbyStopFinder implements NearbyStopFinder {

  private final NearbyStopFilter filter;

  private final NearbyStopFinder delegateNearbyStopFinder;

  public PatternConsideringNearbyStopFinder(
    TransitService transitService,
    NearbyStopFinder delegateNearbyStopFinder
  ) {
    var builder = CompositNearbyStopFilter.of().add(new PatternNearbyStopFilter(transitService));

    if (OTPFeature.FlexRouting.isOn()) {
      builder.add(new FlexTripNearbyStopFilter(transitService));
    }
    this.filter = builder.build();
    this.delegateNearbyStopFinder = delegateNearbyStopFinder;
  }

  @Override
  public List<NearbyStop> findNearbyStops(
    Vertex vertex,
    RouteRequest routingRequest,
    StreetRequest streetRequest,
    boolean reverseDirection
  ) {
    // fetch nearby stops via the street network or using straight-line distance.
    var nearbyStops = delegateNearbyStopFinder.findNearbyStops(
      vertex,
      routingRequest,
      streetRequest,
      reverseDirection
    );
    var result = filter.filterToStops(nearbyStops, reverseDirection);
    return List.copyOf(result);
  }
}
