package org.opentripplanner.updater.trip.model;

import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.basic.TransitMode;

/**
 * Information needed to create a new route when adding a trip that cannot be
 * registered under an existing route.
 */
public final class RouteCreationInfo {

  private final FeedScopedId routeId;

  @Nullable
  private final String routeName;

  @Nullable
  private final TransitMode mode;

  @Nullable
  private final String submode;

  @Nullable
  private final FeedScopedId operatorId;

  /**
   * @param routeId The ID to use for the new route
   * @param routeName The name of the route
   * @param mode The transit mode of the route
   * @param submode The submode of the route (e.g., "localBus", "expressBus")
   * @param operatorId The operator ID for the route
   */
  public RouteCreationInfo(
    FeedScopedId routeId,
    @Nullable String routeName,
    @Nullable TransitMode mode,
    @Nullable String submode,
    @Nullable FeedScopedId operatorId
  ) {
    this.routeId = Objects.requireNonNull(routeId, "routeId must not be null");
    this.routeName = routeName;
    this.mode = mode;
    this.submode = submode;
    this.operatorId = operatorId;
  }

  public FeedScopedId routeId() {
    return routeId;
  }

  @Nullable
  public String routeName() {
    return routeName;
  }

  @Nullable
  public TransitMode mode() {
    return mode;
  }

  @Nullable
  public String submode() {
    return submode;
  }

  @Nullable
  public FeedScopedId operatorId() {
    return operatorId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RouteCreationInfo that = (RouteCreationInfo) o;
    return (
      Objects.equals(routeId, that.routeId) &&
      Objects.equals(routeName, that.routeName) &&
      mode == that.mode &&
      Objects.equals(submode, that.submode) &&
      Objects.equals(operatorId, that.operatorId)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(routeId, routeName, mode, submode, operatorId);
  }

  @Override
  public String toString() {
    return (
      "RouteCreationInfo{" +
      "routeId=" +
      routeId +
      ", routeName='" +
      routeName +
      '\'' +
      ", mode=" +
      mode +
      ", submode='" +
      submode +
      '\'' +
      ", operatorId=" +
      operatorId +
      '}'
    );
  }
}
