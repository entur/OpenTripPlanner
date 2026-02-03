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


  public CarpoolTreeVertexRouter(Set<Vertex> vertices) {
    var streetRequest = new StreetRequest(StreetMode.CAR);
    var request = RouteRequest.of().buildDefault();
    var reverseRequest = RouteRequest.of().withArriveBy(true).buildDefault();

    Map<Vertex, ShortestPathTree> trees = vertices.stream().collect(
      Collectors.toMap(
        vertex -> vertex,
        vertex -> StreetSearchBuilder.of()
          .withSkipEdgeStrategy(
            new DurationSkipEdgeStrategy<>(Duration.ofMinutes(60))
          )
          .withDominanceFunction(new DominanceFunctions.EarliestArrival())
          .withRequest(request)
          .withStreetRequest(streetRequest)
          .withFrom(vertex).getShortestPathTree()
      )
    );
    Map<Vertex, ShortestPathTree> reverseTrees = vertices.stream().collect(
      Collectors.toMap(
        vertex -> vertex,
        vertex -> StreetSearchBuilder.of()
          .withSkipEdgeStrategy(
            new DurationSkipEdgeStrategy<>(Duration.ofMinutes(60))
          )
          .withDominanceFunction(new DominanceFunctions.EarliestArrival())
          .withRequest(reverseRequest)
          .withStreetRequest(streetRequest)
          .withTo(vertex).getShortestPathTree()
      )
    );
    this.trees = trees;
    this.reverseTrees = reverseTrees;
  }

  public GraphPath<State, Edge, Vertex> route(
    Vertex from,
    Vertex to
  ) {

    var isReverse = false;
    var tree = this.trees.get(from);
    if(tree == null) {
      tree = this.reverseTrees.get(to);
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
    if(path == null){
      var a = 1;
    }
    return path;
  }

}
