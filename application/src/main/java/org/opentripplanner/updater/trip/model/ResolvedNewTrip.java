package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
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

  /**
   * Returns true if every stop in the update is cancelled/skipped.
   * When true, the trip should be treated as implicitly cancelled at the trip level.
   */
  public boolean isAllStopsCancelled() {
    return ResolvedStopTimeUpdate.allSkipped(resolvedStopTimeUpdates);
  }

  /**
   * Whether the journey is cancelled at the trip level. A cancelled extra journey is still added,
   * but in cancelled state.
   */
  public boolean isCancellation() {
    return cancellation;
  }

  /** The behavioural {@link FormatPolicy} for this update, chosen once at the parser boundary. */
  public FormatPolicy formatPolicy() {
    return formatPolicy;
  }

  public List<ResolvedStopTimeUpdate> stopTimeUpdates() {
    return resolvedStopTimeUpdates;
  }

  public TripCreationInfo tripCreationInfo() {
    return tripCreationInfo;
  }

  @Nullable
  public String dataSource() {
    return dataSource;
  }
}
