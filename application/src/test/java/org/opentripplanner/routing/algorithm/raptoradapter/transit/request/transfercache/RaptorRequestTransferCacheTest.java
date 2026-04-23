package org.opentripplanner.routing.algorithm.raptoradapter.transit.request.transfercache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.RouteRequestBuilder;
import org.opentripplanner.street.model.StreetMode;
import org.opentripplanner.transfer.regular.model.Transfer;

public class RaptorRequestTransferCacheTest {

  @Test
  public void testRaptorRequestTransferCacheKeyWithWheelchair() {
    List<List<Transfer>> list = List.of();

    RouteRequest base = builder()
      .withJourney(b -> b.withAllModes(StreetMode.WALK))
      .buildRequest();
    RaptorRequestTransferCacheKey cacheKeyBase = new RaptorRequestTransferCacheKey(list, base);

    RouteRequest routeRequestWithWheelchairPreferences = base
      .copyOf()
      .withPreferences(p -> p.withWheelchair(b -> b.withStairsReluctance(999)))
      .buildRequest();
    RaptorRequestTransferCacheKey cacheKeyWithWheelchairPreferences =
      new RaptorRequestTransferCacheKey(list, routeRequestWithWheelchairPreferences);

    RouteRequest routeRequestWithWheelchairPreferencesAndWheelchairEnabled =
      routeRequestWithWheelchairPreferences
        .copyOf()
        .withJourney(j -> j.withWheelchair(true))
        .buildRequest();
    RaptorRequestTransferCacheKey cacheKeyWithWheelchairPreferencesAndWheelchairEnabled =
      new RaptorRequestTransferCacheKey(
        list,
        routeRequestWithWheelchairPreferencesAndWheelchairEnabled
      );

    assertEquals(cacheKeyWithWheelchairPreferences, cacheKeyBase);
    assertNotEquals(cacheKeyWithWheelchairPreferencesAndWheelchairEnabled, cacheKeyBase);
  }

  @Test
  public void testRaptorRequestTransferCacheKeyWithWalkMode() {
    List<List<Transfer>> list = List.of();

    // This is intentionally CAR in the beginning.
    RouteRequest base = builder()
      .withJourney(b -> b.withAllModes(StreetMode.CAR))
      .buildRequest();
    RaptorRequestTransferCacheKey cacheKeyBase = new RaptorRequestTransferCacheKey(list, base);

    RouteRequest routeRequestWithWalkPreferences = base
      .copyOf()
      .withPreferences(p -> p.withWalk(b -> b.withBoardCost(999)))
      .buildRequest();
    RaptorRequestTransferCacheKey cacheKeyWithWalkPreferences = new RaptorRequestTransferCacheKey(
      list,
      routeRequestWithWalkPreferences
    );

    RouteRequest routeRequestWithWalkPreferencesAndWalkMode = routeRequestWithWalkPreferences
      .copyOf()
      .withJourney(j -> j.withAllModes(StreetMode.WALK))
      .buildRequest();
    RaptorRequestTransferCacheKey cacheKeyWithWalkPreferencesAndWalkMode =
      new RaptorRequestTransferCacheKey(list, routeRequestWithWalkPreferencesAndWalkMode);

    assertEquals(cacheKeyWithWalkPreferences, cacheKeyBase);
    assertNotEquals(cacheKeyWithWalkPreferencesAndWalkMode, cacheKeyBase);
  }

  @Test
  public void testRaptorRequestTransferCacheKeyWithBikeMode() {
    List<List<Transfer>> list = List.of();

    // This is intentionally CAR in the beginning.
    RouteRequest base = builder()
      .withJourney(b -> b.withAllModes(StreetMode.CAR))
      .buildRequest();
    RaptorRequestTransferCacheKey cacheKeyBase = new RaptorRequestTransferCacheKey(list, base);

    RouteRequest routeRequestWithBikePreferences = base
      .copyOf()
      .withPreferences(p -> p.withBike(b -> b.withBoardCost(999)))
      .buildRequest();
    RaptorRequestTransferCacheKey cacheKeyWithBikePreferences = new RaptorRequestTransferCacheKey(
      list,
      routeRequestWithBikePreferences
    );

    RouteRequest routeRequestWithBikePreferencesAndBikeMode = routeRequestWithBikePreferences
      .copyOf()
      .withJourney(j -> j.withAllModes(StreetMode.BIKE))
      .buildRequest();
    RaptorRequestTransferCacheKey cacheKeyWithBikePreferencesAndBikeMode =
      new RaptorRequestTransferCacheKey(list, routeRequestWithBikePreferencesAndBikeMode);

    assertEquals(cacheKeyWithBikePreferences, cacheKeyBase);
    assertNotEquals(cacheKeyWithBikePreferencesAndBikeMode, cacheKeyBase);
  }

  @Test
  public void testRaptorRequestTransferCacheKeyWithCarMode() {
    List<List<Transfer>> list = List.of();

    // This is intentionally WALK in the beginning.
    RouteRequest base = builder()
      .withJourney(b -> b.withAllModes(StreetMode.WALK))
      .buildRequest();
    RaptorRequestTransferCacheKey cacheKeyBase = new RaptorRequestTransferCacheKey(list, base);

    RouteRequest routeRequestWithCarPreferences = base
      .copyOf()
      .withPreferences(p -> p.withCar(b -> b.withBoardCost(999)))
      .buildRequest();
    RaptorRequestTransferCacheKey cacheKeyWithCarPreferences = new RaptorRequestTransferCacheKey(
      list,
      routeRequestWithCarPreferences
    );

    RouteRequest routeRequestWithCarPreferencesAndCarMode = routeRequestWithCarPreferences
      .copyOf()
      .withJourney(j -> j.withAllModes(StreetMode.CAR))
      .buildRequest();
    RaptorRequestTransferCacheKey cacheKeyWithCarPreferencesAndCarMode =
      new RaptorRequestTransferCacheKey(list, routeRequestWithCarPreferencesAndCarMode);

    assertEquals(cacheKeyWithCarPreferences, cacheKeyBase);
    assertNotEquals(cacheKeyWithCarPreferencesAndCarMode, cacheKeyBase);
  }

  @Test
  public void testWalkSpeedIsBucketedToNearest5cm() {
    List<List<Transfer>> list = List.of();

    // 1.38, 1.39, 1.40, 1.41, 1.42 are all closer to 1.40 than to 1.35 or 1.45 -> same bucket.
    assertEquals(
      new RaptorRequestTransferCacheKey(list, walkRequestWithSpeed(1.38)),
      new RaptorRequestTransferCacheKey(list, walkRequestWithSpeed(1.39))
    );
    assertEquals(
      new RaptorRequestTransferCacheKey(list, walkRequestWithSpeed(1.40)),
      new RaptorRequestTransferCacheKey(list, walkRequestWithSpeed(1.41))
    );
    assertEquals(
      new RaptorRequestTransferCacheKey(list, walkRequestWithSpeed(1.42)),
      new RaptorRequestTransferCacheKey(list, walkRequestWithSpeed(1.38))
    );

    // 1.42 rounds to 1.40, 1.43 rounds to 1.45 -> different buckets.
    assertNotEquals(
      new RaptorRequestTransferCacheKey(list, walkRequestWithSpeed(1.42)),
      new RaptorRequestTransferCacheKey(list, walkRequestWithSpeed(1.43))
    );

    // Tie (1.425) rounds half-up to 1.45.
    assertEquals(
      new RaptorRequestTransferCacheKey(list, walkRequestWithSpeed(1.425)),
      new RaptorRequestTransferCacheKey(list, walkRequestWithSpeed(1.45))
    );

    // Exact multiples of 0.05 stay in their own bucket.
    assertNotEquals(
      new RaptorRequestTransferCacheKey(list, walkRequestWithSpeed(1.30)),
      new RaptorRequestTransferCacheKey(list, walkRequestWithSpeed(1.35))
    );
  }

  @Test
  public void testWalkSpeedBucketingIsSkippedForNonWalkingModes() {
    List<List<Transfer>> list = List.of();

    // In CAR mode walk sub-options are forced to DEFAULT, so walk speed is irrelevant;
    // two CAR requests with different walk speeds must still produce equal cache keys.
    RouteRequest car1 = RouteRequest.of()
      .withFrom(GenericLocation.fromCoordinate(0, 0))
      .withTo(GenericLocation.fromCoordinate(1, 1))
      .withJourney(b -> b.withAllModes(StreetMode.CAR))
      .withPreferences(p -> p.withWalk(b -> b.withSpeed(1.38)))
      .buildRequest();
    RouteRequest car2 = RouteRequest.of()
      .withFrom(GenericLocation.fromCoordinate(0, 0))
      .withTo(GenericLocation.fromCoordinate(1, 1))
      .withJourney(b -> b.withAllModes(StreetMode.CAR))
      .withPreferences(p -> p.withWalk(b -> b.withSpeed(1.41)))
      .buildRequest();
    assertEquals(
      new RaptorRequestTransferCacheKey(list, car1),
      new RaptorRequestTransferCacheKey(list, car2)
    );
  }

  @Test
  public void testWalkReluctanceIsBucketedWithTieredSteps() {
    List<List<Transfer>> list = List.of();

    // Below 3.0: step 0.1, round to nearest (half-up).
    // 2.05 ties and rounds up to 2.1; 2.10 stays at 2.1 -> same bucket.
    assertEquals(
      new RaptorRequestTransferCacheKey(list, walkRequestWithReluctance(2.05)),
      new RaptorRequestTransferCacheKey(list, walkRequestWithReluctance(2.10))
    );
    // 2.10 -> 2.1, 2.14 -> 2.1 (nearest is 2.1, 2.15 would tie to 2.2).
    assertEquals(
      new RaptorRequestTransferCacheKey(list, walkRequestWithReluctance(2.10)),
      new RaptorRequestTransferCacheKey(list, walkRequestWithReluctance(2.14))
    );
    // 2.14 -> 2.1, 2.15 -> 2.2 (tie rounded up).
    assertNotEquals(
      new RaptorRequestTransferCacheKey(list, walkRequestWithReluctance(2.14)),
      new RaptorRequestTransferCacheKey(list, walkRequestWithReluctance(2.15))
    );

    // [3.0, 10.0): step 0.5, round to nearest.
    // 3.1 -> 3.0, 3.2 -> 3.0 (both closer to 3.0 than to 3.5).
    assertEquals(
      new RaptorRequestTransferCacheKey(list, walkRequestWithReluctance(3.1)),
      new RaptorRequestTransferCacheKey(list, walkRequestWithReluctance(3.2))
    );
    // 3.3 -> 3.5, 3.4 -> 3.5 (both closer to 3.5).
    assertEquals(
      new RaptorRequestTransferCacheKey(list, walkRequestWithReluctance(3.3)),
      new RaptorRequestTransferCacheKey(list, walkRequestWithReluctance(3.4))
    );
    // Crossing the 0.25-halfway point: 3.2 -> 3.0, 3.3 -> 3.5.
    assertNotEquals(
      new RaptorRequestTransferCacheKey(list, walkRequestWithReluctance(3.2)),
      new RaptorRequestTransferCacheKey(list, walkRequestWithReluctance(3.3))
    );

    // [10.0, inf): step 1.0. Units.reluctance already rounds values >= 10.0 to integers
    // (half-up), so 10.0 and 10.4 both normalize to 10; 10.5 normalizes to 11.
    assertEquals(
      new RaptorRequestTransferCacheKey(list, walkRequestWithReluctance(10.0)),
      new RaptorRequestTransferCacheKey(list, walkRequestWithReluctance(10.4))
    );
    assertNotEquals(
      new RaptorRequestTransferCacheKey(list, walkRequestWithReluctance(10.4)),
      new RaptorRequestTransferCacheKey(list, walkRequestWithReluctance(10.5))
    );
  }

  @Test
  public void testRaptorRequestTransferCacheKeyWithTurnReluctance() {
    List<List<Transfer>> list = List.of();

    RouteRequest base = builder()
      .withJourney(b -> b.withAllModes(StreetMode.WALK))
      .buildRequest();
    RaptorRequestTransferCacheKey cacheKeyBase = new RaptorRequestTransferCacheKey(list, base);

    RouteRequest routeRequestWithTurnReluctance = base
      .copyOf()
      .withPreferences(p -> p.withStreet(b -> b.withTurnReluctance(999)))
      .buildRequest();
    RaptorRequestTransferCacheKey cacheKeyWithTurnReluctance = new RaptorRequestTransferCacheKey(
      list,
      routeRequestWithTurnReluctance
    );

    assertNotEquals(cacheKeyWithTurnReluctance, cacheKeyBase);
  }

  private static RouteRequestBuilder builder() {
    return RouteRequest.of()
      .withFrom(GenericLocation.fromCoordinate(0, 0))
      .withTo(GenericLocation.fromCoordinate(1, 1));
  }

  @Test
  public void testBikeSpeedIsBucketedToNearest10cm() {
    List<List<Transfer>> list = List.of();

    // 5.05, 5.06, 5.07 all round half-up to 5.1 -> same bucket.
    assertEquals(
      new RaptorRequestTransferCacheKey(list, bikeRequestWithSpeed(5.05)),
      new RaptorRequestTransferCacheKey(list, bikeRequestWithSpeed(5.07))
    );
    assertEquals(
      new RaptorRequestTransferCacheKey(list, bikeRequestWithSpeed(5.05)),
      new RaptorRequestTransferCacheKey(list, bikeRequestWithSpeed(5.10))
    );

    // 5.03, 5.04 are closer to 5.0 -> different bucket from 5.05.
    assertNotEquals(
      new RaptorRequestTransferCacheKey(list, bikeRequestWithSpeed(5.04)),
      new RaptorRequestTransferCacheKey(list, bikeRequestWithSpeed(5.05))
    );
    assertEquals(
      new RaptorRequestTransferCacheKey(list, bikeRequestWithSpeed(5.03)),
      new RaptorRequestTransferCacheKey(list, bikeRequestWithSpeed(5.00))
    );

    // Exact multiples of 0.1 stay in their own bucket.
    assertNotEquals(
      new RaptorRequestTransferCacheKey(list, bikeRequestWithSpeed(4.9)),
      new RaptorRequestTransferCacheKey(list, bikeRequestWithSpeed(5.0))
    );
  }

  @Test
  public void testBikeBucketingIsSkippedForNonBikingModes() {
    List<List<Transfer>> list = List.of();

    // In CAR mode bike sub-options are forced to DEFAULT, so bike speed is irrelevant;
    // two CAR requests with different bike speeds must still produce equal cache keys.
    RouteRequest car1 = builder()
      .withJourney(b -> b.withAllModes(StreetMode.CAR))
      .withPreferences(p -> p.withBike(b -> b.withSpeed(5.03)))
      .buildRequest();
    RouteRequest car2 = builder()
      .withJourney(b -> b.withAllModes(StreetMode.CAR))
      .withPreferences(p -> p.withBike(b -> b.withSpeed(5.08)))
      .buildRequest();
    assertEquals(
      new RaptorRequestTransferCacheKey(list, car1),
      new RaptorRequestTransferCacheKey(list, car2)
    );
  }

  @Test
  public void testBikeReluctanceIsBucketedWithTieredSteps() {
    List<List<Transfer>> list = List.of();

    // Below 3.0: step 0.1. 2.05 ties half-up to 2.1; 2.10 stays at 2.1 -> same bucket.
    assertEquals(
      new RaptorRequestTransferCacheKey(list, bikeRequestWithReluctance(2.05)),
      new RaptorRequestTransferCacheKey(list, bikeRequestWithReluctance(2.10))
    );
    assertNotEquals(
      new RaptorRequestTransferCacheKey(list, bikeRequestWithReluctance(2.14)),
      new RaptorRequestTransferCacheKey(list, bikeRequestWithReluctance(2.15))
    );

    // [3.0, 10.0): step 0.5.
    assertEquals(
      new RaptorRequestTransferCacheKey(list, bikeRequestWithReluctance(3.1)),
      new RaptorRequestTransferCacheKey(list, bikeRequestWithReluctance(3.2))
    );
    assertNotEquals(
      new RaptorRequestTransferCacheKey(list, bikeRequestWithReluctance(3.2)),
      new RaptorRequestTransferCacheKey(list, bikeRequestWithReluctance(3.3))
    );

    // [10.0, inf): step 1.0. Units.reluctance already rounds values >= 10.0 to integers.
    assertEquals(
      new RaptorRequestTransferCacheKey(list, bikeRequestWithReluctance(10.0)),
      new RaptorRequestTransferCacheKey(list, bikeRequestWithReluctance(10.4))
    );
    assertNotEquals(
      new RaptorRequestTransferCacheKey(list, bikeRequestWithReluctance(10.4)),
      new RaptorRequestTransferCacheKey(list, bikeRequestWithReluctance(10.5))
    );
  }

  @Test
  public void testCarReluctanceIsBucketedWithTieredSteps() {
    List<List<Transfer>> list = List.of();

    // Below 3.0: step 0.1.
    assertEquals(
      new RaptorRequestTransferCacheKey(list, carRequestWithReluctance(2.05)),
      new RaptorRequestTransferCacheKey(list, carRequestWithReluctance(2.10))
    );
    assertNotEquals(
      new RaptorRequestTransferCacheKey(list, carRequestWithReluctance(2.14)),
      new RaptorRequestTransferCacheKey(list, carRequestWithReluctance(2.15))
    );

    // [3.0, 10.0): step 0.5.
    assertEquals(
      new RaptorRequestTransferCacheKey(list, carRequestWithReluctance(3.1)),
      new RaptorRequestTransferCacheKey(list, carRequestWithReluctance(3.2))
    );
    assertNotEquals(
      new RaptorRequestTransferCacheKey(list, carRequestWithReluctance(3.2)),
      new RaptorRequestTransferCacheKey(list, carRequestWithReluctance(3.3))
    );

    // [10.0, inf): step 1.0.
    assertEquals(
      new RaptorRequestTransferCacheKey(list, carRequestWithReluctance(10.0)),
      new RaptorRequestTransferCacheKey(list, carRequestWithReluctance(10.4))
    );
    assertNotEquals(
      new RaptorRequestTransferCacheKey(list, carRequestWithReluctance(10.4)),
      new RaptorRequestTransferCacheKey(list, carRequestWithReluctance(10.5))
    );
  }

  @Test
  public void testCarBucketingIsSkippedForNonDrivingModes() {
    List<List<Transfer>> list = List.of();

    // In WALK mode car sub-options are forced to DEFAULT, so car reluctance is irrelevant;
    // two WALK requests with different car reluctances must still produce equal cache keys.
    RouteRequest walk1 = builder()
      .withJourney(b -> b.withAllModes(StreetMode.WALK))
      .withPreferences(p -> p.withCar(b -> b.withReluctance(2.05)))
      .buildRequest();
    RouteRequest walk2 = builder()
      .withJourney(b -> b.withAllModes(StreetMode.WALK))
      .withPreferences(p -> p.withCar(b -> b.withReluctance(2.14)))
      .buildRequest();
    assertEquals(
      new RaptorRequestTransferCacheKey(list, walk1),
      new RaptorRequestTransferCacheKey(list, walk2)
    );
  }

  @Test
  public void testTurnReluctanceIsBucketedWithTieredSteps() {
    List<List<Transfer>> list = List.of();

    // Below 3.0: step 0.1.
    assertEquals(
      new RaptorRequestTransferCacheKey(list, turnReluctanceRequest(2.05)),
      new RaptorRequestTransferCacheKey(list, turnReluctanceRequest(2.10))
    );
    assertNotEquals(
      new RaptorRequestTransferCacheKey(list, turnReluctanceRequest(2.14)),
      new RaptorRequestTransferCacheKey(list, turnReluctanceRequest(2.15))
    );

    // [3.0, 10.0): step 0.5.
    assertEquals(
      new RaptorRequestTransferCacheKey(list, turnReluctanceRequest(3.1)),
      new RaptorRequestTransferCacheKey(list, turnReluctanceRequest(3.2))
    );
    assertNotEquals(
      new RaptorRequestTransferCacheKey(list, turnReluctanceRequest(3.2)),
      new RaptorRequestTransferCacheKey(list, turnReluctanceRequest(3.3))
    );
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

  private static RouteRequest turnReluctanceRequest(double turnReluctance) {
    return builder()
      .withJourney(b -> b.withAllModes(StreetMode.WALK))
      .withPreferences(p -> p.withStreet(b -> b.withTurnReluctance(turnReluctance)))
      .buildRequest();
  }
}
