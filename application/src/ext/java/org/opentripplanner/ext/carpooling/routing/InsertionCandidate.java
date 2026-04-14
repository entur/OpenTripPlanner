package org.opentripplanner.ext.carpooling.routing;

import java.time.Duration;
import java.util.List;
import org.opentripplanner.astar.model.GraphPath;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.opentripplanner.ext.carpooling.util.GraphPathUtils;
import org.opentripplanner.routing.graphfinder.NearbyStop;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.state.State;

/**
 * Represents a viable insertion of a passenger into a carpool trip.
 * <p>
 * Contains all information needed to construct an itinerary, including:
 * - The original trip
 * - Insertion positions (where pickup and dropoff occur in the modified route)
 * - Route segments (all GraphPaths forming the complete modified route)
 * - Timing information (baseline and total duration, deviation)
 * <p>
 * {@code pickupPosition} and {@code dropoffPosition} are 0-based indices of the passenger's
 * pickup and dropoff stops in the modified route (the route after the passenger's stops have
 * been inserted into the carpool trip).
 */
public record InsertionCandidate(
  CarpoolTrip trip,
  int pickupPosition,
  int dropoffPosition,
  List<GraphPath<State, Edge, Vertex>> routeSegments,
  Duration durationBetweenOriginAndDestination,
  Duration totalDuration,
  Duration stopDuration,
  NearbyStop transitStop,
  Duration totalTripDuration
) {
  public InsertionCandidate(
    CarpoolTrip trip,
    int pickupPosition,
    int dropoffPosition,
    List<GraphPath<State, Edge, Vertex>> routeSegments,
    Duration durationBetweenOriginAndDestination,
    Duration totalDuration,
    Duration stopDuration,
    NearbyStop transitStop
  ) {
    this(
      trip,
      pickupPosition,
      dropoffPosition,
      routeSegments,
      durationBetweenOriginAndDestination,
      totalDuration,
      stopDuration,
      transitStop,
      computeTotalTripDuration(routeSegments, stopDuration)
    );
  }

  private static Duration computeTotalTripDuration(
    List<GraphPath<State, Edge, Vertex>> routeSegments,
    Duration stopDuration
  ) {
    Duration[] cumulativeDurations = GraphPathUtils.calculateCumulativeDurations(
      routeSegments.toArray(new GraphPath[0]),
      stopDuration
    );
    return cumulativeDurations[cumulativeDurations.length - 1];
  }

  /**
   * Calculates the additional duration caused by inserting this passenger.
   */
  public Duration additionalDuration() {
    return totalDuration.minus(durationBetweenOriginAndDestination);
  }

  /**
   * Gets the pickup route segment(s) - from boarding to passenger pickup.
   * Returns all segments before the pickup position.
   */
  public List<GraphPath<State, Edge, Vertex>> getPickupSegments() {
    if (pickupPosition == 0) {
      return List.of();
    }
    return routeSegments.subList(0, pickupPosition);
  }

  /**
   * Gets the shared route segment(s) - from passenger pickup to dropoff.
   * Returns all segments between pickup and dropoff positions.
   */
  public List<GraphPath<State, Edge, Vertex>> getSharedSegments() {
    return routeSegments.subList(pickupPosition, dropoffPosition);
  }

  /**
   * Gets the dropoff route segment(s) - from passenger dropoff to alighting.
   * Returns all segments after the dropoff position.
   */
  public List<GraphPath<State, Edge, Vertex>> getDropoffSegments() {
    if (dropoffPosition >= routeSegments.size()) {
      return List.of();
    }
    return routeSegments.subList(dropoffPosition, routeSegments.size());
  }

  /**
   * Calculates the duration from trip start until the car departs with the passenger onboard.
   * Includes travel time through pickup segments, intermediate stop delays, and boarding time
   * at the pickup point.
   * Returns {@link Duration#ZERO} when the passenger boards at the trip origin (no pickup segments).
   */
  public Duration getDurationUntilDepartureWithPassenger() {
    var pickupSegments = getPickupSegments();
    if (pickupSegments.isEmpty()) {
      return Duration.ZERO;
    }
    return totalSegmentDuration(pickupSegments, stopDuration).plus(stopDuration);
  }

  /**
   * Calculates the duration of the passenger's ride from pickup to dropoff.
   * Includes travel time through shared segments and stop delays at intermediate stops.
   * For a single shared segment (direct ride), no stop delays are added.
   */
  public Duration getPassengerRideDuration() {
    return totalSegmentDuration(getSharedSegments(), stopDuration);
  }

  private static Duration totalSegmentDuration(
    List<GraphPath<State, Edge, Vertex>> segments,
    Duration stopDuration
  ) {
    return segments
      .stream()
      .map(GraphPathUtils::calculateDuration)
      .reduce(Duration.ZERO, Duration::plus)
      .plus(stopDuration.multipliedBy(Math.max(0, segments.size() - 1)));
  }

  @Override
  public String toString() {
    return String.format(
      "InsertionCandidate{trip=%s, pickup@%d, dropoff@%d, additional=%ds, segments=%d}",
      trip.getId(),
      pickupPosition,
      dropoffPosition,
      additionalDuration().getSeconds(),
      routeSegments.size()
    );
  }
}
