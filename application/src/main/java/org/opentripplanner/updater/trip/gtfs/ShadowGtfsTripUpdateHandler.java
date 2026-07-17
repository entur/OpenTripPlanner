package org.opentripplanner.updater.trip.gtfs;

import static org.opentripplanner.updater.trip.UpdateIncrementality.DIFFERENTIAL;
import static org.opentripplanner.updater.trip.UpdateIncrementality.FULL_DATASET;

import com.google.transit.realtime.GtfsRealtime;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.spi.UpdateSuccess;
import org.opentripplanner.updater.trip.UpdateIncrementality;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;
import org.opentripplanner.updater.trip.regression.RealTimeTripUpdateComparator;
import org.opentripplanner.updater.trip.regression.RecordingTimetableSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Update-scoped task produced by {@link ShadowGtfsTripUpdateAdapter#forUpdate}. Per-trip
 * interleaving guarantees that both the primary and the shadow path see identical buffer state:
 * <ol>
 *   <li>Shadow runs first (reads buffer, produces record, does NOT write)</li>
 *   <li>Primary runs second (reads same buffer, produces record, writes to buffer)</li>
 *   <li>Compare the two records</li>
 * </ol>
 */
class ShadowGtfsTripUpdateHandler implements GtfsTripUpdateHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ShadowGtfsTripUpdateHandler.class);

  private final GtfsTripUpdateHandler primaryHandler;
  private final GtfsNewTripUpdateHandler shadowHandler;
  private final RecordingTimetableSnapshot recordingBuffer;

  @Nullable
  private final Path outputDirectory;

  ShadowGtfsTripUpdateHandler(
    GtfsTripUpdateHandler primaryHandler,
    GtfsNewTripUpdateHandler shadowHandler,
    RecordingTimetableSnapshot recordingBuffer,
    @Nullable Path outputDirectory
  ) {
    this.primaryHandler = primaryHandler;
    this.shadowHandler = shadowHandler;
    this.recordingBuffer = recordingBuffer;
    this.outputDirectory = outputDirectory;
  }

  @Override
  public UpdateResult applyTripUpdates(
    @Nullable GtfsRealtimeFuzzyTripMatcher fuzzyTripMatcher,
    ForwardsDelayPropagationType forwardsDelayPropagationType,
    BackwardsDelayPropagationType backwardsDelayPropagationType,
    UpdateIncrementality updateIncrementality,
    List<GtfsRealtime.TripUpdate> updates,
    String feedId
  ) {
    if (updates == null) {
      LOG.warn("updates is null");
      return UpdateResult.empty();
    }

    // Handle FULL_DATASET buffer clear once before the loop
    if (updateIncrementality == FULL_DATASET) {
      recordingBuffer.clear(feedId);
    }

    var comparator = new RealTimeTripUpdateComparator(outputDirectory);
    List<UpdateSuccess> successes = new ArrayList<>();
    List<UpdateError> errors = new ArrayList<>();

    for (GtfsRealtime.TripUpdate update : updates) {
      processOneTrip(
        update,
        fuzzyTripMatcher,
        forwardsDelayPropagationType,
        backwardsDelayPropagationType,
        feedId,
        comparator,
        successes,
        errors
      );
    }

    comparator.logSummary();

    LOG.debug("Shadow: message contains {} trip updates", successes.size() + errors.size());
    return UpdateResult.of(successes, errors);
  }

  private void processOneTrip(
    GtfsRealtime.TripUpdate update,
    @Nullable GtfsRealtimeFuzzyTripMatcher fuzzyTripMatcher,
    ForwardsDelayPropagationType forwardsDelayPropagationType,
    BackwardsDelayPropagationType backwardsDelayPropagationType,
    String feedId,
    RealTimeTripUpdateComparator comparator,
    List<UpdateSuccess> successes,
    List<UpdateError> errors
  ) {
    var tripId = update.getTrip().getTripId();

    // 1. SHADOW FIRST: parse + apply but do NOT write to buffer
    RealTimeTripUpdate shadowRecord = null;
    String shadowFailureReason = null;
    try {
      shadowRecord = shadowHandler.parseAndDispatch(update).realTimeTripUpdate();
    } catch (UpdateException e) {
      shadowFailureReason = "failed: " + e.errorType();
      LOG.warn("Shadow failed for trip {}: {}", tripId, e.errorType());
    } catch (Exception e) {
      shadowFailureReason = "exception: " + e.getMessage();
      LOG.warn("Shadow adapter error for trip {}", tripId, e);
    }

    // 2. PRIMARY SECOND: call through the primary handler per-trip. The recording buffer
    // captures the RealTimeTripUpdate the primary produces.
    recordingBuffer.clearLastUpdate();
    var primaryResult = primaryHandler.applyTripUpdates(
      fuzzyTripMatcher,
      forwardsDelayPropagationType,
      backwardsDelayPropagationType,
      DIFFERENTIAL,
      List.of(update),
      feedId
    );
    var primaryRecord = recordingBuffer.lastUpdate();

    // 3. COMPARE
    String primaryFailureReason = null;
    if (primaryRecord == null && primaryResult.failed() > 0 && !primaryResult.errors().isEmpty()) {
      primaryFailureReason = primaryResult.errors().getFirst().toString();
    }
    comparator.compare(
      primaryRecord,
      shadowRecord,
      tripId,
      update::toString,
      primaryFailureReason,
      shadowFailureReason
    );

    // Return the primary result (single trip -> single result)
    if (primaryResult.failed() > 0 && !primaryResult.errors().isEmpty()) {
      errors.add(primaryResult.errors().getFirst());
    } else if (!primaryResult.successes().isEmpty()) {
      successes.add(primaryResult.successes().getFirst());
    } else {
      successes.add(UpdateSuccess.noWarnings());
    }
  }
}
