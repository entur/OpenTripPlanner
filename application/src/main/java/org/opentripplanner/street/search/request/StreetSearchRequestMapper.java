package org.opentripplanner.street.search.request;

import java.time.Instant;
import java.util.List;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.preference.AccessibilityPreferences;
import org.opentripplanner.routing.api.request.preference.BikePreferences;
import org.opentripplanner.routing.api.request.preference.CarPreferences;
import org.opentripplanner.routing.api.request.preference.RoutingPreferences;
import org.opentripplanner.routing.api.request.preference.ScooterPreferences;
import org.opentripplanner.routing.api.request.preference.VehicleParkingPreferences;
import org.opentripplanner.routing.api.request.preference.VehicleRentalPreferences;
import org.opentripplanner.routing.api.request.preference.VehicleWalkingPreferences;
import org.opentripplanner.routing.api.request.preference.WalkPreferences;
import org.opentripplanner.routing.api.request.preference.WheelchairPreferences;
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
      .withFrom(request.from())
      .withTo(request.to())
      .withWheelchairEnabled(request.journey().wheelchair())
      .withGeoidElevation(preferences.system().geoidElevation())
      .withTurnReluctance(preferences.street().turnReluctance())
      .withWheelchair(mapWheelchair(request.preferences().wheelchair()))
      .withWalk(mapWalk(preferences.walk()))
      .withBike(mapBike(preferences.bike()))
      .withCar(mapCar(preferences.car()))
      .withScooter(mapScooter(preferences.scooter()));
  }

  public static StreetSearchRequestBuilder mapToTransferRequest(RouteRequest request) {
    return mapInternal(request)
      .withFrom(null)
      .withTo(null)
      .withStartTime(Instant.ofEpochSecond(0))
      .withMode(request.journey().transfer().mode());
  }

  // private methods

  private static WheelchairRequest mapWheelchair(WheelchairPreferences wheelchair) {
    return WheelchairRequest.of()
      .withStop(mapAccessibility(wheelchair.stop()))
      .withElevator(mapAccessibility(wheelchair.elevator()))
      .withMaxSlope(wheelchair.maxSlope())
      .withSlopeExceededReluctance(wheelchair.slopeExceededReluctance())
      .withStairsReluctance(wheelchair.stairsReluctance())
      .withInaccessibleStreetReluctance(wheelchair.inaccessibleStreetReluctance())
      .build();
  }

  private static AccessibilityRequest mapAccessibility(AccessibilityPreferences stop) {
    var b = AccessibilityRequest.of()
      .withInaccessibleCost(stop.inaccessibleCost())
      .withUnknownCost(stop.unknownCost());
    if (stop.onlyConsiderAccessible()) {
      b.withAccessibleOnly();
    }
    return b.build();
  }

  private static WalkRequest mapWalk(WalkPreferences pref) {
    return WalkRequest.of()
      .withSpeed(pref.speed())
      .withReluctance(pref.reluctance())
      .withBoardCost(pref.boardCost())
      .withStairsReluctance(pref.stairsReluctance())
      .withStairsTimeFactor(pref.stairsTimeFactor())
      .withSafetyFactor(pref.safetyFactor())
      .build();
  }

  private static BikeRequest mapBike(BikePreferences preferences) {
    return BikeRequest.of()
      .withReluctance(preferences.reluctance())
      .withSpeed(preferences.speed())
      .withBoardCost(preferences.boardCost())
      .withParking(mapParking(preferences.parking()))
      .withRental(mapRental(preferences.rental()))
      .withOptimizeType(preferences.optimizeType())
      .withOptimizeTriangle(preferences.optimizeTriangle())
      .withWalking(mapVehicleWalking(preferences.walking()))
      .build();
  }

  private static CarRequest mapCar(CarPreferences car) {
    return CarRequest.of()
      .withReluctance(car.reluctance())
      .withBoardCost(car.boardCost())
      .withParking(mapParking(car.parking()))
      .withRental(mapRental(car.rental()))
      .withPickupTime(car.pickupTime())
      .withPickupCost(car.pickupCost().toSeconds())
      .withAccelerationSpeed(car.accelerationSpeed())
      .withDecelerationSpeed(car.decelerationSpeed())
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
      .withUseAvailabilityInformation(rental.useAvailabilityInformation())
      .withAllowArrivingInRentedVehicleAtDestination(
        rental.allowArrivingInRentedVehicleAtDestination()
      )
      .withArrivingInRentalVehicleAtDestinationCost(
        rental.arrivingInRentalVehicleAtDestinationCost().toSeconds()
      )
      .withBannedNetworks(rental.bannedNetworks())
      .withAllowedNetworks(rental.allowedNetworks())
      .build();
  }

  private static ParkingRequest mapParking(VehicleParkingPreferences pref) {
    return ParkingRequest.of()
      .withUnpreferredTagCost(pref.unpreferredVehicleParkingTagCost())
      .withFilter(mapParkingFilter(pref.filter()))
      .withPreferred(mapParkingFilter(pref.preferred()))
      .withTime(pref.time())
      .withCost(pref.cost().toSeconds())
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
}
