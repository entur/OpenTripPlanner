package org.opentripplanner.updater.trip.model;

import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.basic.TransitMode;

/**
 * Supplementary metadata needed to create a new route when adding a trip that cannot be
 * registered under an existing route. The route ID itself comes from
 * {@link TripCreationInfo#routeId()}.
 */
public final class RouteCreationInfo {

  @Nullable
  private final String routeName;

  @Nullable
  private final TransitMode mode;

  @Nullable
  private final String submode;

  @Nullable
  private final FeedScopedId operatorId;

  @Nullable
  private final String url;

  @Nullable
  private final FeedScopedId agencyId;

  @Nullable
  private final Integer gtfsType;

  /**
   * @param routeName The name of the route
   * @param mode The transit mode of the route
   * @param submode The submode of the route (e.g., "localBus", "expressBus")
   * @param operatorId The operator ID for the route
   * @param url The URL for the route
   * @param agencyId The agency ID for the route
   * @param gtfsType The GTFS route type (e.g., 3 for bus)
   */
  public RouteCreationInfo(
    @Nullable String routeName,
    @Nullable TransitMode mode,
    @Nullable String submode,
    @Nullable FeedScopedId operatorId,
    @Nullable String url,
    @Nullable FeedScopedId agencyId,
    @Nullable Integer gtfsType
  ) {
    this.routeName = routeName;
    this.mode = mode;
    this.submode = submode;
    this.operatorId = operatorId;
    this.url = url;
    this.agencyId = agencyId;
    this.gtfsType = gtfsType;
  }

  /**
   * Convenience constructor without URL, agencyId, and gtfsType.
   */
  public RouteCreationInfo(
    @Nullable String routeName,
    @Nullable TransitMode mode,
    @Nullable String submode,
    @Nullable FeedScopedId operatorId
  ) {
    this(routeName, mode, submode, operatorId, null, null, null);
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

  @Nullable
  public String url() {
    return url;
  }

  @Nullable
  public FeedScopedId agencyId() {
    return agencyId;
  }

  @Nullable
  public Integer gtfsType() {
    return gtfsType;
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
      Objects.equals(routeName, that.routeName) &&
      mode == that.mode &&
      Objects.equals(submode, that.submode) &&
      Objects.equals(operatorId, that.operatorId) &&
      Objects.equals(url, that.url) &&
      Objects.equals(agencyId, that.agencyId) &&
      Objects.equals(gtfsType, that.gtfsType)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(routeName, mode, submode, operatorId, url, agencyId, gtfsType);
  }

  @Override
  public String toString() {
    return (
      "RouteCreationInfo{" +
      "routeName='" +
      routeName +
      '\'' +
      ", mode=" +
      mode +
      ", submode='" +
      submode +
      '\'' +
      ", operatorId=" +
      operatorId +
      ", url='" +
      url +
      '\'' +
      ", agencyId=" +
      agencyId +
      ", gtfsType=" +
      gtfsType +
      '}'
    );
  }
}
