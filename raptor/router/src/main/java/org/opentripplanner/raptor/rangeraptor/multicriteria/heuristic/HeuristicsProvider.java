package org.opentripplanner.raptor.rangeraptor.multicriteria.heuristic;

import java.util.Objects;
import java.util.function.Supplier;
import org.opentripplanner.raptor.api.request.Optimization;
import org.opentripplanner.raptor.rangeraptor.debug.DebugHandlerFactory;
import org.opentripplanner.raptor.rangeraptor.destination.DestinationArrivalPaths;
import org.opentripplanner.raptor.rangeraptor.internalapi.HeuristicAtStop;
import org.opentripplanner.raptor.rangeraptor.internalapi.Heuristics;
import org.opentripplanner.raptor.rangeraptor.internalapi.WorkerLifeCycle;
import org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop.McStopArrival;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;

/**
 * A wrapper around {@link Heuristics} to cache elements to avoid recalculation of heuristic
 * properties.
 *
 * @param <T> The TripSchedule type defined by the user of the raptor API.
 */
public final class HeuristicsProvider<T extends RaptorTripSchedule> {

  /** The values kept for each stop: min travel duration, min number of transfers and min cost. */
  private static final int N_VALUES = 3;

  /** In place of the first value of a stop: its values are not fetched from the heuristics yet. */
  private static final int NOT_FETCHED = 0;

  /** In place of the first value of a stop: the heuristic search did not reach the stop. */
  private static final int UNREACHED = Integer.MIN_VALUE;

  private final Heuristics heuristics;
  private final DestinationArrivalPaths<T> paths;

  /**
   * The heuristics of the stops, {@link #N_VALUES} for each stop. The values of a stop are read
   * for every stop arrival, so they are kept together in one array. The first is the min travel
   * duration plus one: an array is created with zeros, and zero is {@link #NOT_FETCHED}.
   */
  private final int[] stops;
  private final DebugHandlerFactory<T> debugHandlerFactory;

  private int round;

  public HeuristicsProvider() {
    this.heuristics = null;
    this.paths = null;
    this.stops = null;
    this.debugHandlerFactory = null;
  }

  public HeuristicsProvider(
    Heuristics heuristics,
    DestinationArrivalPaths<T> paths,
    WorkerLifeCycle lifeCycle,
    DebugHandlerFactory<T> debugHandlerFactory
  ) {
    this.heuristics = Objects.requireNonNull(heuristics);
    this.paths = Objects.requireNonNull(paths);
    this.stops = new int[heuristics.size() * N_VALUES];
    this.debugHandlerFactory = Objects.requireNonNull(debugHandlerFactory);

    // Use life-cycle events to inject the range-raptor round
    lifeCycle.onPrepareForNextRound(r -> this.round = r);
  }

  /**
   * This is a very effective optimization, enabled by the
   * {@link Optimization#PARETO_CHECK_AGAINST_DESTINATION}.
   */
  public boolean rejectDestinationArrivalBasedOnHeuristic(McStopArrival<T> arrival) {
    if (heuristics == null || paths.isEmpty()) {
      return false;
    }
    boolean rejected = !qualify(arrival.stop(), arrival.arrivalTime(), arrival.c1(), arrival.c2());

    if (rejected) {
      debugRejectByOptimization(arrival);
    }
    return rejected;
  }

  /**
   * The same as {@link #rejectDestinationArrivalBasedOnHeuristic(McStopArrival)} for an arrival
   * that is not created yet. The values given must be those of the arrival the supplier creates;
   * it is created only if the arrival is rejected and the stop is debugged.
   */
  public boolean rejectDestinationArrivalBasedOnHeuristic(
    int stop,
    int arrivalTime,
    int c1,
    int c2,
    Supplier<McStopArrival<T>> arrival
  ) {
    if (heuristics == null || paths.isEmpty()) {
      return false;
    }
    boolean rejected = !qualify(stop, arrivalTime, c1, c2);

    if (rejected && debugHandlerFactory.isDebugStopArrival(stop)) {
      debugRejectByOptimization(arrival.get());
    }
    return rejected;
  }

  /* private methods */

  private void debugRejectByOptimization(McStopArrival<T> arrival) {
    if (debugHandlerFactory.isDebugStopArrival(arrival.stop())) {
      String details = rejectErrorMessage(arrival.stop()) + ", Existing paths: " + paths;

      debugHandlerFactory
        .debugStopArrival()
        .reject(
          arrival,
          null,
          "The element is rejected because the destination is not reachable within the limit " +
            "based on heuristic. Details: " +
            details
        );
    }
  }

  /**
   * This is used to make an optimistic guess for the best possible arrival at the destination,
   * using the given arrival and a pre-calculated heuristics.
   * <p>
   * The C2 value is passed through since we don't have any clue on what it's used for. There is
   * no heuristic calculation for the C2 value. Destination pruning can only be used if this is ok.
   */
  private boolean qualify(int stop, int arrivalTime, int cost, int c2) {
    final int i = stop * N_VALUES;
    int minTravelDurationPlusOne = stops[i];

    if (minTravelDurationPlusOne == NOT_FETCHED) {
      minTravelDurationPlusOne = fetch(stop);
    }
    if (minTravelDurationPlusOne == UNREACHED) {
      return false;
    }
    int minArrivalTime = arrivalTime + minTravelDurationPlusOne - 1;
    int minNumberOfTransfers = round - 1 + stops[i + 1];
    int minCost = cost + stops[i + 2];
    return paths.qualify(minArrivalTime, minNumberOfTransfers, minCost, c2);
  }

  private String rejectErrorMessage(int stop) {
    HeuristicAtStop h = heuristics.createHeuristicAtStop(stop);
    return h == HeuristicAtStop.UNREACHED
      ? "The stop was not reached in the heuristic calculation."
      : h.toString();
  }

  /**
   * Fetch the values of a stop from the heuristics, and return the first of them as it is kept.
   */
  private int fetch(int stop) {
    final int i = stop * N_VALUES;
    HeuristicAtStop h = heuristics.createHeuristicAtStop(stop);

    if (h == HeuristicAtStop.UNREACHED) {
      stops[i] = UNREACHED;
    } else {
      stops[i] = h.minTravelDuration() + 1;
      stops[i + 1] = h.minNumTransfers();
      stops[i + 2] = h.minCost();
    }
    return stops[i];
  }
}
