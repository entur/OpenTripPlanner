package org.opentripplanner.updater.trip.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.basic.TransitMode;

/**
 * Information needed to create a new trip that does not exist in the scheduled data.
 * This is used for added trips (SIRI: extra journeys, GTFS-RT: ADDED schedule relationship).
 */
public final class TripCreationInfo {

  private final FeedScopedId tripId;

  @Nullable
  private final FeedScopedId routeId;

  @Nullable
  private final RouteCreationInfo routeCreationInfo;

  @Nullable
  private final FeedScopedId tripOnServiceDateId;

  @Nullable
  private final String shortName;

  @Nullable
  private final TransitMode mode;

  @Nullable
  private final String submode;

  @Nullable
  private final FeedScopedId operatorId;

  private final List<FeedScopedId> replacedTrips;

  @Nullable
  private final FeedScopedId replacedRouteId;

  /**
   * @param tripId The ID to use for the new trip
   * @param routeId The route ID to associate the trip with
   * @param routeCreationInfo Information for creating a new route (if needed)
   * @param tripOnServiceDateId The id identifying the added trip on its service date (SIRI: the
   *                            DatedServiceJourney id). Null for GTFS-RT, which has no such
   *                            concept and identifies it by the trip id instead.
   * @param shortName A short name for the trip
   * @param mode The transit mode
   * @param submode The submode (e.g., "localBus", "expressBus")
   * @param operatorId The operator ID
   * @param replacedTrips IDs of trips that this new trip replaces
   * @param replacedRouteId The route ID of the route being replaced (from SIRI ExternalLineRef)
   */
  public TripCreationInfo(
    FeedScopedId tripId,
    @Nullable FeedScopedId routeId,
    @Nullable RouteCreationInfo routeCreationInfo,
    @Nullable FeedScopedId tripOnServiceDateId,
    @Nullable String shortName,
    @Nullable TransitMode mode,
    @Nullable String submode,
    @Nullable FeedScopedId operatorId,
    List<FeedScopedId> replacedTrips,
    @Nullable FeedScopedId replacedRouteId
  ) {
    this.tripId = Objects.requireNonNull(tripId, "tripId must not be null");
    this.routeId = routeId;
    this.routeCreationInfo = routeCreationInfo;
    this.tripOnServiceDateId = tripOnServiceDateId;
    this.shortName = shortName;
    this.mode = mode;
    this.submode = submode;
    this.operatorId = operatorId;
    this.replacedTrips = replacedTrips != null ? List.copyOf(replacedTrips) : List.of();
    this.replacedRouteId = replacedRouteId;
  }

  /**
   * Create a builder for trip creation info.
   */
  public static Builder builder(FeedScopedId tripId) {
    return new Builder(tripId);
  }

  public FeedScopedId tripId() {
    return tripId;
  }

  @Nullable
  public FeedScopedId routeId() {
    return routeId;
  }

  @Nullable
  public RouteCreationInfo routeCreationInfo() {
    return routeCreationInfo;
  }

  /**
   * The id the added trip on service date is known by, when the message names one: SIRI derives it
   * from the EstimatedVehicleJourneyCode as a DatedServiceJourney id. Null for GTFS-RT, where the
   * added trip on service date takes the id of the trip itself.
   */
  @Nullable
  public FeedScopedId tripOnServiceDateId() {
    return tripOnServiceDateId;
  }

  @Nullable
  public String shortName() {
    return shortName;
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

  public List<FeedScopedId> replacedTrips() {
    return replacedTrips;
  }

  @Nullable
  public FeedScopedId replacedRouteId() {
    return replacedRouteId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TripCreationInfo that = (TripCreationInfo) o;
    return (
      Objects.equals(tripId, that.tripId) &&
      Objects.equals(routeId, that.routeId) &&
      Objects.equals(routeCreationInfo, that.routeCreationInfo) &&
      Objects.equals(tripOnServiceDateId, that.tripOnServiceDateId) &&
      Objects.equals(shortName, that.shortName) &&
      mode == that.mode &&
      Objects.equals(submode, that.submode) &&
      Objects.equals(operatorId, that.operatorId) &&
      Objects.equals(replacedTrips, that.replacedTrips) &&
      Objects.equals(replacedRouteId, that.replacedRouteId)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      tripId,
      routeId,
      routeCreationInfo,
      tripOnServiceDateId,
      shortName,
      mode,
      submode,
      operatorId,
      replacedTrips,
      replacedRouteId
    );
  }

  @Override
  public String toString() {
    return (
      "TripCreationInfo{" +
      "tripId=" +
      tripId +
      ", routeId=" +
      routeId +
      ", routeCreationInfo=" +
      routeCreationInfo +
      ", tripOnServiceDateId=" +
      tripOnServiceDateId +
      ", shortName='" +
      shortName +
      '\'' +
      ", mode=" +
      mode +
      ", submode='" +
      submode +
      '\'' +
      ", operatorId=" +
      operatorId +
      ", replacedTrips=" +
      replacedTrips +
      ", replacedRouteId=" +
      replacedRouteId +
      '}'
    );
  }

  /**
   * Builder for TripCreationInfo.
   */
  public static class Builder {

    private final FeedScopedId tripId;
    private FeedScopedId routeId;
    private RouteCreationInfo routeCreationInfo;
    private FeedScopedId tripOnServiceDateId;
    private String shortName;
    private TransitMode mode;
    private String submode;
    private FeedScopedId operatorId;
    private List<FeedScopedId> replacedTrips = new ArrayList<>();
    private FeedScopedId replacedRouteId;

    private Builder(FeedScopedId tripId) {
      this.tripId = Objects.requireNonNull(tripId);
    }

    public Builder withRouteId(FeedScopedId routeId) {
      this.routeId = routeId;
      return this;
    }

    public Builder withRouteCreationInfo(RouteCreationInfo routeCreationInfo) {
      this.routeCreationInfo = routeCreationInfo;
      return this;
    }

    public Builder withTripOnServiceDateId(FeedScopedId tripOnServiceDateId) {
      this.tripOnServiceDateId = tripOnServiceDateId;
      return this;
    }

    public Builder withShortName(String shortName) {
      this.shortName = shortName;
      return this;
    }

    public Builder withMode(TransitMode mode) {
      this.mode = mode;
      return this;
    }

    public Builder withSubmode(String submode) {
      this.submode = submode;
      return this;
    }

    public Builder withOperatorId(FeedScopedId operatorId) {
      this.operatorId = operatorId;
      return this;
    }

    public Builder withReplacedTrips(List<FeedScopedId> replacedTrips) {
      this.replacedTrips = new ArrayList<>(replacedTrips);
      return this;
    }

    public Builder addReplacedTrip(FeedScopedId tripId) {
      this.replacedTrips.add(tripId);
      return this;
    }

    public Builder withReplacedRouteId(FeedScopedId replacedRouteId) {
      this.replacedRouteId = replacedRouteId;
      return this;
    }

    public TripCreationInfo build() {
      return new TripCreationInfo(
        tripId,
        routeId,
        routeCreationInfo,
        tripOnServiceDateId,
        shortName,
        mode,
        submode,
        operatorId,
        replacedTrips,
        replacedRouteId
      );
    }
  }
}
