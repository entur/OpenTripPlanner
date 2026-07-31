package org.opentripplanner.ext.updater.trip.unified.model.command;

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
  private final String tripShortName;

  @Nullable
  private final String publishedLineName;

  @Nullable
  private final TransitMode mode;

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
   * @param tripShortName The short name of the trip itself
   * @param publishedLineName The name the message publishes for the line the trip runs on
   * @param mode The transit mode
   * @param operatorId The operator ID
   * @param replacedTrips IDs of trips that this new trip replaces
   * @param replacedRouteId The route ID of the route being replaced (from SIRI ExternalLineRef)
   */
  public TripCreationInfo(
    FeedScopedId tripId,
    @Nullable FeedScopedId routeId,
    @Nullable RouteCreationInfo routeCreationInfo,
    @Nullable FeedScopedId tripOnServiceDateId,
    @Nullable String tripShortName,
    @Nullable String publishedLineName,
    @Nullable TransitMode mode,
    @Nullable FeedScopedId operatorId,
    List<FeedScopedId> replacedTrips,
    @Nullable FeedScopedId replacedRouteId
  ) {
    this.tripId = Objects.requireNonNull(tripId, "tripId must not be null");
    this.routeId = routeId;
    this.routeCreationInfo = routeCreationInfo;
    this.tripOnServiceDateId = tripOnServiceDateId;
    this.tripShortName = tripShortName;
    this.publishedLineName = publishedLineName;
    this.mode = mode;
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

  /**
   * The short name of the trip itself, when the message states one: GTFS-RT names it in the trip
   * properties. SIRI states no such thing for an extra journey - the name it publishes names the
   * line, see {@link #publishedLineName()}.
   */
  @Nullable
  public String tripShortName() {
    return tripShortName;
  }

  /**
   * The name the message publishes for the line the created trip runs on: the SIRI
   * PublishedLineName. It names the line, not the trip, and is only used to name a route that has
   * to be created for the trip. GTFS-RT names a route it has to create through
   * {@link RouteCreationInfo#routeName()} instead.
   */
  @Nullable
  public String publishedLineName() {
    return publishedLineName;
  }

  @Nullable
  public TransitMode mode() {
    return mode;
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
      Objects.equals(tripShortName, that.tripShortName) &&
      Objects.equals(publishedLineName, that.publishedLineName) &&
      mode == that.mode &&
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
      tripShortName,
      publishedLineName,
      mode,
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
      ", tripShortName='" +
      tripShortName +
      '\'' +
      ", publishedLineName='" +
      publishedLineName +
      '\'' +
      ", mode=" +
      mode +
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
    private String tripShortName;
    private String publishedLineName;
    private TransitMode mode;
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

    public Builder withTripShortName(String tripShortName) {
      this.tripShortName = tripShortName;
      return this;
    }

    public Builder withPublishedLineName(String publishedLineName) {
      this.publishedLineName = publishedLineName;
      return this;
    }

    public Builder withMode(TransitMode mode) {
      this.mode = mode;
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
        tripShortName,
        publishedLineName,
        mode,
        operatorId,
        replacedTrips,
        replacedRouteId
      );
    }
  }
}
