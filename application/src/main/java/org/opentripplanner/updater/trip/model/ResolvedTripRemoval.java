package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.updater.trip.TripUpdateResult;

/**
 * Resolved data for cancelling or deleting a trip on one service date.
 * <p>
 * A removal targets exactly one trip, and which kind of trip it is can only be decided against the
 * current transit model, so {@link org.opentripplanner.updater.trip.TripRemovalResolver} decides it
 * and the resolved update carries the answer:
 * <ul>
 *   <li>{@link ResolvedScheduledTripRemoval} - a trip of the static schedule</li>
 *   <li>{@link ResolvedAddedTripRemoval} - a trip a previous real-time message added</li>
 * </ul>
 * The update owns its own application through {@link #apply}, which takes the removal itself as a
 * parameter: {@link org.opentripplanner.updater.trip.TripCanceller} and
 * {@link org.opentripplanner.updater.trip.TripDeleter} differ only in the real-time state the trip
 * ends up in, and both apply to either kind of trip.
 */
public abstract sealed class ResolvedTripRemoval
  permits ResolvedScheduledTripRemoval, ResolvedAddedTripRemoval {

  private final LocalDate serviceDate;
  private final FeedScopedId tripId;

  @Nullable
  private final String dataSource;

  protected ResolvedTripRemoval(
    LocalDate serviceDate,
    FeedScopedId tripId,
    @Nullable String dataSource
  ) {
    this.serviceDate = Objects.requireNonNull(serviceDate, "serviceDate must not be null");
    this.tripId = Objects.requireNonNull(tripId, "tripId must not be null");
    this.dataSource = dataSource;
  }

  /**
   * Remove the trip from the timetable on the service date and return the result as a real-time
   * update.
   *
   * @param removal marks the trip as cancelled or deleted - injects
   *                {@code TripRemover#applyRemoval}
   */
  public abstract TripUpdateResult apply(Consumer<RealTimeTripTimesBuilder> removal);

  public LocalDate serviceDate() {
    return serviceDate;
  }

  /** The id of the trip to remove. */
  public FeedScopedId tripId() {
    return tripId;
  }

  /** The data source / producer of the real-time update. */
  @Nullable
  public String dataSource() {
    return dataSource;
  }
}
