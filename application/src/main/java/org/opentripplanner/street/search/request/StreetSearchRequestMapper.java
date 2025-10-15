package org.opentripplanner.street.search.request;

import java.time.Instant;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.preference.RoutingPreferences;

public class StreetSearchRequestMapper {

  public static StreetSearchRequestBuilder mapModeSpecificRental(RouteRequest request) {
    var time = request.dateTime() == null ? RouteRequest.normalizeNow() : request.dateTime();
    return StreetSearchRequest.of()
      .withStartTime(time)
      .withWheelchair(request.journey().wheelchair())
      .withFrom(request.from())
      .withTo(request.to())
      .withGeoidElevation(request.preferences().system().geoidElevation())
      .withTurnReluctance(request.preferences().street().turnReluctance());
  }

  private static RentalRequest mapRental(RoutingPreferences preferences) {
    return RentalRequest.of().build();
  }

  public static StreetSearchRequestBuilder mapToTransferRequest(RouteRequest request) {
    return StreetSearchRequest.of()
      .withStartTime(Instant.ofEpochSecond(0))
      .withWheelchair(request.journey().wheelchair())
      .withMode(request.journey().transfer().mode());
  }
}
