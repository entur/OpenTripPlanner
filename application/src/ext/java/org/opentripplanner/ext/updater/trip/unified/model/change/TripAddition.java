package org.opentripplanner.ext.updater.trip.unified.model.change;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.model.command.AddTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.VehicleDescription;
import org.opentripplanner.ext.updater.trip.unified.policy.FormatPolicy;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;

/**
 * The addition of a trip that is not part of the static schedule, resolved from an
 * {@link AddTrip} command.
 * <p>
 * The classification between the two concrete cases is state-dependent and therefore made by
 * {@link org.opentripplanner.ext.updater.trip.unified.factory.TripAdditionFactory}, not by the (state-free) parsers:
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
   * Whether the added trip does not run: either the message cancels the journey outright, or the
   * pattern it builds from the calls leaves nobody able to board or alight - whether the latter
   * counts is the format's answer to give.
   *
   * @param stopPattern the pattern the added trip is built to run on
   */
  public boolean isCancelled(StopPattern stopPattern) {
    return (
      isCancelledAtJourneyLevel() || formatPolicy().implicitCancellation().cancelsTrip(stopPattern)
    );
  }

  /** The behavioural {@link FormatPolicy} for this update, chosen once at the parser boundary. */
  public FormatPolicy formatPolicy() {
    return formatPolicy;
  }

  /**
   * Whether the format builds the added trip anew from every message it sends about it, rather than
   * revising the trip an earlier message already added.
   */
  public boolean isRebuiltFromEveryMessage() {
    return !formatPolicy.repeatedAddition().revisesInPlace();
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
