package org.opentripplanner.astar.spi;

import org.opentripplanner.astar.strategy.NoopAStarTrace;

/**
 * Callback interface for tracing A* search execution. When enabled, receives
 * notifications for every significant search event: state dequeuing, edge
 * traversal, enqueuing, domination, and goal discovery.
 * <p>
 * Use {@link #NOOP} when tracing is disabled — all methods are no-ops with
 * zero overhead beyond the method call.
 */
public interface AStarTrace<
  State extends AStarState<State, Edge, Vertex>,
  Edge extends AStarEdge<State, Edge, Vertex>,
  Vertex extends AStarVertex<State, Edge, Vertex>
> {
  @SuppressWarnings("rawtypes")
  AStarTrace NOOP = new NoopAStarTrace();

  /** Called when a state is dequeued from the priority queue and not dominated. */
  void dequeue(State state);

  /** Called when a dequeued state is already dominated and will be skipped. */
  void skipAlreadyDominated(State state);

  /** Called after an edge is traversed, with the resulting states (may be empty). */
  void traverse(State fromState, Edge edge, State[] results);

  /** Called when a new state is enqueued into the priority queue. */
  void enqueue(State state, double estimate);

  /** Called when a state is rejected by the shortest path tree (dominated on insertion). */
  void reject(State state);

  /** Called when a goal state is found. */
  void goal(State state);

  /** Called when the search completes. */
  void searchComplete(int nVisited);
}
