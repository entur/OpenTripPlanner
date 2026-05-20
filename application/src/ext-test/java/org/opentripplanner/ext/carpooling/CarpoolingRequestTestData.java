package org.opentripplanner.ext.carpooling;

import java.time.Duration;
import java.time.Instant;
import org.opentripplanner.ext.carpooling.filter.CarpoolingRequest;
import org.opentripplanner.ext.carpooling.filter.CarpoolingRequestBuilder;
import org.opentripplanner.routing.algorithm.raptoradapter.router.street.AccessEgressType;
import org.opentripplanner.street.geometry.WgsCoordinate;

/**
 * Factory methods for creating {@link CarpoolingRequest} instances in tests.
 * Covers all six routing use cases: depart-after and arrive-by, each in direct, access, and
 * egress variants.
 */
public class CarpoolingRequestTestData {

  /**
   * Default max walk time used by every factory here. Matches the 15-minute slack the
   * {@code TimeTripFilter} tests are tuned around. Pinning an explicit value here keeps the
   * test boundary assertions independent of any future preference-default changes.
   */
  public static final Duration DEFAULT_MAX_WALK_TIME = Duration.ofMinutes(15);

  /** Direct routing request with specific passenger coordinates; no time constraint. */
  public static CarpoolingRequest directRequest(WgsCoordinate pickup, WgsCoordinate dropoff) {
    return new CarpoolingRequestBuilder()
      .withPassengerPickup(pickup)
      .withPassengerDropoff(dropoff)
      .withMaxWalkTime(DEFAULT_MAX_WALK_TIME)
      .build();
  }

  /** Access routing request; the passenger's relevant coordinate is the pickup. */
  public static CarpoolingRequest accessRequest(WgsCoordinate passengerPickup) {
    return new CarpoolingRequestBuilder()
      .withPassengerPickup(passengerPickup)
      .withAccessOrEgress(AccessEgressType.ACCESS)
      .withMaxWalkTime(DEFAULT_MAX_WALK_TIME)
      .build();
  }

  /** Egress routing request; the passenger's relevant coordinate is the dropoff. */
  public static CarpoolingRequest egressRequest(WgsCoordinate passengerDropoff) {
    return new CarpoolingRequestBuilder()
      .withPassengerDropoff(passengerDropoff)
      .withAccessOrEgress(AccessEgressType.EGRESS)
      .withMaxWalkTime(DEFAULT_MAX_WALK_TIME)
      .build();
  }

  public static CarpoolingRequest departAfterWithNoTime() {
    return new CarpoolingRequestBuilder()
      .withArriveBy(false)
      .withMaxWalkTime(DEFAULT_MAX_WALK_TIME)
      .build();
  }

  public static CarpoolingRequest arriveByWithNoTime() {
    return new CarpoolingRequestBuilder()
      .withArriveBy(true)
      .withMaxWalkTime(DEFAULT_MAX_WALK_TIME)
      .build();
  }

  public static CarpoolingRequest departAfterDirect(Instant T) {
    return request(T, false, null);
  }

  public static CarpoolingRequest departAfterAccess(Instant T) {
    return request(T, false, AccessEgressType.ACCESS);
  }

  public static CarpoolingRequest departAfterEgress(Instant T) {
    return request(T, false, AccessEgressType.EGRESS);
  }

  public static CarpoolingRequest arriveByDirect(Instant T) {
    return request(T, true, null);
  }

  public static CarpoolingRequest arriveByAccess(Instant T) {
    return request(T, true, AccessEgressType.ACCESS);
  }

  public static CarpoolingRequest arriveByEgress(Instant T) {
    return request(T, true, AccessEgressType.EGRESS);
  }

  private static CarpoolingRequest request(
    Instant T,
    boolean arriveBy,
    AccessEgressType accessOrEgress
  ) {
    return new CarpoolingRequestBuilder()
      .withArriveBy(arriveBy)
      .withRequestedDateTime(T)
      .withAccessOrEgress(accessOrEgress)
      .withMaxWalkTime(DEFAULT_MAX_WALK_TIME)
      .build();
  }

  private CarpoolingRequestTestData() {}
}
