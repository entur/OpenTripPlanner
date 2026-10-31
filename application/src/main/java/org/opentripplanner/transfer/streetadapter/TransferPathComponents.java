package org.opentripplanner.transfer.streetadapter;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.place.api.NearbyStop;
import org.opentripplanner.raptor.data.transfers.regular.TransferValueFunction;
import org.opentripplanner.routing.cost.CostLimit;
import org.opentripplanner.street.model.VehicleRoutingOptimizeType;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.edge.ElevatorAlightEdge;
import org.opentripplanner.street.model.edge.ElevatorBoardEdge;
import org.opentripplanner.street.model.edge.ElevatorHopEdge;
import org.opentripplanner.street.model.edge.EscalatorEdge;
import org.opentripplanner.street.model.edge.FreeEdge;
import org.opentripplanner.street.model.edge.StreetEdge;
import org.opentripplanner.street.model.edge.StreetTransitEntityLink;
import org.opentripplanner.street.model.vertex.IntersectionVertex;
import org.opentripplanner.street.search.TraverseMode;
import org.opentripplanner.street.search.intersection_model.IntersectionTraversalCalculator;
import org.opentripplanner.street.search.intersection_model.SimpleIntersectionTraversalCalculator;
import org.opentripplanner.street.search.request.StreetSearchRequest;
import org.opentripplanner.street.search.state.StateEditor;
import org.opentripplanner.transit.transfer.regular.spi.TransferSearch;

/**
 * Splits the street path of a transfer into the numbers the duration and the cost of the path
 * depend on, and computes the duration and the cost of the path from these numbers and the
 * preferences of a request. The numbers do not depend on the preferences, so they are computed
 * once per path, and the evaluation does not traverse the edges: it is a few multiplications.
 * <p>
 * The formulas replicate {@code StreetEdge.walkingTraversalCosts},
 * {@code StreetEdge.bicycleOrScooterTraversalCost}, the turn cost of the intersection traversal
 * calculator and the reluctance calculator. Edges that are not street edges (links, elevators,
 * escalators, pathways) do not depend on the preferences that vary by user, their duration and
 * cost are summed in two constants, found by traversing the edge alone.
 * <p>
 * <p>
 * A path is only represented if its duration and cost are exactly a function of these numbers.
 * Otherwise {@link TransferSearch#NOT_DERIVABLE} is returned, and the transfer is costed by
 * traversing the edges for every request. That is the case for a path with
 * <ul>
 *   <li>an edge that is neither a street edge nor one of the known edges that do not depend on the
 *       speed or the reluctance of the user (transit entity links, free edges, elevator and
 *       escalator edges). The main case is the pathway, whose duration is {@code distance / walk
 *       speed} when it has no traversal time, and whose cost is multiplied by the walk
 *       reluctance.</li>
 *   <li>a street edge with a cost extension (the data overlay), it adds a request dependent
 *       cost.</li>
 *   <li>a turn the intersection model does not make proportional to {@code 1 / speed}: all models
 *       except {@link SimpleIntersectionTraversalCalculator} are accepted only if the turn costs
 *       nothing and has no traffic light.</li>
 * </ul>
 * Nothing the user varies (speed, reluctance, safety, triangle, bike walking) makes an edge
 * untraversable, they only enter the cost, so a path that can be traversed for the profile can be
 * traversed for all users.
 * <p>
 * Walk layout: {@code [d, dSafe, turn, lights]}, then {@code [dStairs, dSafeStairs, turnStairs]}
 * if the path has stairs, then {@code [constTime, constWeight]} if the constants are not the
 * default ones. Bicycle layout: {@code [constTime, constWeight]} followed by groups of
 * {@code [flags, carSpeed, d, dWork, dSafe, turn, lights]}, one for each combination of flags
 * (walking the bike, stairs) and car speed cap.
 */
final class TransferPathComponents {

  /** Bike edges with a car speed below this are capped by it, and are grouped by the speed. */
  private static final double CAP_MAX = 9.0;

  /** Width of a bucket of the speed limit of a bike on a sloped edge, relative (0.5%). */
  private static final double CAP_STEP = Math.log(1.005);

  private static final int WALK_BASE = 4;
  private static final int WALK_STAIRS = 3;
  private static final int WALK_CONST = 2;
  private static final double DEFAULT_CONST_TIME = 0.0;
  private static final double DEFAULT_CONST_WEIGHT = 2.0;

  private static final int BIKE_HEADER = 2;
  private static final int BIKE_GROUP = 7;

  private static final double TRAFFIC_LIGHT_SECONDS = 15.0;

  private TransferPathComponents() {}

  // ---------------------------------------------------------------- walk

  @Nullable
  static float[] walk(NearbyStop path, StreetSearchRequest req) {
    var edges = path.edges();
    if (edges == null || edges.isEmpty()) {
      float d = (float) path.distance();
      return new float[] { d, d, 0, 0, 0, 0 };
    }
    double[] c = new double[9];
    var calc = req.intersectionTraversalCalculator();
    Edge prev = null;
    for (var ed : edges) {
      if (ed instanceof StreetEdge se) {
        int g = se.isStairs() ? 3 : 0;
        c[g] += se.getEffectiveWalkDistance();
        c[g + 1] += se.getEffectiveWalkSafetyDistance();
        if (se.hasCostExtension()) {
          return TransferSearch.NOT_DERIVABLE;
        }
        if (
          prev instanceof StreetEdge pe &&
          se.getFromVertex() instanceof IntersectionVertex iv &&
          !iv.inferredFreeFlowing()
        ) {
          double td1 = calc.computeTraversalDuration(iv, pe, se, TraverseMode.WALK, 1f, 1f);
          boolean light = iv.hasWalkingTrafficLight();
          if (!isScaledBySpeed(calc, td1, light)) {
            return TransferSearch.NOT_DERIVABLE;
          }
          if (light) {
            c[6] += 1;
            td1 -= TRAFFIC_LIGHT_SECONDS;
          }
          c[g + 2] += td1;
        }
      } else {
        if (!hasPreferenceIndependentCost(ed)) {
          return TransferSearch.NOT_DERIVABLE;
        }
        var k = constantCost(ed, req);
        if (k == null) {
          return null;
        }
        c[7] += k[0];
        c[8] += k[1];
      }
      prev = ed;
    }
    boolean stairs = c[3] != 0 || c[4] != 0 || c[5] != 0;
    boolean consts =
      Math.abs(c[7] - DEFAULT_CONST_TIME) > 1e-6 || Math.abs(c[8] - DEFAULT_CONST_WEIGHT) > 1e-6;
    int len = WALK_BASE + (stairs ? WALK_STAIRS : 0) + (consts ? WALK_CONST : 0);
    float[] out = new float[len];
    out[0] = (float) c[0];
    out[1] = (float) c[1];
    out[2] = (float) c[2];
    out[3] = (float) c[6];
    int i = WALK_BASE;
    if (stairs) {
      out[i++] = (float) c[3];
      out[i++] = (float) c[4];
      out[i++] = (float) c[5];
    }
    if (consts) {
      out[i++] = (float) c[7];
      out[i] = (float) c[8];
    }
    return out;
  }

  /** @return the function of the request, or null if the request is not supported */
  @Nullable
  static TransferValueFunction walkFunction(StreetSearchRequest q) {
    if (q.wheelchairEnabled()) {
      return null;
    }
    final double v = q.walk().speed();
    final double r = q.walk().reluctance();
    final double sf = q.walk().safetyFactor();
    final double stf = q.walk().stairsTimeFactor();
    final double sr = q.walk().stairsReluctance();
    final double trl = q.turnReluctance();
    return (p, off, len) -> {
      double d0 = p[off];
      double ds0 = p[off + 1];
      double t0 = p[off + 2];
      double l = p[off + 3];
      double d1 = 0;
      double ds1 = 0;
      double t1 = 0;
      double bt = DEFAULT_CONST_TIME;
      double bw = DEFAULT_CONST_WEIGHT;
      boolean stairs =
        len == WALK_BASE + WALK_STAIRS || len == WALK_BASE + WALK_STAIRS + WALK_CONST;
      boolean consts = len == WALK_BASE + WALK_CONST || len == WALK_BASE + WALK_STAIRS + WALK_CONST;
      int i = off + WALK_BASE;
      if (stairs) {
        d1 = p[i];
        ds1 = p[i + 1];
        t1 = p[i + 2];
        i += WALK_STAIRS;
      }
      if (consts) {
        bt = p[i];
        bw = p[i + 1];
      }
      double time = (d0 + t0) / v + (stf * (d1 + t1)) / v + TRAFFIC_LIGHT_SECONDS * l + bt;
      double w =
        r *
          ((sf * ds0 + (1 - sf) * d0 + trl * t0) / v +
            (stf * (sr * (sf * ds1 + (1 - sf) * d1) + trl * t1)) / v +
            trl * TRAFFIC_LIGHT_SECONDS * l) +
        bw;
      return TransferValueFunction.pack(
        (int) Math.ceil(time - 1e-9),
        CostLimit.toRaptorCostWholeSeconds(w)
      );
    };
  }

  // ---------------------------------------------------------------- bicycle

  @Nullable
  static float[] bike(NearbyStop path, StreetSearchRequest req) {
    var edges = path.edges();
    if (edges == null || edges.isEmpty()) {
      float d = (float) path.distance();
      return new float[] { 0, 0, 0, 0, d, d, d, 0, 0 };
    }
    var calc = req.intersectionTraversalCalculator();
    var groups = new ArrayList<double[]>();
    double bt = 0;
    double bw = 0;
    Edge prev = null;
    for (var ed : edges) {
      if (ed instanceof StreetEdge se) {
        boolean bike = se.canTraverse(TraverseMode.BICYCLE);
        double cs = bike ? bikeSpeedCap(se) : 0;
        int flags = (bike ? 0 : 1) + (se.isStairs() ? 2 : 0);
        double[] g = find(groups, flags, cs);
        g[2] += se.getEffectiveBikeDistance();
        g[3] += se.getEffectiveBikeDistanceForWorkCost();
        g[4] += se.getEffectiveBicycleSafetyDistance();
        if (se.hasCostExtension()) {
          return TransferSearch.NOT_DERIVABLE;
        }
        if (
          prev instanceof StreetEdge pe &&
          se.getFromVertex() instanceof IntersectionVertex iv &&
          !iv.inferredFreeFlowing()
        ) {
          var mode = bike ? TraverseMode.BICYCLE : TraverseMode.WALK;
          double td1 = calc.computeTraversalDuration(iv, pe, se, mode, 1f, 1f);
          boolean light = bike ? iv.hasCyclingTrafficLight() : iv.hasWalkingTrafficLight();
          if (!isScaledBySpeed(calc, td1, light)) {
            return TransferSearch.NOT_DERIVABLE;
          }
          if (light) {
            g[6] += 1;
            td1 -= TRAFFIC_LIGHT_SECONDS;
          }
          g[5] += td1;
        }
      } else {
        if (!hasPreferenceIndependentCost(ed)) {
          return TransferSearch.NOT_DERIVABLE;
        }
        var k = constantCost(ed, req);
        if (k == null) {
          return null;
        }
        bt += k[0];
        bw += k[1];
      }
      prev = ed;
    }
    float[] out = new float[BIKE_HEADER + BIKE_GROUP * groups.size()];
    out[0] = (float) bt;
    out[1] = (float) bw;
    int i = BIKE_HEADER;
    for (var g : groups) {
      for (double x : g) {
        out[i++] = (float) x;
      }
    }
    return out;
  }

  /**
   * The speed limit of a bike on the edge, or 0 if it is not below {@link #CAP_MAX}. It is the
   * {@code StreetEdge.getCyclingSpeedLimit}: the car speed times the slope factor of the edge
   * (effective bike distance / distance), which is 1 without elevation. With elevation the limit
   * differs from edge to edge, so it is rounded to a logarithmic grid of {@link #CAP_STEP} to keep
   * the number of groups of a path small. An edge is wrongly capped, or not, only if the bike speed
   * is within half a step of the limit, and then its duration is off by at most half a step.
   * Without a slope (factor 1) the limit is kept exact.
   */
  private static double bikeSpeedCap(StreetEdge se) {
    double distance = se.getDistanceMeters();
    double factor = distance > 0 ? se.getEffectiveBikeDistance() / distance : 1.0;
    double cap = se.getCarSpeed() * factor;
    if (cap >= CAP_MAX) {
      return 0;
    }
    if (Math.abs(factor - 1.0) < 1e-9) {
      return cap;
    }
    return Math.exp(Math.round(Math.log(cap) / CAP_STEP) * CAP_STEP);
  }

  private static double[] find(List<double[]> groups, int flags, double cs) {
    for (var g : groups) {
      if (g[0] == flags && g[1] == cs) {
        return g;
      }
    }
    var g = new double[BIKE_GROUP];
    g[0] = flags;
    g[1] = cs;
    groups.add(g);
    return g;
  }

  /** @return the function of the request, or null if the request is not supported */
  @Nullable
  static TransferValueFunction bikeFunction(StreetSearchRequest q) {
    if (q.wheelchairEnabled()) {
      return null;
    }
    var optimize = q.bike().optimizeType();
    if (
      optimize != VehicleRoutingOptimizeType.TRIANGLE &&
      optimize != VehicleRoutingOptimizeType.SAFE_STREETS
    ) {
      return null;
    }
    final double v = q.bike().speed();
    if (v > CAP_MAX) {
      return null;
    }
    final double r = q.bike().reluctance();
    final double tq;
    final double tsl;
    final double tsa;
    if (optimize == VehicleRoutingOptimizeType.TRIANGLE) {
      var tri = q.bike().optimizeTriangle();
      tq = tri.time();
      tsl = tri.slope();
      tsa = tri.safety();
    } else {
      tq = 0;
      tsl = 0;
      tsa = 1;
    }
    final double vw = q.bike().walking().speed();
    final double rw = q.bike().walking().reluctance();
    final double srw = q.bike().walking().stairsReluctance();
    final double stf = q.walk().stairsTimeFactor();
    final double trl = q.turnReluctance();
    return (p, off, len) -> {
      double time = p[off];
      double w = p[off + 1];
      for (int i = off + BIKE_HEADER; i < off + len; i += BIKE_GROUP) {
        int flags = (int) p[i];
        boolean walkBike = (flags & 1) != 0;
        boolean stairs = (flags & 2) != 0;
        double cs = p[i + 1];
        double d = p[i + 2];
        double dwork = p[i + 3];
        double dsafe = p[i + 4];
        double t = p[i + 5];
        double l = p[i + 6];
        double s = walkBike ? vw : cs > 0 ? Math.min(v, cs) : v;
        if (stairs) {
          s /= stf;
        }
        time += (d + t) / s + TRAFFIC_LIGHT_SECONDS * l;
        if (walkBike) {
          w += (rw * (stairs ? srw : 1) * d) / s + rw * trl * (t / s + TRAFFIC_LIGHT_SECONDS * l);
        } else {
          w +=
            r * ((tq * d + tsl * dwork + tsa * dsafe) / s) +
            r * trl * (t / s + TRAFFIC_LIGHT_SECONDS * l);
        }
      }
      return TransferValueFunction.pack(
        (int) Math.ceil(time - 1e-9),
        CostLimit.toRaptorCostWholeSeconds(w)
      );
    };
  }

  // ---------------------------------------------------------------- common

  /**
   * The turn duration is divided by the speed in the formulas of this class, and the traffic light
   * time is the constant {@link #TRAFFIC_LIGHT_SECONDS}. That is how the simple intersection model
   * works. For any other model the turn must cost nothing (and have no light), then it does not
   * matter.
   */
  private static boolean isScaledBySpeed(
    IntersectionTraversalCalculator calc,
    double durationAtSpeedOne,
    boolean light
  ) {
    return (
      calc instanceof SimpleIntersectionTraversalCalculator || (durationAtSpeedOne == 0 && !light)
    );
  }

  /**
   * True for the edges that are not street edges and whose duration and cost do not depend on the
   * preferences that vary by user. They depend on other preferences (the elevator and escalator
   * ones), which are the ones of the profile, so they are the same for all users.
   */
  private static boolean hasPreferenceIndependentCost(Edge ed) {
    return (
      ed instanceof StreetTransitEntityLink ||
      ed instanceof FreeEdge ||
      ed instanceof ElevatorBoardEdge ||
      ed instanceof ElevatorAlightEdge ||
      ed instanceof ElevatorHopEdge ||
      ed instanceof EscalatorEdge
    );
  }

  /** Time and weight of an edge that is not a street edge, traversed alone. */
  @Nullable
  private static double[] constantCost(Edge ed, StreetSearchRequest req) {
    var se = new StateEditor(ed.getFromVertex(), req);
    se.setTimeSeconds(0);
    var s0 = se.makeState();
    var res = ed.traverse(s0);
    if (res == null || res.length == 0 || res[0] == null) {
      return null;
    }
    var s1 = res[0];
    return new double[] {
      s1.getElapsedTimeMilliseconds() / 1000.0,
      s1.getWeight() - s0.getWeight(),
    };
  }
}
