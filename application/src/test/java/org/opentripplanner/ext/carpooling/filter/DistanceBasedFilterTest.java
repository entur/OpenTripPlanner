package org.opentripplanner.ext.carpooling.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.opentripplanner.ext.carpooling.TestCarpoolTripBuilder.*;
import static org.opentripplanner.ext.carpooling.TestFixtures.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.framework.geometry.WgsCoordinate;

class DistanceBasedFilterTest {

  private DistanceBasedFilter filter;

  @BeforeEach
  void setup() {
    filter = new DistanceBasedFilter();
  }

  @Test
  void accepts_passengerAlongRoute_returnsTrue() {
    // Trip from Oslo Center (59.9139, 10.7522) to Oslo North (59.9549, 10.7922)
    // This is roughly northeast direction
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);

    // Passenger journey along approximately the same line
    var passengerPickup = new WgsCoordinate(59.920, 10.760);
    var passengerDropoff = new WgsCoordinate(59.940, 10.780);

    // Both points should be very close to the trip's direct line
    assertTrue(filter.accepts(trip, passengerPickup, passengerDropoff));
  }

  @Test
  void accepts_passengerParallelToRoute_nearRoute_returnsTrue() {
    // Trip from Oslo Center to Oslo North (going north-northeast)
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);

    // Passenger journey parallel to the route, but slightly to the west
    // Within 50km perpendicular distance
    var passengerPickup = new WgsCoordinate(59.920, 10.740);
    var passengerDropoff = new WgsCoordinate(59.940, 10.760);

    assertTrue(filter.accepts(trip, passengerPickup, passengerDropoff));
  }

  @Test
  void rejects_passengerPerpendicularToRoute_farAway_returnsFalse() {
    // Trip from Oslo Center to Oslo North (going north-northeast)
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);

    // Passenger journey perpendicular to the route, far to the west
    // > 50km perpendicular distance from the route line
    var passengerPickup = new WgsCoordinate(59.9139, 9.5); // Far west
    var passengerDropoff = new WgsCoordinate(59.9549, 9.5); // Still far west

    assertFalse(filter.accepts(trip, passengerPickup, passengerDropoff));
  }

  @Test
  void rejects_passengerInDifferentCity_returnsFalse() {
    // Trip from Oslo Center to Oslo North
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);

    // Passenger in Bergen (~300km away)
    var passengerPickup = new WgsCoordinate(60.39, 5.32);
    var passengerDropoff = new WgsCoordinate(60.40, 5.33);

    assertFalse(filter.accepts(trip, passengerPickup, passengerDropoff));
  }

  @Test
  void rejects_oneLocationNear_otherLocationFar_returnsFalse() {
    // Simple horizontal trip (east-west, same latitude)
    var tripStart = new WgsCoordinate(59.9, 10.70);
    var tripEnd = new WgsCoordinate(59.9, 10.80);
    var trip = createSimpleTrip(tripStart, tripEnd);

    // Pickup on the route, but dropoff far to the north (>50km perpendicular)
    // At this latitude, 0.5° latitude ≈ 55km
    var passengerPickup = new WgsCoordinate(59.9, 10.75); // On route
    var passengerDropoff = new WgsCoordinate(59.9 + 0.5, 10.75); // Far north

    // Should reject because BOTH locations must be near the route
    assertFalse(filter.accepts(trip, passengerPickup, passengerDropoff));
  }

  @Test
  void rejects_pickupFar_dropoffNear_returnsFalse() {
    // Simple horizontal trip (east-west, same latitude)
    var tripStart = new WgsCoordinate(59.9, 10.70);
    var tripEnd = new WgsCoordinate(59.9, 10.80);
    var trip = createSimpleTrip(tripStart, tripEnd);

    // Pickup far to the north (>50km perpendicular), dropoff on the route
    // At this latitude, 0.5° latitude ≈ 55km
    var passengerPickup = new WgsCoordinate(59.9 + 0.5, 10.75); // Far north
    var passengerDropoff = new WgsCoordinate(59.9, 10.75); // On route

    // Should reject because BOTH locations must be near the route
    assertFalse(filter.accepts(trip, passengerPickup, passengerDropoff));
  }

  @Test
  void accepts_longTripShortPassengerSegment_returnsTrue() {
    // Long driver trip from Oslo to much further north
    var farNorth = new WgsCoordinate(60.5, 10.8);
    var trip = createSimpleTrip(OSLO_CENTER, farNorth);

    // Short passenger segment along the driver's route
    var passengerPickup = new WgsCoordinate(59.920, 10.760);
    var passengerDropoff = new WgsCoordinate(59.940, 10.780);

    // Should accept - passenger is riding only a small segment of a long trip
    assertTrue(filter.accepts(trip, passengerPickup, passengerDropoff));
  }

  @Test
  void accepts_passengerNearRouteEndpoints_returnsTrue() {
    // Trip from Oslo Center to Oslo North
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);

    // Passenger very close to trip start and end points
    var passengerPickup = new WgsCoordinate(59.914, 10.753); // Very close to start
    var passengerDropoff = new WgsCoordinate(59.954, 10.791); // Very close to end

    assertTrue(filter.accepts(trip, passengerPickup, passengerDropoff));
  }

  @Test
  void accepts_passengerAtMaxDistance_returnsTrue() {
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);

    // Passenger locations at approximately 50km perpendicular distance from route
    // This is at the boundary of acceptance
    // Using ~0.4° offset which is roughly 45km at this latitude
    var passengerPickup = new WgsCoordinate(59.920, 10.752 + 0.4);
    var passengerDropoff = new WgsCoordinate(59.940, 10.772 + 0.4);

    // Should accept at boundary
    assertTrue(filter.accepts(trip, passengerPickup, passengerDropoff));
  }

  @Test
  void customMaxDistance_acceptsWithinCustomDistance() {
    // Custom filter with 100km max distance
    var customFilter = new DistanceBasedFilter(100_000);

    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);

    // Passenger 80km perpendicular to the route (would be rejected by default 50km filter)
    var passengerPickup = new WgsCoordinate(59.920, 10.752 + 0.7);
    var passengerDropoff = new WgsCoordinate(59.940, 10.772 + 0.7);

    assertTrue(customFilter.accepts(trip, passengerPickup, passengerDropoff));
  }

  @Test
  void customMaxDistance_rejectsOutsideCustomDistance() {
    // Custom filter with 20km max distance (stricter)
    var customFilter = new DistanceBasedFilter(20_000);

    // Simple horizontal trip
    var tripStart = new WgsCoordinate(59.9, 10.70);
    var tripEnd = new WgsCoordinate(59.9, 10.80);
    var trip = createSimpleTrip(tripStart, tripEnd);

    // Passenger ~30km perpendicular to the route
    // At this latitude, 0.27° latitude ≈ 30km
    var passengerPickup = new WgsCoordinate(59.9 + 0.27, 10.72);
    var passengerDropoff = new WgsCoordinate(59.9 + 0.27, 10.78);

    assertFalse(customFilter.accepts(trip, passengerPickup, passengerDropoff));
  }

  @Test
  void getMaxDistanceMeters_returnsConfiguredDistance() {
    var customFilter = new DistanceBasedFilter(75_000);
    assertEquals(75_000, customFilter.getMaxDistanceMeters());
  }

  @Test
  void defaultMaxDistance_is50km() {
    assertEquals(50_000, filter.getMaxDistanceMeters());
  }

  @Test
  void accepts_verticalRoute_passengerAlongRoute_returnsTrue() {
    // Trip going straight north (same longitude)
    var tripStart = new WgsCoordinate(59.9, 10.75);
    var tripEnd = new WgsCoordinate(60.0, 10.75);
    var trip = createSimpleTrip(tripStart, tripEnd);

    // Passenger also going north along the same longitude
    var passengerPickup = new WgsCoordinate(59.92, 10.75);
    var passengerDropoff = new WgsCoordinate(59.95, 10.75);

    assertTrue(filter.accepts(trip, passengerPickup, passengerDropoff));
  }

  @Test
  void accepts_horizontalRoute_passengerAlongRoute_returnsTrue() {
    // Trip going straight east (same latitude)
    var tripStart = new WgsCoordinate(59.9, 10.70);
    var tripEnd = new WgsCoordinate(59.9, 10.80);
    var trip = createSimpleTrip(tripStart, tripEnd);

    // Passenger also going east along the same latitude
    var passengerPickup = new WgsCoordinate(59.9, 10.72);
    var passengerDropoff = new WgsCoordinate(59.9, 10.78);

    assertTrue(filter.accepts(trip, passengerPickup, passengerDropoff));
  }

  @Test
  void accepts_tripWithMultipleStops_passengerNearMainRoute() {
    // Trip with multiple stops - filter only looks at boarding to alighting line
    var stop1 = createStopAt(0, LAKE_EAST);
    var stop2 = createStopAt(1, LAKE_SOUTH);
    var trip = createTripWithStops(LAKE_NORTH, java.util.List.of(stop1, stop2), LAKE_WEST);

    // Passenger journey near the direct line from LAKE_NORTH to LAKE_WEST
    // Even though actual route goes through EAST and SOUTH
    var passengerPickup = new WgsCoordinate(59.9239, 10.735); // Between NORTH and WEST
    var passengerDropoff = new WgsCoordinate(59.9239, 10.720);

    // Should accept if close to the direct line (boarding to alighting)
    assertTrue(filter.accepts(trip, passengerPickup, passengerDropoff));
  }

  @Test
  void accepts_sameStartEnd_passengerAtSameLocation_returnsTrue() {
    // Edge case: trip starts and ends at same location (round trip)
    var sameLocation = new WgsCoordinate(59.9, 10.75);
    var trip = createSimpleTrip(sameLocation, sameLocation);

    // Passenger at the same location
    var passengerPickup = new WgsCoordinate(59.901, 10.751); // Very close
    var passengerDropoff = new WgsCoordinate(59.902, 10.752);

    assertTrue(filter.accepts(trip, passengerPickup, passengerDropoff));
  }

  @Test
  void rejects_sameStartEnd_passengerFarAway_returnsFalse() {
    // Edge case: trip starts and ends at same location
    var sameLocation = new WgsCoordinate(59.9, 10.75);
    var trip = createSimpleTrip(sameLocation, sameLocation);

    // Passenger far away
    var passengerPickup = new WgsCoordinate(60.5, 11.0);
    var passengerDropoff = new WgsCoordinate(60.5, 11.1);

    assertFalse(filter.accepts(trip, passengerPickup, passengerDropoff));
  }
}
