package org.opentripplanner.ext.carpooling.routing;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.opentripplanner.ext.carpooling.constraints.PassengerDelayConstraints;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.opentripplanner.ext.carpooling.util.BeelineEstimator;
import org.opentripplanner.street.geometry.DirectionUtils;
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
 *   <li>Directional compatibility - prevents backtracking and U-turns</li>
 *   <li>Beeline delay heuristic - optimistic straight-line time estimates</li>
 * </ul>
 * <p>
 * This follows the established OTP pattern of separating candidate generation from evaluation,
 * similar to {@code TransferGenerator} and {@code StreetNearbyStopFinder}.
 */
public class InsertionPositionFinder {

  private static final Logger LOG = LoggerFactory.getLogger(InsertionPositionFinder.class);

  /** Maximum bearing deviation allowed for forward progress (90° allows detours, prevents U-turns) */
  private static final double FORWARD_PROGRESS_TOLERANCE_DEGREES = 90.0;

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
   * This is done BEFORE any expensive routing to eliminate positions early.
   *
   * @param trip The carpool trip being evaluated
   * @param passengerPickup Passenger's pickup location
   * @param passengerDropoff Passenger's dropoff location
   * @return List of viable insertion positions (may be empty)
   */
  public List<InsertionPosition> findViablePositions(
    CarpoolTrip trip,
    WgsCoordinate passengerPickup,
    WgsCoordinate passengerDropoff,
    Duration stopDuration
  ) {
    List<WgsCoordinate> routePoints = trip.routePoints();

    Duration[] beelineTimes = beelineEstimator.calculateCumulativeTimes(routePoints, stopDuration);

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
          !insertionMaintainsForwardProgress(
            routePoints,
            pickupPos,
            dropoffPos,
            passengerPickup,
            passengerDropoff
          )
        ) {
          LOG.trace(
            "Insertion at pickup={}, dropoff={} rejected by directional check",
            pickupPos,
            dropoffPos
          );
          continue;
        }

        if (
          !passesBeelineDelayCheck(
            routePoints,
            beelineTimes,
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
   * Checks if inserting pickup/dropoff points maintains forward progress.
   * Prevents backtracking by ensuring insertions don't cause the route
   * to deviate too far from its intended direction.
   *
   * @param routePoints Current route points
   * @param pickupPos 0-based index of the passenger's pickup in the modified route
   * @param dropoffPos 0-based index of the passenger's dropoff in the modified route
   * @param passengerPickup Passenger pickup coordinate
   * @param passengerDropoff Passenger dropoff coordinate
   * @return true if insertion maintains forward progress
   */
  private boolean insertionMaintainsForwardProgress(
    List<WgsCoordinate> routePoints,
    int pickupPos,
    int dropoffPos,
    WgsCoordinate passengerPickup,
    WgsCoordinate passengerDropoff
  ) {
    if (pickupPos > 0 && pickupPos < routePoints.size()) {
      WgsCoordinate prevPoint = routePoints.get(pickupPos - 1);
      WgsCoordinate nextPoint = routePoints.get(pickupPos);

      if (!maintainsForwardProgress(prevPoint, passengerPickup, nextPoint)) {
        return false;
      }
    }

    if (dropoffPos > 0 && dropoffPos <= routePoints.size()) {
      WgsCoordinate prevPoint;
      if (dropoffPos == pickupPos) {
        prevPoint = passengerPickup;
      } else if (dropoffPos - 1 < routePoints.size()) {
        prevPoint = routePoints.get(dropoffPos - 1);
      } else {
        return true;
      }

      if (dropoffPos < routePoints.size()) {
        WgsCoordinate nextPoint = routePoints.get(dropoffPos);

        return maintainsForwardProgress(prevPoint, passengerDropoff, nextPoint);
      }
    }

    return true;
  }

  /**
   * Checks if inserting a new point maintains forward progress.
   */
  private boolean maintainsForwardProgress(
    WgsCoordinate previous,
    WgsCoordinate newPoint,
    WgsCoordinate next
  ) {
    // Skip check if inserting at an existing point (newPoint equals next or previous)
    // This avoids undefined bearing calculations from a point to itself
    if (newPoint.equals(next) || newPoint.equals(previous)) {
      return true;
    }

    // Calculate intended direction (previous → next)
    double intendedBearing = DirectionUtils.getAzimuth(
      previous.asJtsCoordinate(),
      next.asJtsCoordinate()
    );

    // Calculate detour directions
    double bearingToNew = DirectionUtils.getAzimuth(
      previous.asJtsCoordinate(),
      newPoint.asJtsCoordinate()
    );
    double bearingFromNew = DirectionUtils.getAzimuth(
      newPoint.asJtsCoordinate(),
      next.asJtsCoordinate()
    );

    // Check deviations
    double deviationToNew = DirectionUtils.bearingDifference(intendedBearing, bearingToNew);
    double deviationFromNew = DirectionUtils.bearingDifference(intendedBearing, bearingFromNew);

    // Allow some deviation but not complete reversal
    return (
      deviationToNew <= FORWARD_PROGRESS_TOLERANCE_DEGREES &&
      deviationFromNew <= FORWARD_PROGRESS_TOLERANCE_DEGREES
    );
  }

  /**
   * Checks if an insertion position passes the beeline delay heuristic.
   * This is a fast, optimistic check using straight-line distance estimates.
   * If this check fails, we know the actual A* routing will also fail, so we
   * can skip the expensive routing calculation.
   *
   * @param originalCoords Original route coordinates
   * @param originalBeelineTimes Beeline cumulative times for original route
   * @param passengerPickup Passenger pickup location
   * @param passengerDropoff Passenger dropoff location
   * @param pickupPos 0-based index of the passenger's pickup in the modified route
   * @param dropoffPos 0-based index of the passenger's dropoff in the modified route
   * @param trip The carpool trip being evaluated
   * @return true if insertion might satisfy delay constraints (proceed with A* routing)
   */
  private boolean passesBeelineDelayCheck(
    List<WgsCoordinate> originalCoords,
    Duration[] originalBeelineTimes,
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

    // Calculate beeline times for modified route
    Duration[] modifiedBeelineTimes = beelineEstimator.calculateCumulativeTimes(
      modifiedCoords,
      stopDuration
    );

    // If even the optimistic beeline estimate exceeds a stop's budget, actual routing will too
    return PassengerDelayConstraints.satisfiesConstraints(
      originalBeelineTimes,
      modifiedBeelineTimes,
      pickupPos,
      dropoffPos,
      trip.stops()
    );
  }
}
