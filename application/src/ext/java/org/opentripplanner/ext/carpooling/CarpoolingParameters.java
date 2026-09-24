package org.opentripplanner.ext.carpooling;

import java.time.Duration;

/**
 * The tuning knobs of the carpooling feature, collected in one place.
 * <p>
 * These are deployment-wide values, not part of the routing request. They are mapped from the
 * {@code carpooling} section of {@code router-config.json} by
 * {@link org.opentripplanner.standalone.config.sandbox.CarpoolingConfig}; {@link #DEFAULT} holds
 * the values used when that section is absent.
 *
 * @param maxCandidateTripsPerRequest the most trips a request evaluates; when more pass the
 *        pre-filters, the ones whose route passes closest to the passenger are kept
 * @param maxCandidatesPerStop the most access/egress candidates a transit stop hands to Raptor;
 *        a stop with more keeps the first and the last car and one per slot of the search window
 * @param maxTrips the most trips an instance holds, over all feeds; further new trips are dropped
 * @param maxStopWalk the longest walk between a transit stop and the vertex where a car can stop
 *        for it; stops farther from any drivable street are not served
 * @param boardCost cost added once to every carpool leg — direct, access and egress alike — for
 *        getting into the car. It is what keeps very short carpool rides from beating walking:
 *        walking costs {@code duration x walkReluctance}, so {@code 10 x 60 x walkReluctance}
 *        means a carpool ride only wins when it saves the passenger roughly ten minutes of
 *        walking. The default assumes a {@code walkReluctance} of 4.0.
 */
public record CarpoolingParameters(
  int maxCandidateTripsPerRequest,
  int maxCandidatesPerStop,
  int maxTrips,
  Duration maxStopWalk,
  int boardCost
) {
  public static final CarpoolingParameters DEFAULT = new CarpoolingParameters(
    50,
    24,
    10_000,
    Duration.ofMinutes(15),
    2400
  );

  public CarpoolingParameters {
    if (maxCandidateTripsPerRequest < 1) {
      throw new IllegalArgumentException("maxCandidateTripsPerRequest must be positive");
    }
    if (maxCandidatesPerStop < 4) {
      throw new IllegalArgumentException("maxCandidatesPerStop must be at least 4");
    }
    if (maxTrips < 1) {
      throw new IllegalArgumentException("maxTrips must be positive");
    }
    if (maxStopWalk.isNegative() || maxStopWalk.isZero()) {
      throw new IllegalArgumentException("maxStopWalk must be positive");
    }
    if (boardCost < 0) {
      throw new IllegalArgumentException("boardCost must not be negative");
    }
  }
}
