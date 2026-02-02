package org.opentripplanner.ext.carpooling.routing;

import com.esotericsoftware.minlog.Log;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.opentripplanner.astar.model.GraphPath;
import org.opentripplanner.astar.model.ShortestPathTree;
import org.opentripplanner.astar.strategy.DurationSkipEdgeStrategy;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.routing.api.request.request.StreetRequest;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.StreetSearchBuilder;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.street.search.strategy.DominanceFunctions;
import org.opentripplanner.utils.collection.Pair;

public class CarpoolTreeVertexRouter {


  private final Map<Vertex, ShortestPathTree> trees;
  private final Map<Vertex, ShortestPathTree> reverseTrees;
  private final HashMap<Pair<Vertex>, GraphPath<State, Edge, Vertex>> pathCache = new HashMap<>();


  public CarpoolTreeVertexRouter(Set<Vertex> vertices, RouteRequest routeRequest) {
    var streetRequest = new StreetRequest(StreetMode.CAR);
    Map<Vertex, ShortestPathTree> trees = vertices.stream().collect(
      Collectors.toMap(
        vertex -> vertex,
        vertex -> StreetSearchBuilder.of()
          .withSkipEdgeStrategy(
            new DurationSkipEdgeStrategy<>(Duration.ofMinutes(60))
          )
          .withDominanceFunction(new DominanceFunctions.EarliestArrival())
          .withRequest(routeRequest)
          .withStreetRequest(streetRequest)
          .withFrom(vertex).getShortestPathTree()
      )
    );
    try {
      Map<Vertex, ShortestPathTree> reverseTrees = vertices.stream().collect(
        Collectors.toMap(
          vertex -> vertex,
          vertex -> StreetSearchBuilder.of()
            .withSkipEdgeStrategy(
              new DurationSkipEdgeStrategy<>(Duration.ofMinutes(60))
            )
            .withDominanceFunction(new DominanceFunctions.EarliestArrival())
            .withRequest(routeRequest)
            .withStreetRequest(streetRequest)
            .withTo(vertex).getShortestPathTree()
        )
      );
    }catch (Exception e){
      var a = 1;
    }
    this.trees = trees;
    this.reverseTrees = null;
  }

  public GraphPath<State, Edge, Vertex> route(
    Vertex from,
    Vertex to
  ) {

    var isReverse = false;
    var tree = this.trees.get(from);
    if(tree == null) {
      tree = this.reverseTrees.get(from);
      isReverse = true;
    }
    if(tree == null) {
      Log.error("tree is NULL");
      return null;
    }

    if (pathCache.containsKey(new Pair(from, to))) {
      return pathCache.get(new Pair(from, to));
    }

    var path = isReverse ? tree.getPath(from) : tree.getPath(to);
    pathCache.put(new Pair<>(from, to), path);
    return path;
  }

}
