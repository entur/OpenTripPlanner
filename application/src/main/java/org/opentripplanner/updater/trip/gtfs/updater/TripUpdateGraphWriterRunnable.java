package org.opentripplanner.updater.trip.gtfs.updater;

import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.opentripplanner.updater.GraphWriterRunnable;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.trip.UpdateIncrementality;
import org.opentripplanner.updater.trip.gtfs.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.ForwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.GtfsRealTimeTripUpdateAdapter;
import org.opentripplanner.updater.trip.gtfs.GtfsRealtimeFuzzyTripMatcher;

public class TripUpdateGraphWriterRunnable implements GraphWriterRunnable {

  private final UpdateIncrementality updateIncrementality;

  /**
   * The list with updates to apply to the graph
   */
  private final List<TripUpdate> updates;

  @Nullable
  private final GtfsRealtimeFuzzyTripMatcher fuzzyTripMatcher;

  private final ForwardsDelayPropagationType forwardsDelayPropagationType;
  private final BackwardsDelayPropagationType backwardsDelayPropagationType;

  private final String feedId;
  private final Consumer<UpdateResult> sendMetrics;
  private final GtfsRealTimeTripUpdateAdapter adapter;

  public TripUpdateGraphWriterRunnable(
    GtfsRealTimeTripUpdateAdapter adapter,
    @Nullable GtfsRealtimeFuzzyTripMatcher fuzzyTripMatcher,
    ForwardsDelayPropagationType forwardsDelayPropagationType,
    BackwardsDelayPropagationType backwardsDelayPropagationType,
    UpdateIncrementality updateIncrementality,
    List<TripUpdate> updates,
    String feedId,
    Consumer<UpdateResult> sendMetrics
  ) {
    this.adapter = adapter;
    this.fuzzyTripMatcher = fuzzyTripMatcher;
    this.forwardsDelayPropagationType = forwardsDelayPropagationType;
    this.backwardsDelayPropagationType = backwardsDelayPropagationType;
    this.updateIncrementality = updateIncrementality;
    this.updates = Objects.requireNonNull(updates);
    this.feedId = Objects.requireNonNull(feedId);
    this.sendMetrics = sendMetrics;
  }

  @Override
  public void run() {
    var result = adapter.applyTripUpdates(
      fuzzyTripMatcher,
      forwardsDelayPropagationType,
      backwardsDelayPropagationType,
      updateIncrementality,
      updates,
      feedId
    );
    sendMetrics.accept(result);
  }
}
