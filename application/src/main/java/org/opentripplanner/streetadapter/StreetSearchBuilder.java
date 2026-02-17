package org.opentripplanner.streetadapter;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;
import org.opentripplanner.astar.AStarBuilder;
import org.opentripplanner.astar.spi.DominanceFunction;
import org.opentripplanner.astar.spi.RemainingWeightHeuristic;
import org.opentripplanner.routing.api.request.request.StreetRequest;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.request.StreetSearchRequest;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.street.search.strategy.DominanceFunctions;

public class StreetSearchBuilder extends AStarBuilder<State, Edge, Vertex, StreetSearchBuilder> {

  private StreetSearchRequest request;
  private StreetRequest streetRequest = StreetRequest.DEFAULT;

  public static StreetSearchBuilder of() {
    return new StreetSearchBuilder();
  }

  private StreetSearchBuilder() {
    super();
    setBuilder(this);
  }

  public StreetSearchBuilder withRequest(StreetSearchRequest request) {
    this.request = request;
    withArriveBy(request.arriveBy());
    return this;
  }

  public StreetSearchBuilder withStreetRequest(StreetRequest streetRequest) {
    this.streetRequest = streetRequest;
    return this;
  }

  @Override
  protected Duration streetRoutingTimeout() {
    return request.timeout();
  }

  @Override
  protected Collection<State> createInitialStates(Set<Vertex> originVertices) {
    StreetSearchRequest streetSearchRequest = StreetSearchRequest.copyOf(request)
      .withMode(streetRequest.mode())
      .withArriveBy(arriveBy())
      .build();

    return State.getInitialStates(originVertices, streetSearchRequest);
  }

  @Override
  protected void initializeHeuristic(
    RemainingWeightHeuristic<State> heuristic,
    Set<Vertex> ignored,
    Set<Vertex> destination,
    boolean arriveBy
  ) {
    if (heuristic.equals(RemainingWeightHeuristic.TRIVIAL)) {
      // No initialization needed
    } else if (heuristic instanceof EuclideanRemainingWeightHeuristic euclideanHeuristic) {
      euclideanHeuristic.initialize(streetRequest.mode(), destination, arriveBy, request);
    } else {
      throw new IllegalArgumentException("Unknown heuristic type: " + heuristic);
    }
  }

  @Override
  protected DominanceFunction<State> createDefaultDominanceFunction() {
    return new DominanceFunctions.Pareto();
  }
}
