package org.opentripplanner.ext.carpooling.routing;

import com.esotericsoftware.minlog.Log;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.opentripplanner.astar.model.GraphPath;
import org.opentripplanner.astar.model.ShortestPathTree;
import org.opentripplanner.astar.strategy.DurationSkipEdgeStrategy;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.request.StreetRequest;
import org.opentripplanner.street.model.StreetMode;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.street.search.strategy.DominanceFunctions;
import org.opentripplanner.streetadapter.StreetSearchBuilder;
import org.opentripplanner.utils.collection.Pair;

public class CarpoolTreeStreetRouter implements CarpoolRouter {

  private final Map<Vertex, ShortestPathTree<State, Edge, Vertex>> trees = new HashMap<>();
  private final Map<Vertex, ShortestPathTree<State, Edge, Vertex>> reverseTrees = new HashMap<>();
  private final HashMap<Pair<Vertex>, GraphPath<State, Edge, Vertex>> pathCache = new HashMap<>();

  public enum Direction {
    /**
     * We want to calculate paths from the vertex
     */
    FROM,
    /**
     * We want to calculate paths to the vertex
     */
    TO,
    /**
     *  We want to calculate paths both from and to the vertex
     */
    BOTH,
  }

  private ShortestPathTree<State, Edge, Vertex> createTree(
    Vertex vertex,
    Boolean reverse,
    Duration searchLimit
  ) {
    var streetRequest = new StreetRequest(StreetMode.CARPOOL);
    var routeRequest = reverse
      ? RouteRequest.of().withArriveBy(true).buildDefault()
      : RouteRequest.of().buildDefault();
    var builder = StreetSearchBuilder.of()
      .withSkipEdgeStrategy(new DurationSkipEdgeStrategy<>(searchLimit))
      .withDominanceFunction(new DominanceFunctions.EarliestArrival())
      .withRequest(routeRequest)
      .withStreetRequest(streetRequest);

    if (reverse) {
      return builder.withTo(vertex).getShortestPathTree();
    }

    return builder.withFrom(vertex).getShortestPathTree();
  }

  public void addVertex(Vertex vertex, Direction direction, Duration searchLimit) {
    if (
      !this.trees.containsKey(vertex) &&
      (direction == Direction.FROM || direction == Direction.BOTH)
    ) {
      this.trees.put(vertex, createTree(vertex, false, searchLimit));
    }

    if (
      !this.reverseTrees.containsKey(vertex) &&
      (direction == Direction.TO || direction == Direction.BOTH)
    ) {
      this.reverseTrees.put(vertex, createTree(vertex, true, searchLimit));
    }
  }

  @Override
  public GraphPath<State, Edge, Vertex> route(Vertex from, Vertex to) {
    if (pathCache.containsKey(new Pair<>(from, to))) {
      return pathCache.get(new Pair<>(from, to));
    }

    var isReverse = false;
    var tree = this.trees.get(from);
    if (tree == null) {
      tree = this.reverseTrees.get(to);
      isReverse = true;
    }
    if (tree == null) {
      Log.error(
        String.format("tree is null for vertices from %s to %s", from.toString(), to.toString())
      );
      return null;
    }

    var path = isReverse ? tree.getPath(from) : tree.getPath(to);
    pathCache.put(new Pair<>(from, to), path);
    return path;
  }
}
