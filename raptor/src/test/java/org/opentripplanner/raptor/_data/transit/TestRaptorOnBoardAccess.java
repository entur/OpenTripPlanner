package org.opentripplanner.raptor._data.transit;

import org.opentripplanner.raptor.api.model.RaptorOnBoardAccess;

public class TestRaptorOnBoardAccess implements RaptorOnBoardAccess {
  private final int stop;
  private final int routeIndex;
  private final int generalizedCost;

  public TestRaptorOnBoardAccess(int stop, int routeIndex, int generalizedCost) {
    this.stop = stop;
    this.routeIndex = routeIndex;
    this.generalizedCost = generalizedCost;
  }

  @Override
  public int routeIndex() {
    return routeIndex;
  }

  @Override
  public int stop() {
    return stop;
  }

  @Override
  public int c1() {
    return generalizedCost;
  }

  @Override
  public String toString() {
    return asString(true, true, null);
  }
}
