package org.opentripplanner.routing.algorithm.raptoradapter.transit.request.transfercache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.RouteRequestBuilder;
import org.opentripplanner.street.model.StreetMode;
import org.opentripplanner.street.search.request.StreetSearchRequest;
import org.opentripplanner.streetadapter.StreetSearchRequestMapper;

class TransferRequestBucketerTest {

  @Test
  void walkSpeedSnapsToNearest5cm() {
    assertEquals(1.40, bucket(walkRequestWithSpeed(1.38)).walk().speed(), 0.0);
    assertEquals(1.40, bucket(walkRequestWithSpeed(1.42)).walk().speed(), 0.0);
    assertEquals(1.45, bucket(walkRequestWithSpeed(1.43)).walk().speed(), 0.0);
    // Tie rounds up.
    assertEquals(1.45, bucket(walkRequestWithSpeed(1.425)).walk().speed(), 0.0);
    // Already on the grid -> unchanged.
    assertEquals(1.30, bucket(walkRequestWithSpeed(1.30)).walk().speed(), 0.0);
  }

  @Test
  void walkBucketingSkippedForCarMode() {
    // CAR mode does not include walking, so the bucketer must leave walk speed alone
    // (1.38 would otherwise snap to 1.40).
    StreetSearchRequest input = mapToTransfer(carRequestWithWalkSpeed(1.38));
    assertEquals(input.walk().speed(), bucket(carRequestWithWalkSpeed(1.38)).walk().speed(), 0.0);
  }

  @Test
  void walkReluctanceSnapsBelow2WhereUnitsKeeps2Decimals() {
    // 1.94 -> 1.9, 1.95 -> 2.0 (tie up).
    assertEquals(1.9, bucket(walkRequestWithReluctance(1.94)).walk().reluctance(), 0.0);
    assertEquals(2.0, bucket(walkRequestWithReluctance(1.95)).walk().reluctance(), 0.0);
  }

  @Test
  void walkReluctanceSnapsToTier2() {
    // [3.0, 10.0): step 0.5. Units already rounds to 0.1, so the step-0.5 collapse
    // here is the only tier where reluctance bucketing visibly bites in production.
    assertEquals(3.0, bucket(walkRequestWithReluctance(3.2)).walk().reluctance(), 0.0);
    assertEquals(3.5, bucket(walkRequestWithReluctance(3.3)).walk().reluctance(), 0.0);
  }

  @Test
  void bikeSpeedSnapsBelow2WhereUnitsKeeps2Decimals() {
    assertEquals(1.8, bucket(bikeRequestWithSpeed(1.84)).bike().speed(), 0.0);
    assertEquals(1.9, bucket(bikeRequestWithSpeed(1.85)).bike().speed(), 0.0);
  }

  @Test
  void bikeBucketingSkippedForCarMode() {
    // CAR mode does not include biking, so the bucketer must leave bike speed alone
    // (5.05 would otherwise snap to 5.1).
    StreetSearchRequest input = mapToTransfer(carRequestWithBikeSpeed(5.05));
    assertEquals(input.bike().speed(), bucket(carRequestWithBikeSpeed(5.05)).bike().speed(), 0.0);
  }

  @Test
  void bikeReluctanceSnapsBelow2() {
    assertEquals(1.9, bucket(bikeRequestWithReluctance(1.94)).bike().reluctance(), 0.0);
    assertEquals(2.0, bucket(bikeRequestWithReluctance(1.95)).bike().reluctance(), 0.0);
  }

  @Test
  void carReluctanceSnapsBelow2() {
    assertEquals(1.9, bucket(carRequestWithReluctance(1.94)).car().reluctance(), 0.0);
    assertEquals(2.0, bucket(carRequestWithReluctance(1.95)).car().reluctance(), 0.0);
  }

  @Test
  void carBucketingSkippedForWalkMode() {
    // WALK mode does not include driving, so the bucketer must leave car reluctance alone
    // (1.94 would otherwise snap to 1.9).
    StreetSearchRequest input = mapToTransfer(walkRequestWithCarReluctance(1.94));
    assertEquals(
      input.car().reluctance(),
      bucket(walkRequestWithCarReluctance(1.94)).car().reluctance(),
      0.0
    );
  }

  @Test
  void turnReluctanceSnapsBelow2() {
    assertEquals(1.9, bucket(turnReluctanceRequest(1.94)).turnReluctance(), 0.0);
    assertEquals(2.0, bucket(turnReluctanceRequest(1.95)).turnReluctance(), 0.0);
  }

  @Test
  void turnReluctanceIsBucketedRegardlessOfMode() {
    // turnReluctance applies on every mode, so even a CAR request with no walk/bike
    // configuration sees it bucketed.
    assertEquals(1.9, bucket(carModeWithTurnReluctance(1.94)).turnReluctance(), 0.0);
  }

  // --- helpers ---

  private static StreetSearchRequest bucket(RouteRequest r) {
    return TransferRequestBucketer.bucket(mapToTransfer(r));
  }

  private static StreetSearchRequest mapToTransfer(RouteRequest r) {
    return StreetSearchRequestMapper.mapToTransferRequest(r).build();
  }

  private static RouteRequestBuilder builder() {
    return RouteRequest.of()
      .withFrom(GenericLocation.fromCoordinate(0, 0))
      .withTo(GenericLocation.fromCoordinate(1, 1));
  }

  private static RouteRequest walkRequestWithSpeed(double speed) {
    return builder()
      .withJourney(b -> b.withAllModes(StreetMode.WALK))
      .withPreferences(p -> p.withWalk(b -> b.withSpeed(speed)))
      .buildRequest();
  }

  private static RouteRequest walkRequestWithReluctance(double reluctance) {
    return builder()
      .withJourney(b -> b.withAllModes(StreetMode.WALK))
      .withPreferences(p -> p.withWalk(b -> b.withReluctance(reluctance)))
      .buildRequest();
  }

  private static RouteRequest bikeRequestWithSpeed(double speed) {
    return builder()
      .withJourney(b -> b.withAllModes(StreetMode.BIKE))
      .withPreferences(p -> p.withBike(b -> b.withSpeed(speed)))
      .buildRequest();
  }

  private static RouteRequest bikeRequestWithReluctance(double reluctance) {
    return builder()
      .withJourney(b -> b.withAllModes(StreetMode.BIKE))
      .withPreferences(p -> p.withBike(b -> b.withReluctance(reluctance)))
      .buildRequest();
  }

  private static RouteRequest carRequestWithReluctance(double reluctance) {
    return builder()
      .withJourney(b -> b.withAllModes(StreetMode.CAR))
      .withPreferences(p -> p.withCar(b -> b.withReluctance(reluctance)))
      .buildRequest();
  }

  private static RouteRequest carRequestWithWalkSpeed(double speed) {
    return builder()
      .withJourney(b -> b.withAllModes(StreetMode.CAR))
      .withPreferences(p -> p.withWalk(b -> b.withSpeed(speed)))
      .buildRequest();
  }

  private static RouteRequest carRequestWithBikeSpeed(double speed) {
    return builder()
      .withJourney(b -> b.withAllModes(StreetMode.CAR))
      .withPreferences(p -> p.withBike(b -> b.withSpeed(speed)))
      .buildRequest();
  }

  private static RouteRequest walkRequestWithCarReluctance(double reluctance) {
    return builder()
      .withJourney(b -> b.withAllModes(StreetMode.WALK))
      .withPreferences(p -> p.withCar(b -> b.withReluctance(reluctance)))
      .buildRequest();
  }

  private static RouteRequest turnReluctanceRequest(double turnReluctance) {
    return builder()
      .withJourney(b -> b.withAllModes(StreetMode.WALK))
      .withPreferences(p -> p.withStreet(b -> b.withTurnReluctance(turnReluctance)))
      .buildRequest();
  }

  private static RouteRequest carModeWithTurnReluctance(double turnReluctance) {
    return builder()
      .withJourney(b -> b.withAllModes(StreetMode.CAR))
      .withPreferences(p -> p.withStreet(b -> b.withTurnReluctance(turnReluctance)))
      .buildRequest();
  }
}
