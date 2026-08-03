package org.opentripplanner.ext.updater.trip.unified.model.change;

import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.model.command.VehicleDescription;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;

/**
 * The removal of a trip on one service date - a cancellation or a deletion.
 * <p>
 * A removal targets exactly one trip, and which kind of trip it is can only be decided against the
 * current transit model, so {@link org.opentripplanner.ext.updater.trip.unified.factory.TripRemovalFactory} decides it
 * and the removal carries the answer:
 * <ul>
 *   <li>{@link ScheduledTripRemoval} - a trip of the static schedule</li>
 *   <li>{@link AddedTripRemoval} - a trip a previous real-time message added</li>
 * </ul>
 * The removal applies itself through {@link #apply}, which takes the removal semantics as a
 * parameter: {@link org.opentripplanner.ext.updater.trip.unified.service.TripCanceller} and
 * {@link org.opentripplanner.ext.updater.trip.unified.service.TripDeleter} differ only in the real-time state the trip
 * ends up in, and both apply to either kind of trip.
 */
public abstract sealed class TripRemoval permits ScheduledTripRemoval, AddedTripRemoval {

  private final LocalDate serviceDate;
  private final FeedScopedId tripId;

  @Nullable
  private final String dataSource;

  private final VehicleDescription vehicleDescription;

  protected TripRemoval(
    LocalDate serviceDate,
    FeedScopedId tripId,
    @Nullable String dataSource,
    VehicleDescription vehicleDescription
  ) {
    this.serviceDate = Objects.requireNonNull(serviceDate, "serviceDate must not be null");
    this.tripId = Objects.requireNonNull(tripId, "tripId must not be null");
    this.dataSource = dataSource;
    this.vehicleDescription = Objects.requireNonNull(
      vehicleDescription,
      "vehicleDescription must not be null"
    );
  }

  /**
   * Apply what the message says about the vehicle serving the trip to the trip times being built. A
   * cancellation still states the vehicle that was to serve the journey, and the removed trip keeps
   * it.
   */
  protected void applyVehicleDescription(RealTimeTripTimesBuilder builder) {
    vehicleDescription.applyTo(builder);
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
