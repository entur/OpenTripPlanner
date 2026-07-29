package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.timetable.TripBuilder;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;

/**
 * Resolved data for creating a brand new trip that does not exist in the transit model,
 * neither in the scheduled data nor as a previously added real-time trip.
 * <p>
 * Used by {@link org.opentripplanner.updater.trip.TripCreator}.
 */
public final class ResolvedTripCreation extends ResolvedNewTrip {

  private final FeedScopedId serviceId;
  private final int serviceCode;
  private final Route route;
  private final boolean isNewRoute;
  private final List<TripOnServiceDate> replacedTrips;
  private final FeedScopedId tripOnServiceDateId;

  @Nullable
  private final TransitMode mode;

  @Nullable
  private final String submode;

  @Nullable
  private final String shortName;

  public ResolvedTripCreation(
    TripAddition parsedUpdate,
    LocalDate serviceDate,
    List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates,
    FeedScopedId serviceId,
    int serviceCode,
    Route route,
    boolean isNewRoute,
    List<TripOnServiceDate> replacedTrips
  ) {
    super(parsedUpdate, serviceDate, resolvedStopTimeUpdates);
    this.serviceId = Objects.requireNonNull(serviceId, "serviceId must not be null");
    this.serviceCode = serviceCode;
    this.route = Objects.requireNonNull(route, "route must not be null");
    this.isNewRoute = isNewRoute;
    this.replacedTrips = List.copyOf(replacedTrips);
    var creationInfo = parsedUpdate.tripCreationInfo();
    this.tripOnServiceDateId = creationInfo.tripOnServiceDateId() != null
      ? creationInfo.tripOnServiceDateId()
      : creationInfo.tripId();
    this.mode = creationInfo.mode();
    this.submode = creationInfo.submode();
    this.shortName = creationInfo.shortName();
    validate();
  }

  /** The service id valid for the created trip's service date. */
  public FeedScopedId serviceId() {
    return serviceId;
  }

  /** The service code corresponding to {@link #serviceId()}. */
  public int serviceCode() {
    return serviceCode;
  }

  /** The route the created trip runs on - found in the transit model or created for this trip. */
  public Route route() {
    return route;
  }

  /**
   * Whether {@link #route()} must be registered with the transit model as part of this update -
   * because it was created for this trip, or (GTFS-RT) because a full-dataset batch re-registers
   * every route it references.
   */
  public boolean isNewRoute() {
    return isNewRoute;
  }

  /** The dated trips the created trip replaces. References to unknown trips are dropped. */
  public List<TripOnServiceDate> replacedTrips() {
    return replacedTrips;
  }

  /**
   * The id identifying the added trip on its service date. SIRI names the dated instance of a
   * journey separately - the DatedServiceJourney - and that id identifies the added trip on
   * service date. GTFS-RT names no such entity, so the added trip on service date takes the trip
   * id instead: an added trip is held once per id (realTimeAddedTrips), and a repeat of that id
   * revises the same trip rather than adding a second service date, so the two can never collide.
   */
  public FeedScopedId tripOnServiceDateId() {
    return tripOnServiceDateId;
  }

  /**
   * Apply the mode, submode and short name the message describes the created trip with. Falls back
   * to the mode of {@link #route()} when the message states none. The headsign is not creation
   * data - see {@link #tripHeadsign()}.
   */
  public void applyTripDescription(TripBuilder builder) {
    builder.withMode(mode != null ? mode : route.getMode());
    if (submode != null) {
      builder.withNetexSubmode(submode);
    }
    if (shortName != null) {
      builder.withShortName(shortName);
    }
  }

  /**
   * A trip can only be created from a journey that calls at least twice, and - in FAIL mode - only
   * from calls at stops the transit model knows.
   * <p>
   * IGNORE-mode filtering and the minimum-stop check on the filtered calls stay in the
   * {@link org.opentripplanner.updater.trip.TripCreator}: they judge the outcome of a
   * transformation, not the message as it arrived.
   *
   * @throws UpdateException if the message cannot describe a trip
   */
  private void validate() {
    var calls = stopTimeUpdates();

    if (formatPolicy().unknownStop().failOnUnknownStop()) {
      for (int i = 0; i < calls.size(); i++) {
        if (calls.get(i).stop() == null) {
          throw UpdateException.of(tripId(), UpdateErrorType.UNKNOWN_STOP, i);
        }
      }
    }

    if (calls.size() < 2) {
      throw UpdateException.of(tripId(), UpdateErrorType.TOO_FEW_STOPS);
    }
  }

  @Override
  public String toString() {
    return (
      "ResolvedTripCreation{" + "tripId=" + tripId() + ", serviceDate=" + serviceDate() + '}'
    );
  }
}
