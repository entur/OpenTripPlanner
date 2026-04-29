package org.opentripplanner.updater.trip.siri.updater;

import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.trip.UpdateIncrementality;
import org.opentripplanner.updater.trip.siri.EntityResolver;
import org.opentripplanner.updater.trip.siri.SiriFuzzyTripMatcher;
import org.opentripplanner.updater.trip.siri.SiriRealTimeTripUpdateAdapter;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;

/**
 * A consumer of estimated timetables that applies the real-time updates to the transit model.
 */
public class EstimatedTimetableHandler {

  private final SiriRealTimeTripUpdateAdapter adapter;

  @Nullable
  private final SiriFuzzyTripMatcher fuzzyTripMatcher;

  private final EntityResolver entityResolver;

  /**
   * The ID for the static feed to which these real time updates are applied
   */
  private final String feedId;

  public EstimatedTimetableHandler(
    SiriRealTimeTripUpdateAdapter adapter,
    @Nullable SiriFuzzyTripMatcher fuzzyTripMatcher,
    EntityResolver entityResolver,
    String feedId
  ) {
    this.adapter = adapter;
    this.fuzzyTripMatcher = fuzzyTripMatcher;
    this.entityResolver = entityResolver;
    this.feedId = feedId;
  }

  /**
   * Apply the update to the transit model.
   */
  public UpdateResult applyUpdate(
    List<EstimatedTimetableDeliveryStructure> estimatedTimetableDeliveries,
    UpdateIncrementality updateMode
  ) {
    return adapter.applyEstimatedTimetable(
      fuzzyTripMatcher,
      entityResolver,
      feedId,
      updateMode,
      estimatedTimetableDeliveries
    );
  }
}
