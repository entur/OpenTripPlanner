package org.opentripplanner.street.search.strategy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.street.model.StreetModelFactory.intersectionVertex;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.opentripplanner.astar.spi.DominanceFunction;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.request.StreetSearchRequest;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.street.search.state.StateData;

public class DominanceFunctionTest {

  @Test
  public void testGeneralDominanceFunction() {
    DominanceFunction minimumWeightDominanceFunction = new DominanceFunctions.MinimumWeight();
    Vertex fromVertex = intersectionVertex(1, 1);
    Vertex toVertex = intersectionVertex(2, 2);

    // Test if domination works in the general case

    StreetSearchRequest streetSearchRequest = StreetSearchRequest.of().build();
    StateData stateData = StateData.getBaseCaseStateData(streetSearchRequest);
    State stateA = new State(fromVertex, Instant.EPOCH, stateData, streetSearchRequest);
    State stateB = new State(toVertex, Instant.EPOCH, stateData, streetSearchRequest);
    stateA.weight = 1;
    stateB.weight = 2;

    assertTrue(minimumWeightDominanceFunction.betterOrEqualAndComparable(stateA, stateB));
    assertFalse(minimumWeightDominanceFunction.betterOrEqualAndComparable(stateB, stateA));
  }

  // TODO: Make unit tests for rest of dominance functionality
  // TODO: Make functional tests for concepts covered by dominance with current algorithm
  // (Specific transfers, bike rental, park and ride, turn restrictions)
}
