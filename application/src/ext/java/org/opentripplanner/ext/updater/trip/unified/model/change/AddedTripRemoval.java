package org.opentripplanner.ext.updater.trip.unified.model.change;

import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.TripTimes;

/**
 * The removal of a trip that a previous real-time message added (an extra journey).
 * The trip is not part of the static schedule, so there is nothing to revert to - it is removed on
 * the real-time pattern it was added to.
 */
public final class AddedTripRemoval extends TripRemoval {

  /** The real-time pattern the trip was added to. */
  private final TripPattern pattern;

  /** The real-time times of the added trip, the baseline the removal is built from. */
  private final TripTimes tripTimes;

  public AddedTripRemoval(
    LocalDate serviceDate,
    FeedScopedId tripId,
    TripPattern pattern,
    TripTimes tripTimes,
    @Nullable String dataSource
  ) {
    super(serviceDate, tripId, dataSource);
    this.pattern = Objects.requireNonNull(pattern, "pattern must not be null");
    this.tripTimes = Objects.requireNonNull(tripTimes, "tripTimes must not be null");
  }

  @Override
  public TripUpdateResult apply(Consumer<RealTimeTripTimesBuilder> removal) {
    var builder = tripTimes.createRealTimeFromScheduledTimes();
    removal.accept(builder);

    // An extra journey keeps the "added" flag when it is removed: it was never part of the static
    // schedule, so the removed trip is still an added one.
    builder.withAdded();

    var realTimeTripUpdate = RealTimeTripUpdate.of(pattern, builder.build(), serviceDate())
      .withProducer(dataSource())
      .build();

    return new TripUpdateResult(realTimeTripUpdate);
  }

  @Override
  public String toString() {
    return (
      "AddedTripRemoval{" +
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
