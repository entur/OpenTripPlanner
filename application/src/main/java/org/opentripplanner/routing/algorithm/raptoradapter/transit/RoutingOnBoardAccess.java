package org.opentripplanner.routing.algorithm.raptoradapter.transit;

import java.util.Objects;
import org.opentripplanner.raptor.api.model.RaptorOnBoardAccess;

public final class RoutingOnBoardAccess implements RaptorOnBoardAccess {

  private final int routeIndex;
  private final int tripScheduleIndex;
  private final int stopPositionInPattern;
  private final int stop;

  public RoutingOnBoardAccess(
    int routeIndex,
    int tripScheduleIndex,
    int stopPositionInPattern,
    int stop
  ) {
    this.routeIndex = routeIndex;
    this.tripScheduleIndex = tripScheduleIndex;
    this.stopPositionInPattern = stopPositionInPattern;
    this.stop = stop;
  }

  @Override
  public int c1() {
    return 0;
  }

  @Override
  public String toString() {
    return asString(true, true, null);
  }

  @Override
  public int routeIndex() {
    return routeIndex;
  }

  @Override
  public int tripScheduleIndex() {
    return tripScheduleIndex;
  }

  @Override
  public int stopPositionInPattern() {
    return stopPositionInPattern;
  }

  @Override
  public int stop() {
    return stop;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    var that = (RoutingOnBoardAccess) obj;
    return (
      this.routeIndex == that.routeIndex &&
      this.tripScheduleIndex == that.tripScheduleIndex &&
      this.stopPositionInPattern == that.stopPositionInPattern &&
      this.stop == that.stop
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(routeIndex, tripScheduleIndex, stopPositionInPattern, stop);
  }
}
