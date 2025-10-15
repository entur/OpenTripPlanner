package org.opentripplanner.street.search.request;

import java.time.Instant;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.preference.BikePreferences;
import org.opentripplanner.routing.api.request.preference.CarPreferences;
import org.opentripplanner.routing.api.request.preference.ScooterPreferences;
import org.opentripplanner.routing.api.request.preference.VehicleRentalPreferences;
import org.opentripplanner.routing.api.request.preference.VehicleWalkingPreferences;

public class StreetSearchRequestMapper {

  public static StreetSearchRequestBuilder mapInternal(RouteRequest request) {
    var time = request.dateTime() == null ? RouteRequest.normalizeNow() : request.dateTime();
    return StreetSearchRequest.of()
      .withStartTime(time)
      .withArriveBy(request.arriveBy())
      .withWheelchair(request.journey().wheelchair())
      .withFrom(request.from())
      .withTo(request.to())
      .withGeoidElevation(request.preferences().system().geoidElevation())
      .withTurnReluctance(request.preferences().street().turnReluctance())
      .withBike(mapBike(request.preferences().bike()))
      .withCar(mapCar(request.preferences().car()))
      .withScooter(mapScooter(request.preferences().scooter()));
  }

  private static ScooterRequest mapScooter(ScooterPreferences scooter) {
    return ScooterRequest.of()
      .withSpeed(scooter.speed())
      .withReluctance(scooter.reluctance())
      .withRental(b -> mapRental(scooter.rental()))
      .withOptimizeType(scooter.optimizeType())
      .withOptimizeTriangle(b ->
        b
          .withTime(scooter.optimizeTriangle().time())
          .withSlope(scooter.optimizeTriangle().slope())
          .withSafety(scooter.optimizeTriangle().safety())
      )
      .build();
  }

  private static CarRequest mapCar(CarPreferences car) {
    return CarRequest.of()
      .withReluctance(car.reluctance())
      .withPickupTime(car.pickupTime())
      .withPickupCost(car.pickupCost().toSeconds())
      .withAccelerationSpeed(car.accelerationSpeed())
      .withDecelerationSpeed(car.decelerationSpeed())
      .withRental(b -> mapRental(car.rental()))
      .build();
  }

  private static BikeRequest mapBike(BikePreferences preferences) {
    return BikeRequest.of()
      .withSpeed(preferences.speed())
      .withReluctance(preferences.reluctance())
      .withRental(b -> mapRental(preferences.rental()))
      .withOptimizeType(preferences.optimizeType())
      .withBoardCost(preferences.boardCost())
      .withWalking(map(preferences.walking()))
      .withOptimizeType(preferences.optimizeType())
      .withOptimizeTriangle(b ->
        b
          .withTime(preferences.optimizeTriangle().time())
          .withSlope(preferences.optimizeTriangle().slope())
          .withSafety(preferences.optimizeTriangle().safety())
      )
      .build();
  }

  private static VehicleWalkingRequest map(VehicleWalkingPreferences walking) {
    return VehicleWalkingRequest.of()
      .withSpeed(walking.speed())
      .withReluctance(walking.reluctance())
      .withStairsReluctance(walking.stairsReluctance())
      .withMountDismountTime(walking.mountDismountTime())
      .withMountDismountCost(walking.mountDismountCost().toSeconds())
      .build();
  }

  private static RentalRequest mapRental(VehicleRentalPreferences rental) {
    return RentalRequest.of()
      .withPickupTime(rental.pickupTime())
      .withDropOffTime(rental.dropOffTime())
      .withDropOffCost(rental.dropOffCost().toSeconds())
      .withBannedNetworks(rental.bannedNetworks())
      .withAllowedNetworks(rental.allowedNetworks())
      .withUseAvailabilityInformation(rental.useAvailabilityInformation())
      .withAllowArrivingInRentedVehicleAtDestination(
        rental.allowArrivingInRentedVehicleAtDestination()
      )
      .build();
  }

  public static StreetSearchRequestBuilder mapToTransferRequest(RouteRequest request) {
    return StreetSearchRequest.of()
      .withStartTime(Instant.ofEpochSecond(0))
      .withWheelchair(request.journey().wheelchair())
      .withMode(request.journey().transfer().mode());
  }
}
