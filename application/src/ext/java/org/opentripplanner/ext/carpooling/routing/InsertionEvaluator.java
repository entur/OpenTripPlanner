package org.opentripplanner.ext.carpooling.routing;

import static org.opentripplanner.ext.carpooling.util.GraphPathUtils.calculateCumulativeDurations;
import static org.opentripplanner.ext.carpooling.util.GraphPathUtils.durationOrZero;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.astar.model.GraphPath;
import org.opentripplanner.ext.carpooling.constraints.PassengerDelayConstraints;
import org.opentripplanner.place.api.NearbyStop;
import org.opentripplanner.routing.algorithm.raptoradapter.router.street.AccessEgressType;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.state.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates pre-filtered insertion positions using A* routing.
 * <p>
 * This class is a pure evaluator that takes positions identified by heuristic
 * filtering and evaluates them using expensive A* street routing. It selects
 * the insertion that minimizes additional travel time while satisfying
 * passenger delay constraints.
 * <p>
 * This follows the established OTP pattern of separating candidate generation
 * from evaluation, similar to {@code TransferGenerator} and {@code OptimizePathDomainService}.
 */
public class InsertionEvaluator {

  private static final Logger LOG = LoggerFactory.getLogger(InsertionEvaluator.class);

  private final CarpoolRouter router;
  private final Duration stopDuration;

  /**
   * @param router routes both the new street segments created around an inserted pickup/dropoff and
   *        the driver's baseline legs.
   * @param stopDuration duration added at each intermediate stop (from the car {@code pickupTime}
   *        preference); applied between consecutive segments when computing total trip and
   *        passenger-ride durations.
   */
  public InsertionEvaluator(CarpoolRouter router, Duration stopDuration) {
    this.router = router;
    this.stopDuration = stopDuration;
  }

  /**
   * Finds the best insertion for every nearby stop that yields one. Stops are evaluated using
   * durations only; the trip's baseline path is routed at most once, and only if at least one stop
   * produced a valid insertion.
   *
   * @return one {@link InsertionCandidate} per nearby stop that has a valid insertion; empty if
   *         none do or the baseline cannot be routed.
   */
  public List<InsertionCandidate> findBestInsertions(
    TripWithViableAccessEgress tripWithViableAccessEgress
  ) {
    var tripWithVertices = tripWithViableAccessEgress.tripWithVertices();
    Duration[] baselineDurations = tripWithViableAccessEgress.baselineLegDurations();
    Duration[] baselineCumulative = calculateCumulativeDurations(baselineDurations, stopDuration);

    // Pick the best insertion per nearby stop using durations only — no baseline routing yet.
    List<StopInsertion> winners = tripWithViableAccessEgress
      .viableAccessEgress()
      .stream()
      .map(viableAccessEgress -> {
        var snap = toPassengerSnap(viableAccessEgress);
        var best = selectBestPosition(
          tripWithVertices,
          viableAccessEgress.insertionPositions(),
          snap,
          baselineDurations,
          baselineCumulative
        );
        return best == null
          ? null
          : new StopInsertion(snap, best, viableAccessEgress.transitStop());
      })
      .filter(Objects::nonNull)
      .toList();

    return materializeWinners(tripWithVertices, winners, baselineDurations);
  }

  /** A chosen insertion awaiting baseline routing. See {@link #materializeWinners}. */
  private record StopInsertion(
    PassengerSnap snap,
    SelectedPosition position,
    @Nullable NearbyStop transitStop
  ) {}

  private static PassengerSnap toPassengerSnap(ViableAccessEgress viableAccessEgress) {
    boolean isAccess = viableAccessEgress.accessEgress() == AccessEgressType.ACCESS;
    var pickup = isAccess
      ? viableAccessEgress.passengerVertex()
      : viableAccessEgress.transitVertex();
    var dropoff = isAccess
      ? viableAccessEgress.transitVertex()
      : viableAccessEgress.passengerVertex();
    return new PassengerSnap(
      pickup,
      dropoff,
      viableAccessEgress.walkToPickup(),
      viableAccessEgress.walkFromDropoff()
    );
  }

  /**
   * Finds the best insertion for pre-filtered positions, routing the trip's baseline only if one of
   * them is actually valid.
   *
   * @param tripWithVertices The carpool trip with resolved vertices
   * @param viablePositions Positions that passed heuristic checks (from InsertionPositionFinder)
   * @param snap Pickup/dropoff vertices (already snapped to car-accessible vertices by the
   *        caller) and the optional walk paths bracketing the carpool ride
   * @param baselineLegDurations OTP's routed travel duration for each baseline leg, one per leg.
   * @return The best insertion candidate, or null if none are viable after routing
   */
  @Nullable
  public InsertionCandidate findBestInsertion(
    CarpoolTripWithVertices tripWithVertices,
    List<InsertionPosition> viablePositions,
    PassengerSnap snap,
    Duration[] baselineLegDurations
  ) {
    Duration[] baselineCumulative = calculateCumulativeDurations(
      baselineLegDurations,
      stopDuration
    );
    var best = selectBestPosition(
      tripWithVertices,
      viablePositions,
      snap,
      baselineLegDurations,
      baselineCumulative
    );
    if (best == null) {
      return null;
    }
    var candidates = materializeWinners(
      tripWithVertices,
      List.of(new StopInsertion(snap, best, null)),
      baselineLegDurations
    );
    return candidates.isEmpty() ? null : candidates.getFirst();
  }

  /**
   * Routes the baseline once — via {@link #router} — and materializes each winner's geometry against
   * it. Returns empty without routing when there are no winners, so a trip with no valid insertion
   * never routes its baseline.
   *
   * @param baselineLegDurations the per-leg durations the winners were scored against; asserted
   *        (in tests, where assertions are enabled) to equal the freshly routed baseline legs.
   *        Selection scores positions with these durations while the geometry is materialized from
   *        {@link #router}; the two only agree because both routers pick the same-cost path, and the
   *        assertion catches a future routing change that breaks that before it silently corrupts
   *        selection or the reported trip duration.
   */
  private List<InsertionCandidate> materializeWinners(
    CarpoolTripWithVertices tripWithVertices,
    List<StopInsertion> winners,
    Duration[] baselineLegDurations
  ) {
    if (winners.isEmpty()) {
      return List.of();
    }
    GraphPath<State, Edge, Vertex>[] baselineSegments = router.routeLegs(
      tripWithVertices.vertices()
    );
    if (baselineSegments == null) {
      LOG.warn("Could not route baseline for trip {}", tripWithVertices.trip().getId());
      return List.of();
    }
    assert baselineDurationsMatch(
      baselineSegments,
      baselineLegDurations
    ) : describeBaselineDurationMismatch(baselineSegments, baselineLegDurations);
    return winners
      .stream()
      .map(winner ->
        materialize(
          tripWithVertices,
          winner.position(),
          winner.snap(),
          baselineSegments,
          winner.transitStop()
        )
      )
      .toList();
  }

  /**
   * True when each freshly routed baseline leg's duration equals the {@code baselineLegDurations}
   * entry the insertion was scored against. Invoked only from an {@code assert} in
   * {@link #materializeWinners}, so it runs in tests and is skipped in production.
   */
  private static boolean baselineDurationsMatch(
    GraphPath<State, Edge, Vertex>[] baselineSegments,
    Duration[] baselineLegDurations
  ) {
    if (baselineSegments.length != baselineLegDurations.length) {
      return false;
    }
    for (int i = 0; i < baselineSegments.length; i++) {
      if (!durationOrZero(baselineSegments[i]).equals(baselineLegDurations[i])) {
        return false;
      }
    }
    return true;
  }

  /**
   * Builds the assertion-failure message for {@link #baselineDurationsMatch}. Only evaluated when
   * the assertion fails, so it never runs on the production path.
   */
  private static String describeBaselineDurationMismatch(
    GraphPath<State, Edge, Vertex>[] baselineSegments,
    Duration[] baselineLegDurations
  ) {
    if (baselineSegments.length != baselineLegDurations.length) {
      return (
        "baseline leg count mismatch: routed " +
        baselineSegments.length +
        " but scored " +
        baselineLegDurations.length
      );
    }
    var sb = new StringBuilder(
      "materialized baseline durations diverge from the durations the insertion was scored " +
        "against; the baseline router and the geometry router must pick the same-cost path:"
    );
    for (int i = 0; i < baselineSegments.length; i++) {
      var routed = durationOrZero(baselineSegments[i]);
      if (!routed.equals(baselineLegDurations[i])) {
        sb
          .append(" leg ")
          .append(i)
          .append(" scored=")
          .append(baselineLegDurations[i])
          .append(" routed=")
          .append(routed)
          .append(';');
      }
    }
    return sb.toString();
  }

  /**
   * Picks the position with the smallest total trip duration that satisfies the delay constraints,
   * using durations only. Each position's detour segments are routed (and kept on the returned
   * {@link SelectedPosition}), but the baseline legs it reuses are taken from
   * {@code baselineLegDurations} rather than routed — that is deferred to {@link #materialize}.
   *
   * @return the best position, or {@code null} if none is valid.
   */
  @Nullable
  private SelectedPosition selectBestPosition(
    CarpoolTripWithVertices tripWithVertices,
    List<InsertionPosition> viablePositions,
    PassengerSnap snap,
    Duration[] baselineLegDurations,
    Duration[] baselineCumulative
  ) {
    SelectedPosition best = null;
    for (InsertionPosition position : viablePositions) {
      var evaluated = evaluatePosition(
        tripWithVertices,
        position.pickupPos(),
        position.dropoffPos(),
        snap,
        baselineLegDurations,
        baselineCumulative
      );
      if (evaluated == null) {
        continue;
      }
      if (best == null || evaluated.totalTripDuration().compareTo(best.totalTripDuration()) < 0) {
        best = evaluated;
        LOG.debug(
          "New best insertion: pickup@{}, dropoff@{}, duration={}s",
          evaluated.pickupPos(),
          evaluated.dropoffPos(),
          evaluated.totalTripDuration().getSeconds()
        );
      }
    }
    return best;
  }

  /**
   * Evaluates a single insertion using durations only. Routes the detour segments around the
   * inserted pickup/dropoff (keeping them for {@link #materialize}) and takes the untouched legs'
   * durations from {@code baselineLegDurations}, so the trip's baseline path is not routed here.
   *
   * @return the evaluated position with its detour segments and total duration, or {@code null} if
   *         a detour cannot be routed or the delay constraints are exceeded.
   */
  @Nullable
  @SuppressWarnings("unchecked")
  private SelectedPosition evaluatePosition(
    CarpoolTripWithVertices tripWithVertices,
    int pickupPos,
    int dropoffPos,
    PassengerSnap snap,
    Duration[] baselineLegDurations,
    Duration[] baselineCumulative
  ) {
    List<Vertex> modifiedPoints = new ArrayList<>(tripWithVertices.vertices());
    modifiedPoints.add(pickupPos, snap.pickupVertex());
    modifiedPoints.add(dropoffPos, snap.dropoffVertex());

    // Detour segments are routed and kept; reused legs are left null and filled from the baseline
    // path in materialize(). Their durations, needed here for the delay check, come from
    // baselineLegDurations.
    GraphPath<State, Edge, Vertex>[] detourSegments = new GraphPath[modifiedPoints.size() - 1];
    Duration[] modifiedDurations = new Duration[detourSegments.length];
    for (int i = 0; i < detourSegments.length; i++) {
      int baselineIndex = InsertionPosition.baselineSegmentIndex(i, pickupPos, dropoffPos);
      if (baselineIndex >= 0) {
        modifiedDurations[i] = baselineLegDurations[baselineIndex];
      } else {
        var segment = router.route(modifiedPoints.get(i), modifiedPoints.get(i + 1));
        if (segment == null) {
          LOG.trace("Routing failed for new segment {} → {}", i, i + 1);
          return null;
        }
        detourSegments[i] = segment;
        modifiedDurations[i] = durationOrZero(segment);
      }
    }

    Duration[] modifiedCumulative = calculateCumulativeDurations(modifiedDurations, stopDuration);
    if (
      !PassengerDelayConstraints.satisfiesConstraints(
        baselineCumulative,
        modifiedCumulative,
        pickupPos,
        dropoffPos,
        tripWithVertices.trip().stops()
      )
    ) {
      LOG.trace(
        "Insertion at pickup={}, dropoff={} rejected by delay constraints",
        pickupPos,
        dropoffPos
      );
      return null;
    }

    return new SelectedPosition(
      pickupPos,
      dropoffPos,
      detourSegments,
      modifiedCumulative[modifiedCumulative.length - 1]
    );
  }

  /**
   * Builds the final {@link InsertionCandidate} for a selected position by stitching the routed
   * baseline legs into the detour segments {@link #evaluatePosition} already routed.
   */
  private InsertionCandidate materialize(
    CarpoolTripWithVertices tripWithVertices,
    SelectedPosition selected,
    PassengerSnap snap,
    GraphPath<State, Edge, Vertex>[] baselineSegments,
    @Nullable NearbyStop transitStop
  ) {
    GraphPath<State, Edge, Vertex>[] detourSegments = selected.detourSegments();
    List<GraphPath<State, Edge, Vertex>> modifiedSegments = new ArrayList<>(detourSegments.length);
    for (int i = 0; i < detourSegments.length; i++) {
      if (detourSegments[i] != null) {
        modifiedSegments.add(detourSegments[i]);
      } else {
        int baselineIndex = InsertionPosition.baselineSegmentIndex(
          i,
          selected.pickupPos(),
          selected.dropoffPos()
        );
        modifiedSegments.add(baselineSegments[baselineIndex]);
      }
    }

    return new InsertionCandidate(
      tripWithVertices.trip(),
      selected.pickupPos(),
      selected.dropoffPos(),
      modifiedSegments,
      stopDuration,
      transitStop,
      snap.walkToPickup(),
      snap.walkFromDropoff()
    );
  }

  /**
   * A position that passed the delay check, carrying its routed detour segments (reused-leg slots
   * left {@code null}) and its total trip duration. {@link #materialize} fills the null slots with
   * the routed baseline legs to produce the final candidate.
   */
  private record SelectedPosition(
    int pickupPos,
    int dropoffPos,
    GraphPath<State, Edge, Vertex>[] detourSegments,
    Duration totalTripDuration
  ) {}
}
