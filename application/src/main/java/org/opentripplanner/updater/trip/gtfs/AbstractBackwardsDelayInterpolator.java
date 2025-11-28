package org.opentripplanner.updater.trip.gtfs;

import java.util.OptionalInt;
import java.util.stream.IntStream;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;

abstract class AbstractBackwardsDelayInterpolator implements BackwardsDelayInterpolator {

  /**
   * Propagate backwards from the first stop with real-time information
   * @return The first stop position with given time if propagation is done.
   */
  public OptionalInt propagateBackwards(RealTimeTripTimesBuilder builder) {
    var max = builder.numberOfStops();
    var containsNoUpdates = IntStream.range(0, max).allMatch(
      i -> builder.hasNoDelay(i) || builder.isNoData(i)
    );
    if (containsNoUpdates) {
      return OptionalInt.empty();
    }

    var firstUpdatedIndex = getFirstUpdatedIndex(builder);
    if (firstUpdatedIndex == 0) {
      return OptionalInt.empty();
    }
    fillInMissingTimes(builder, firstUpdatedIndex);
    return OptionalInt.of(firstUpdatedIndex);
  }

  protected int getFirstUpdatedIndex(RealTimeTripTimesBuilder builder) {
    var firstUpdatedIndex = 0;
    while (builder.isNoData(firstUpdatedIndex) || builder.hasNoDelay(firstUpdatedIndex)) {
      ++firstUpdatedIndex;
      if (firstUpdatedIndex == builder.numberOfStops()) {
        throw new IllegalArgumentException(
          "No real-time updates exist in the builder, can't propagate backwards."
        );
      }
    }
    return firstUpdatedIndex;
  }

  protected abstract void fillInMissingTimes(
    RealTimeTripTimesBuilder builder,
    int firstUpdatedIndex
  );
}
