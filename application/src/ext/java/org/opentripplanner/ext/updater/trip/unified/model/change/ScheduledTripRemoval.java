package org.opentripplanner.ext.updater.trip.unified.model.change;

import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;

/**
 * The removal of a trip of the static schedule.
 * <p>
 * Note: extra call cancellations (SIRI messages with extra calls AND {@code isCancellation=true})
 * are not removals - they are handled by {@link org.opentripplanner.ext.updater.trip.unified.service.TripModifier}.
 */
public final class ScheduledTripRemoval extends TripRemoval {

  /** The scheduled pattern the trip runs on. */
  private final TripPattern pattern;

  /** The scheduled times of the trip, the baseline the removal is built from. */
  private final TripTimes tripTimes;

  public ScheduledTripRemoval(
    LocalDate serviceDate,
    Trip trip,
    TripPattern pattern,
    TripTimes tripTimes,
    @Nullable String dataSource
  ) {
    super(serviceDate, Objects.requireNonNull(trip, "trip must not be null").getId(), dataSource);
    this.pattern = Objects.requireNonNull(pattern, "pattern must not be null");
    this.tripTimes = Objects.requireNonNull(tripTimes, "tripTimes must not be null");
  }

  @Override
  public TripUpdateResult apply(Consumer<RealTimeTripTimesBuilder> removal) {
    var builder = tripTimes.createRealTimeFromScheduledTimes();
    removal.accept(builder);

    // Removing a scheduled trip always reverts previous real-time modifications (quay changes,
    // time updates), so that any existing RT-modified pattern entry for this trip is cleared from
    // the snapshot and the trip is removed from the pattern it is scheduled to run.
    var realTimeTripUpdate = RealTimeTripUpdate.of(pattern, builder.build(), serviceDate())
      .withProducer(dataSource())
      .withRevertPreviousRealTimeUpdates(true)
      .build();

    return new TripUpdateResult(realTimeTripUpdate);
  }

  @Override
  public String toString() {
    return (
      "ScheduledTripRemoval{" +
      "serviceDate=" +
      serviceDate() +
      ", tripId=" +
      tripId() +
      ", pattern=" +
      pattern.getId() +
      '}'
    );
  }
}
