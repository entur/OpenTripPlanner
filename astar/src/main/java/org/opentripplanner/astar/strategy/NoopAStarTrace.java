package org.opentripplanner.astar.strategy;

import org.opentripplanner.astar.spi.AStarEdge;
import org.opentripplanner.astar.spi.AStarState;
import org.opentripplanner.astar.spi.AStarTrace;
import org.opentripplanner.astar.spi.AStarVertex;

/**
 * An A* trace that does nothing. Used when tracing is disabled.
 */
public class NoopAStarTrace<
  State extends AStarState<State, Edge, Vertex>,
  Edge extends AStarEdge<State, Edge, Vertex>,
  Vertex extends AStarVertex<State, Edge, Vertex>
>
  implements AStarTrace<State, Edge, Vertex> {

  @Override
  public void dequeue(State state) {}

  @Override
  public void skipAlreadyDominated(State state) {}

  @Override
  public void traverse(State fromState, Edge edge, State[] results) {}

  @Override
  public void enqueue(State state, double estimate) {}

  @Override
  public void reject(State state) {}

  @Override
  public void goal(State state) {}

  @Override
  public void searchComplete(int nVisited) {}
}
