package org.opentripplanner.perfrig;

import java.util.ArrayList;
import java.util.List;
import org.opentripplanner.routing.api.RoutingService;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.RouteViaRequest;
import org.opentripplanner.routing.api.response.RoutingResponse;
import org.opentripplanner.routing.api.response.ViaRoutingResponse;

/** Delegates to the routing service and keeps every request and response. */
final class RecordingRoutingService implements RoutingService {

  record Call(RouteRequest request, RoutingResponse response) {}

  private final RoutingService delegate;
  private final List<Call> calls = new ArrayList<>();

  RecordingRoutingService(RoutingService delegate) {
    this.delegate = delegate;
  }

  List<Call> calls() {
    return calls;
  }

  @Override
  public RoutingResponse route(RouteRequest request) {
    var response = delegate.route(request);
    calls.add(new Call(request, response));
    return response;
  }

  @Override
  @SuppressWarnings("deprecation")
  public ViaRoutingResponse route(RouteViaRequest request) {
    throw new UnsupportedOperationException("The rig does not record via-routing requests");
  }
}
