package org.opentripplanner.street.search.request;

import java.time.Instant;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.preference.RoutingPreferences;
import org.opentripplanner.routing.api.request.preference.VehicleRentalPreferences;

public class StreetSearchRequestMapper {

  public static StreetSearchRequestBuilder mapModeSpecificRental(RouteRequest request) {
    var time = request.dateTime() == null ? RouteRequest.normalizeNow() : request.dateTime();
    return StreetSearchRequest.of()
      .withStartTime(time)
      .withWheelchair(request.journey().wheelchair())
      .withFrom(request.from())
      .withTo(request.to())
      .withGeoidElevation(request.preferences().system().geoidElevation())
      .withTurnReluctance(request.preferences().street().turnReluctance())
      .withRental(mapRental(request.preferences()))
      ;
  }

  private static RentalRequest mapRental(RoutingPreferences preferences) {
    return new RentalRequest(
      mapModeSpecificRental(preferences.bike().rental()),
      mapModeSpecificRental(preferences.car().rental()),
      mapModeSpecificRental(preferences.scooter().rental())
    );
  }

  private static ModeSpecificRentalRequest mapModeSpecificRental(VehicleRentalPreferences rental) {
    return new ModeSpecificRentalRequest(rental.pickupCost(), rental.dropOffCost(), rental.pickupTime(), rental.dropOffTime(), rental.allowedNetworks(), rental.bannedNetworks());
  }

  public static StreetSearchRequestBuilder mapToTransferRequest(RouteRequest request) {
    return StreetSearchRequest.of()
      .withStartTime(Instant.ofEpochSecond(0))
      .withWheelchair(request.journey().wheelchair())
      .withMode(request.journey().transfer().mode());
  }
}
