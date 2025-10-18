package org.opentripplanner.street.search.request;

import java.time.Instant;
import java.util.List;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.preference.BikePreferences;
import org.opentripplanner.routing.api.request.preference.CarPreferences;
import org.opentripplanner.routing.api.request.preference.RoutingPreferences;
import org.opentripplanner.routing.api.request.preference.ScooterPreferences;
import org.opentripplanner.routing.api.request.preference.VehicleParkingPreferences;
import org.opentripplanner.routing.api.request.preference.VehicleRentalPreferences;
import org.opentripplanner.routing.api.request.preference.VehicleWalkingPreferences;
import org.opentripplanner.routing.api.request.preference.WalkPreferences;
import org.opentripplanner.routing.api.request.preference.filter.VehicleParkingFilter;
import org.opentripplanner.routing.api.request.preference.filter.VehicleParkingSelect;
import org.opentripplanner.street.search.request.filter.ParkingFilter;
import org.opentripplanner.street.search.request.filter.ParkingSelect;
import org.opentripplanner.street.search.request.filter.ParkingSelect.TagsSelect;

public class StreetSearchRequestMapper {

  public static StreetSearchRequestBuilder mapInternal(RouteRequest request) {
    var time = request.dateTime() == null ? RouteRequest.normalizeNow() : request.dateTime();
    final RoutingPreferences preferences = request.preferences();
    return StreetSearchRequest.of()
      .withStartTime(time)
      .withArriveBy(request.arriveBy())
      .withWheelchair(request.journey().wheelchair())
      .withFrom(request.from())
      .withTo(request.to())
      .withGeoidElevation(preferences.system().geoidElevation())
      .withTurnReluctance(preferences.street().turnReluctance())
      .withWalk(mapWalk(preferences.walk()))
      .withBike(mapBike(preferences.bike()))
      .withCar(mapCar(preferences.car()))
      .withScooter(mapScooter(preferences.scooter()));
  }

  private static WalkRequest mapWalk(WalkPreferences pref) {
    return WalkRequest.of()
      .withSpeed(pref.speed())
      .withReluctance(pref.reluctance())
      .withStairsReluctance(pref.stairsReluctance())
      .withStairsTimeFactor(pref.stairsTimeFactor())
      .withBoardCost(pref.boardCost())
      .withSafetyFactor(pref.safetyFactor())
      .build();
  }

  private static ScooterRequest mapScooter(ScooterPreferences scooter) {
    return ScooterRequest.of()
      .withSpeed(scooter.speed())
      .withReluctance(scooter.reluctance())
      .withRental(mapRental(scooter.rental()))
      .withOptimizeType(scooter.optimizeType())
      .withOptimizeTriangle(scooter.optimizeTriangle())
      .build();
  }

  private static CarRequest mapCar(CarPreferences car) {
    return CarRequest.of()
      .withReluctance(car.reluctance())
      .withPickupTime(car.pickupTime())
      .withPickupCost(car.pickupCost().toSeconds())
      .withAccelerationSpeed(car.accelerationSpeed())
      .withDecelerationSpeed(car.decelerationSpeed())
      .withRental(mapRental(car.rental()))
      .withParking(mapParking(car.parking()))
      .build();
  }

  private static ParkingRequest mapParking(VehicleParkingPreferences pref) {
    return ParkingRequest.of()
      .withCost(pref.cost().toSeconds())
      .withTime(pref.time())
      .withUnpreferredVehicleParkingTagCost(pref.unpreferredVehicleParkingTagCost())
      .withFilter(mapParkingFilter(pref.filter()))
      .withPreferred(mapParkingFilter(pref.preferred()))
      .build();
  }

  private static ParkingFilter mapParkingFilter(VehicleParkingFilter filter) {
    return new ParkingFilter(mapTagSelect(filter.not()), mapTagSelect(filter.select()));
  }

  private static List<ParkingSelect> mapTagSelect(List<VehicleParkingSelect> selects) {
    return selects
      .stream()
      .map(s -> new TagsSelect(s.tags()))
      .map(ParkingSelect.class::cast)
      .toList();
  }

  private static BikeRequest mapBike(BikePreferences preferences) {
    return BikeRequest.of()
      .withReluctance(preferences.reluctance())
      .withSpeed(preferences.speed())
      .withRental(mapRental(preferences.rental()))
      .withBoardCost(preferences.boardCost())
      .withWalking(mapVehicleWalking(preferences.walking()))
      .withOptimizeType(preferences.optimizeType())
      .withOptimizeTriangle(preferences.optimizeTriangle())
      .build();
  }

  private static VehicleWalkingRequest mapVehicleWalking(VehicleWalkingPreferences walking) {
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
      .withPickupCost(rental.pickupCost().toSeconds())
      .withDropOffTime(rental.dropOffTime())
      .withDropOffCost(rental.dropOffCost().toSeconds())
      .withBannedNetworks(rental.bannedNetworks())
      .withAllowedNetworks(rental.allowedNetworks())
      .withUseAvailabilityInformation(rental.useAvailabilityInformation())
      .withAllowArrivingInRentedVehicleAtDestination(
        rental.allowArrivingInRentedVehicleAtDestination()
      )
      .withArrivingInRentalVehicleAtDestinationCost(
        rental.arrivingInRentalVehicleAtDestinationCost().toSeconds()
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
