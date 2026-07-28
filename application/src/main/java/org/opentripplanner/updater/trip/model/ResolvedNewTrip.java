package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.updater.trip.StopTimeUpdates;
import org.opentripplanner.updater.trip.policy.FormatPolicy;

/**
 * Resolved data for the ADD_NEW_TRIP update type.
 * <p>
 * The classification between the two concrete cases is state-dependent and therefore made by
 * {@link org.opentripplanner.updater.trip.NewTripResolver}, not by the (state-free) parsers:
 * <ul>
 *   <li>{@link ResolvedTripCreation} - the trip has never been integrated in the transit model
 *       and must be created from scratch</li>
 *   <li>{@link ResolvedAddedTripUpdate} - the trip was already added in real-time and this is a
 *       subsequent update to it</li>
 * </ul>
 */
public abstract sealed class ResolvedNewTrip permits ResolvedTripCreation, ResolvedAddedTripUpdate {

  private final FormatPolicy formatPolicy;
  private final TripCreationInfo tripCreationInfo;

  @Nullable
  private final String dataSource;

  @Nullable
  private final String vehicleId;

  private final LocalDate serviceDate;
  private final List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates;
  private final boolean cancellation;

  protected ResolvedNewTrip(
    TripAddition parsedUpdate,
    LocalDate serviceDate,
    List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates
  ) {
    this.formatPolicy = parsedUpdate.formatPolicy();
    this.tripCreationInfo = parsedUpdate.tripCreationInfo();
    this.dataSource = parsedUpdate.dataSource();
    this.vehicleId = parsedUpdate.vehicleId();
    this.serviceDate = Objects.requireNonNull(serviceDate, "serviceDate must not be null");
    this.resolvedStopTimeUpdates = Objects.requireNonNull(
      resolvedStopTimeUpdates,
      "resolvedStopTimeUpdates must not be null"
    );
    this.cancellation = parsedUpdate.cancellation();
  }

  public LocalDate serviceDate() {
    return serviceDate;
  }

  /** The id of the trip this update adds. */
  public FeedScopedId tripId() {
    return tripCreationInfo.tripId();
  }

  /**
   * Whether the message cancels the journey as a whole. A cancelled extra journey is still added,
   * but in cancelled state.
   */
  public boolean isCancelledAtJourneyLevel() {
    return cancellation;
  }

  /**
   * Whether every stop of the journey is cancelled/skipped, which cancels the trip implicitly.
   */
  public boolean isCancelledAtEveryStop() {
    return ResolvedStopTimeUpdate.allSkipped(resolvedStopTimeUpdates);
  }

  /**
   * Whether the added trip does not run, be it cancelled as a whole or at every one of its stops.
   */
  public boolean isCancelled() {
    return isCancelledAtJourneyLevel() || isCancelledAtEveryStop();
  }

  /** The behavioural {@link FormatPolicy} for this update, chosen once at the parser boundary. */
  public FormatPolicy formatPolicy() {
    return formatPolicy;
  }

  /**
   * Apply the description of the vehicle serving the journey - its id and its wheelchair
   * accessibility - to the trip times being built.
   * <p>
   * Both are journey-level attributes (the GTFS-RT vehicle descriptor, the SIRI journey), so every
   * message restates them and they are re-applied on every message, whether the journey is created
   * or updated and also when it is cancelled. A message that leaves an attribute out means "no
   * information", so nothing is carried over from the previous message.
   */
  public void applyVehicleDescription(RealTimeTripTimesBuilder builder) {
    builder.withVehicleId(vehicleId());
    var wheelchairAccessibility = tripCreationInfo.wheelchairAccessibility();
    if (wheelchairAccessibility != null) {
      builder.withWheelchairAccessibility(wheelchairAccessibility);
    }
  }

  /** The calls of the added trip as they arrived, including calls at unknown stops. */
  public List<ResolvedStopTimeUpdate> stopTimeUpdates() {
    return resolvedStopTimeUpdates;
  }

  /**
   * The calls of the added trip that reference a stop known to the transit model, together with the
   * warnings raised by dropping the others. Calls at unknown stops are only dropped in IGNORE mode -
   * in FAIL mode the {@link org.opentripplanner.updater.trip.AddNewTripValidator} rejects the update
   * before the trip is built.
   */
  public StopTimeUpdates.FilteredStopTimeUpdates stopTimeUpdatesWithKnownStops() {
    return StopTimeUpdates.filterUnknownStops(resolvedStopTimeUpdates);
  }

  public TripCreationInfo tripCreationInfo() {
    return tripCreationInfo;
  }

  @Nullable
  public String dataSource() {
    return dataSource;
  }

  @Nullable
  public String vehicleId() {
    return vehicleId;
  }
}
