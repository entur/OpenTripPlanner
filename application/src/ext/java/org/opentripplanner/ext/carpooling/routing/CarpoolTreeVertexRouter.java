package org.opentripplanner.ext.carpooling.routing;

import com.esotericsoftware.minlog.Log;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.opentripplanner.astar.model.GraphPath;
import org.opentripplanner.astar.model.ShortestPathTree;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.utils.collection.Pair;

public class CarpoolTreeVertexRouter {


  private final Map<Vertex, ShortestPathTree> trees;
  private final Set<Vertex> fromVertices;
  private final HashMap<Pair<Vertex>, GraphPath<State, Edge, Vertex>> pathCache = new HashMap<>();


  public CarpoolTreeVertexRouter(Map<Vertex, ShortestPathTree> trees, Set<Vertex> fromVertices) {
    this.trees = trees;
    this.fromVertices = fromVertices;
  }

  public GraphPath<State, Edge, Vertex> route(
    Vertex from,
    Vertex to
  ) {
    var fromVertex = fromVertices.contains(from) ? from : (fromVertices.contains(to) ? to : null);
    if (fromVertex == null) {
      Log.error("FROM VERTEX NULL");
      return null;
    }
    var toVertex = fromVertex == from ? to : fromVertex;

    if (pathCache.containsKey(new Pair(fromVertex, toVertex))) {
      return pathCache.get(new Pair(fromVertex, toVertex));
    }

    var tree = trees.get(fromVertex);
    if (tree == null) {
      Log.error("NO TREE FOR FROM VERTEX");
      return null;
    }

    var path = tree.getPath(toVertex);
    pathCache.put(new Pair<>(fromVertex, toVertex), path);
    return path;
  }

}
