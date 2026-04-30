package org.opentripplanner.ext.carpooling.routing;

import java.time.Duration;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.astar.model.GraphPath;
import org.opentripplanner.ext.carpooling.util.GraphPathUtils;
import org.opentripplanner.framework.model.TimeAndCost;
import org.opentripplanner.raptor.spi.RaptorConstants;
import org.opentripplanner.raptor.spi.RaptorCostConverter;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RoutingAccessEgress;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.state.State;

public class CarpoolAccessEgress implements RoutingAccessEgress {

  /**
   * The Raptor departure time of this access/egress leg, in seconds since
   * {@code transitSearchTimeZero}. This is the moment the passenger leaves the origin side of the
   * leg: the start of an optional walk to the carpool pickup, or — when no walk is needed — the
   * moment the carpool itself arrives at the pickup. The driver runs on a committed schedule, so
   * Raptor cannot delay this time.
   */
  private final int passengerDepartureTime;

  /**
   * The Raptor arrival time of this access/egress leg, in seconds since
   * {@code transitSearchTimeZero}. This is the moment the passenger reaches the destination side
   * of the leg: the end of an optional walk from the carpool dropoff, or — when no walk is needed
   * — the moment the carpool reaches the dropoff. Equal to
   * {@code passengerDepartureTime + walkSeconds + rideSeconds}.
   */
  private final int passengerArrivalTime;

  private final int stop;
  private final int rideSeconds;
  private final int durationInSeconds;
  private final int c1;

  @Nullable
  private final GraphPath<State, Edge, Vertex> walkToPickup;

  private final List<GraphPath<State, Edge, Vertex>> sharedSegments;

  @Nullable
  private final GraphPath<State, Edge, Vertex> walkFromDropoff;

  private final TimeAndCost penalty;
  private final double totalWeight;
  private final double carpoolReluctance;

  /**
   * Builds an access/egress leg from a passenger-side departure time and the durations implied by
   * the supplied paths and ride. The walk portion is the combined duration of {@code walkToPickup}
   * and {@code walkFromDropoff} (zero when either path is {@code null}); the ride portion is the
   * caller-supplied {@code rideDuration} (typically {@code InsertionCandidate.getPassengerRideDuration()},
   * which already accounts for boarding dwell and intermediate stops). The arrival time and total
   * duration are derived from these — there is no implicit invariant tying the caller's clock
   * arithmetic to the path durations.
   * <p>
   * The walk paths' A* weights are used as-is for the walk portion of the cost; they already
   * encode the user's walk preferences (reluctance, safety, slope, ...) from the search that
   * produced them. The ride portion is billed at {@code carpoolReluctance}.
   */
  public CarpoolAccessEgress(
    int stop,
    int passengerDepartureTime,
    @Nullable GraphPath<State, Edge, Vertex> walkToPickup,
    List<GraphPath<State, Edge, Vertex>> sharedSegments,
    Duration rideDuration,
    @Nullable GraphPath<State, Edge, Vertex> walkFromDropoff,
    TimeAndCost penalty,
    double carpoolReluctance
  ) {
    this.stop = stop;
    this.walkToPickup = walkToPickup;
    this.sharedSegments = sharedSegments;
    this.walkFromDropoff = walkFromDropoff;
    int walkSeconds = (int) (GraphPathUtils.durationOrZero(walkToPickup).getSeconds() +
      GraphPathUtils.durationOrZero(walkFromDropoff).getSeconds());
    this.rideSeconds = (int) rideDuration.getSeconds();
    this.durationInSeconds = walkSeconds + this.rideSeconds;
    this.passengerDepartureTime = passengerDepartureTime;
    this.passengerArrivalTime = passengerDepartureTime + this.durationInSeconds;
    this.carpoolReluctance = carpoolReluctance;
    double walkWeight =
      GraphPathUtils.weightOrZero(walkToPickup) + GraphPathUtils.weightOrZero(walkFromDropoff);
    this.totalWeight = walkWeight + this.rideSeconds * carpoolReluctance;
    this.c1 = RaptorCostConverter.toRaptorCost(this.totalWeight);
    this.penalty = penalty;
  }

  @Override
  public int stop() {
    return this.stop;
  }

  @Override
  public int c1() {
    return this.c1;
  }

  @Override
  public int durationInSeconds() {
    return this.durationInSeconds;
  }

  @Override
  public int earliestDepartureTime(int requestedDepartureTime) {
    if (requestedDepartureTime > passengerDepartureTime) {
      return RaptorConstants.TIME_NOT_SET;
    }
    return passengerDepartureTime;
  }

  @Override
  public int latestArrivalTime(int requestedArrivalTime) {
    if (requestedArrivalTime < passengerArrivalTime) {
      return RaptorConstants.TIME_NOT_SET;
    }
    return passengerArrivalTime;
  }

  @Override
  public boolean hasOpeningHours() {
    return true;
  }

  public int getPassengerDepartureTime() {
    return passengerDepartureTime;
  }

  public int getPassengerArrivalTime() {
    return passengerArrivalTime;
  }

  @Override
  public RoutingAccessEgress withPenalty(TimeAndCost penalty) {
    return new CarpoolAccessEgress(
      this.stop,
      this.passengerDepartureTime,
      this.walkToPickup,
      this.sharedSegments,
      Duration.ofSeconds(this.rideSeconds),
      this.walkFromDropoff,
      penalty,
      this.carpoolReluctance
    );
  }

  /*
    TODO: Implement this function.
    It is never used for instances of CarpoolAccessEgress, but this might change in the future.
   */
  @Override
  public State getFinalState() {
    throw new UnsupportedOperationException(
      "Fetching last state of CarpoolAccessEgress is not yet implemented"
    );
  }

  @Override
  public boolean isWalkOnly() {
    return false;
  }

  @Override
  public TimeAndCost penalty() {
    return this.penalty;
  }

  @Nullable
  public GraphPath<State, Edge, Vertex> walkToPickup() {
    return this.walkToPickup;
  }

  public List<GraphPath<State, Edge, Vertex>> sharedSegments() {
    return this.sharedSegments;
  }

  @Nullable
  public GraphPath<State, Edge, Vertex> walkFromDropoff() {
    return this.walkFromDropoff;
  }

  public double getTotalWeight() {
    return this.totalWeight;
  }
}
