package org.opentripplanner.ext.carpooling.routing;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.opentripplanner.ext.carpooling.constraints.PassengerDelayConstraints;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.opentripplanner.ext.carpooling.util.BeelineEstimator;
import org.opentripplanner.ext.carpooling.util.GraphPathUtils;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds viable insertion positions for a passenger in a carpool trip using fast heuristics.
 * <p>
 * This class performs early-stage filtering to identify pickup/dropoff position pairs that
 * are worth evaluating with expensive A* routing. It validates positions using:
 * <ul>
 *   <li>Capacity constraints - ensures available seats throughout the journey</li>
 *   <li>Beeline delay heuristic - optimistic straight-line time estimates</li>
 * </ul>
 * <p>
 * This follows the established OTP pattern of separating candidate generation from evaluation,
 * similar to {@code TransferGenerator} and {@code StreetNearbyStopFinder}.
 */
public class InsertionPositionFinder {

  private static final Logger LOG = LoggerFactory.getLogger(InsertionPositionFinder.class);

  private final BeelineEstimator beelineEstimator;

  /**
   * Creates a finder with default estimator.
   */
  public InsertionPositionFinder() {
    this(new BeelineEstimator());
  }

  /**
   * Creates a finder with specified estimator.
   *
   * @param beelineEstimator Estimator for beeline travel times
   */
  public InsertionPositionFinder(BeelineEstimator beelineEstimator) {
    this.beelineEstimator = beelineEstimator;
  }

  /**
   * Finds insertion positions that pass validation and beeline checks.
   * This is done BEFORE any expensive per-position routing to eliminate positions early.
   * <p>
   * The delay heuristic is a guaranteed lower bound on the actual routed delay: the detour's new
   * segments are estimated by straight-line beeline distance, while the untouched and replaced legs
   * are taken from {@code baselineLegDurations}. A position it rejects would also be rejected after
   * routing, so no feasible insertion is lost here.
   *
   * @param trip The carpool trip being evaluated
   * @param passengerPickup Passenger's pickup location
   * @param passengerDropoff Passenger's dropoff location
   * @param stopDuration Dwell time added at each intermediate stop; used by the beeline delay
   *                     heuristic so its cumulative-time estimates match the per-stop budget
   *                     check used downstream
   * @param baselineLegDurations OTP's routed travel duration for each leg of the trip's baseline,
   *                     one entry per leg ({@code stops().size() - 1}).
   * @return List of viable insertion positions (may be empty)
   */
  public List<InsertionPosition> findViablePositions(
    CarpoolTrip trip,
    WgsCoordinate passengerPickup,
    WgsCoordinate passengerDropoff,
    Duration stopDuration,
    Duration[] baselineLegDurations
  ) {
    List<WgsCoordinate> routePoints = trip.routePoints();
    if (baselineLegDurations.length != routePoints.size() - 1) {
      throw new IllegalArgumentException(
        "baselineLegDurations length (%d) must equal the number of legs (%d)".formatted(
          baselineLegDurations.length,
          routePoints.size() - 1
        )
      );
    }

    Duration[] baselineCumulative = GraphPathUtils.calculateCumulativeDurations(
      baselineLegDurations,
      stopDuration
    );

    List<InsertionPosition> viable = new ArrayList<>();

    // pickupPos/dropoffPos are 0-based indices of the passenger's stops in the modified route.
    // Pickup cannot be at index 0 (that's the driver's origin).
    for (int pickupPos = 1; pickupPos < routePoints.size(); pickupPos++) {
      // Dropoff must be after pickup. Max is routePoints.size() (appended after all original stops except the last).
      for (int dropoffPos = pickupPos + 1; dropoffPos <= routePoints.size(); dropoffPos++) {
        if (!trip.hasCapacityForInsertion(pickupPos, dropoffPos, 1)) {
          LOG.trace(
            "Insertion at pickup={}, dropoff={} rejected by capacity check",
            pickupPos,
            dropoffPos
          );
          continue;
        }

        if (
          !passesBeelineDelayCheck(
            routePoints,
            baselineLegDurations,
            baselineCumulative,
            passengerPickup,
            passengerDropoff,
            pickupPos,
            dropoffPos,
            trip,
            stopDuration
          )
        ) {
          LOG.trace(
            "Insertion at pickup={}, dropoff={} rejected by beeline delay heuristic",
            pickupPos,
            dropoffPos
          );
          continue;
        }

        viable.add(new InsertionPosition(pickupPos, dropoffPos));
      }
    }

    return viable;
  }

  /**
   * Checks if an insertion position passes the beeline delay heuristic — a fast, optimistic check
   * that, when it fails, guarantees the actual A* routing would fail too, so the expensive routing
   * can be skipped.
   * <p>
   * The modified route is the baseline with the passenger's pickup and dropoff inserted. Each
   * untouched leg keeps its baseline duration and only the (at most four) detour segments around
   * the inserted points are estimated with beeline distance.
   *
   * @param originalCoords Original route coordinates
   * @param baselineLegDurations Actual routed duration of each baseline leg
   * @param baselineCumulative Cumulative arrival times for the baseline route (from
   *        {@code baselineLegDurations} and {@code stopDuration})
   * @param passengerPickup Passenger pickup location
   * @param passengerDropoff Passenger dropoff location
   * @param pickupPos 0-based index of the passenger's pickup in the modified route
   * @param dropoffPos 0-based index of the passenger's dropoff in the modified route
   * @param trip The carpool trip being evaluated
   * @return true if insertion might satisfy delay constraints (proceed with A* routing)
   */
  private boolean passesBeelineDelayCheck(
    List<WgsCoordinate> originalCoords,
    Duration[] baselineLegDurations,
    Duration[] baselineCumulative,
    WgsCoordinate passengerPickup,
    WgsCoordinate passengerDropoff,
    int pickupPos,
    int dropoffPos,
    CarpoolTrip trip,
    Duration stopDuration
  ) {
    // Build modified coordinate list with passenger stops inserted
    List<WgsCoordinate> modifiedCoords = new ArrayList<>(originalCoords);
    modifiedCoords.add(pickupPos, passengerPickup);
    modifiedCoords.add(dropoffPos, passengerDropoff);

    // Each modified segment is either an untouched baseline leg (use its actual routed duration)
    // or one of the detour segments around the inserted points (lower-bound it with the beeline).
    Duration[] modifiedSegmentDurations = new Duration[modifiedCoords.size() - 1];
    for (int i = 0; i < modifiedSegmentDurations.length; i++) {
      int baselineIndex = InsertionPosition.baselineSegmentIndex(i, pickupPos, dropoffPos);
      modifiedSegmentDurations[i] = baselineIndex >= 0
        ? baselineLegDurations[baselineIndex]
        : beelineEstimator.estimateDuration(modifiedCoords.get(i), modifiedCoords.get(i + 1));
    }

    Duration[] modifiedCumulative = GraphPathUtils.calculateCumulativeDurations(
      modifiedSegmentDurations,
      stopDuration
    );

    return PassengerDelayConstraints.satisfiesConstraints(
      baselineCumulative,
      modifiedCumulative,
      pickupPos,
      dropoffPos,
      trip.stops()
    );
  }
}
