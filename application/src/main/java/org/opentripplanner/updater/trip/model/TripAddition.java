package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.updater.trip.StopTimeUpdates;
import org.opentripplanner.updater.trip.policy.FormatPolicy;

/**
 * The addition of a trip that is not part of the static schedule, resolved from an
 * {@link AddTrip} command.
 * <p>
 * The classification between the two concrete cases is state-dependent and therefore made by
 * {@link org.opentripplanner.updater.trip.TripAdditionFactory}, not by the (state-free) parsers:
 * <ul>
 *   <li>{@link TripCreation} - the trip has never been integrated in the transit model
 *       and must be created from scratch</li>
 *   <li>{@link AddedTripRevision} - the trip was already added in real-time and this is a
 *       subsequent update to it</li>
 * </ul>
 */
public abstract sealed class TripAddition permits TripCreation, AddedTripRevision {

  private final FormatPolicy formatPolicy;
  private final FeedScopedId tripId;

  @Nullable
  private final String dataSource;

  private final VehicleDescription vehicleDescription;

  @Nullable
  private final I18NString tripHeadsign;

  private final LocalDate serviceDate;
  private final List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates;
  private final boolean cancellation;

  protected TripAddition(
    AddTrip command,
    LocalDate serviceDate,
    List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates
  ) {
    this.formatPolicy = command.formatPolicy();
    this.tripId = command.tripCreationInfo().tripId();
    this.dataSource = command.dataSource();
    this.vehicleDescription = command.vehicleDescription();
    this.tripHeadsign = command.tripHeadsign();
    this.serviceDate = Objects.requireNonNull(serviceDate, "serviceDate must not be null");
    this.resolvedStopTimeUpdates = Objects.requireNonNull(
      resolvedStopTimeUpdates,
      "resolvedStopTimeUpdates must not be null"
    );
    this.cancellation = command.cancellation();
  }

  public LocalDate serviceDate() {
    return serviceDate;
  }

  /** The id of the trip this update adds. */
  public FeedScopedId tripId() {
    return tripId;
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
   * Apply what the message says about the journey as it runs today - the headsign it displays and
   * the vehicle serving it - to the trip times being built. Applied on every message, whether the
   * journey is created or updated, and also when it is cancelled.
   */
  public void applyJourneyDescription(RealTimeTripTimesBuilder builder) {
    if (tripHeadsign != null) {
      builder.withTripHeadsign(tripHeadsign);
    }
    vehicleDescription.applyTo(builder);
  }

  /** The headsign the journey displays today, if the message states one. */
  @Nullable
  public I18NString tripHeadsign() {
    return tripHeadsign;
  }

  /** The calls of the added trip as they arrived, including calls at unknown stops. */
  public List<ResolvedStopTimeUpdate> stopTimeUpdates() {
    return resolvedStopTimeUpdates;
  }

  /**
   * The calls of the added trip that reference a stop known to the transit model, together with the
   * warnings raised by dropping the others. Calls at unknown stops are only dropped in IGNORE mode -
   * in FAIL mode {@link TripCreation} rejects the update before the trip is built.
   */
  public StopTimeUpdates.FilteredStopTimeUpdates stopTimeUpdatesWithKnownStops() {
    return StopTimeUpdates.filterUnknownStops(resolvedStopTimeUpdates);
  }

  @Nullable
  public String dataSource() {
    return dataSource;
  }
}
