package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.updater.trip.policy.FormatPolicy;

/**
 * A fully specified change to an existing scheduled trip, resolved from a command against the
 * transit model.
 * <p>
 * The two concrete cases are two different operations on the same trip, told apart by the entry
 * point of {@link org.opentripplanner.updater.trip.ExistingTripChangeFactory} the caller picks - not by
 * a runtime check - and each validated against the invariants of its own use case:
 * <ul>
 *   <li>{@link TripRevision} - the trip keeps running its scheduled pattern and only
 *       its times and minor pattern details change</li>
 *   <li>{@link TripModification} - the trip is rerouted on a new stop pattern for the
 *       service date</li>
 * </ul>
 */
public abstract sealed class ExistingTripChange permits TripRevision, TripModification {

  private final FormatPolicy formatPolicy;

  @Nullable
  private final String dataSource;

  private final VehicleDescription vehicleDescription;

  @Nullable
  private final I18NString tripHeadsign;

  private final LocalDate serviceDate;
  private final Trip trip;
  private final TripPattern scheduledPattern;
  private final List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates;

  protected ExistingTripChange(
    ExistingTripCommand command,
    LocalDate serviceDate,
    Trip trip,
    TripPattern scheduledPattern,
    List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates
  ) {
    this.formatPolicy = command.formatPolicy();
    this.dataSource = command.dataSource();
    this.vehicleDescription = command.vehicleDescription();
    this.tripHeadsign = command.tripHeadsign();
    this.serviceDate = Objects.requireNonNull(serviceDate, "serviceDate must not be null");
    this.trip = Objects.requireNonNull(trip, "trip must not be null");
    this.scheduledPattern = Objects.requireNonNull(
      scheduledPattern,
      "scheduledPattern must not be null"
    );
    this.resolvedStopTimeUpdates = Objects.requireNonNull(
      resolvedStopTimeUpdates,
      "resolvedStopTimeUpdates must not be null"
    );
  }

  public LocalDate serviceDate() {
    return serviceDate;
  }

  public Trip trip() {
    return trip;
  }

  /**
   * The original scheduled pattern.
   * If the current pattern is a real-time modified pattern, this returns the original.
   * Otherwise, returns the same as the trip's current pattern.
   */
  public TripPattern scheduledPattern() {
    return scheduledPattern;
  }

  /** The behavioural {@link FormatPolicy} for this update, chosen once at the parser boundary. */
  public FormatPolicy formatPolicy() {
    return formatPolicy;
  }

  /**
   * Apply what the message says about the journey as it runs today - the headsign it displays and
   * the vehicle serving it - to the trip times being built.
   */
  public void applyJourneyDescription(RealTimeTripTimesBuilder builder) {
    if (tripHeadsign != null) {
      builder.withTripHeadsign(tripHeadsign);
    }
    vehicleDescription.applyTo(builder);
  }

  public List<ResolvedStopTimeUpdate> stopTimeUpdates() {
    return resolvedStopTimeUpdates;
  }

  @Nullable
  public String dataSource() {
    return dataSource;
  }
}
