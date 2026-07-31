package org.opentripplanner.ext.updater.trip.unified.gtfs;

import static org.opentripplanner.updater.trip.UpdateIncrementality.FULL_DATASET;

import com.google.transit.realtime.GtfsRealtime;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.ext.updater.trip.unified.regression.AdapterOutcome;
import org.opentripplanner.ext.updater.trip.unified.regression.RealTimeTripUpdateComparator;
import org.opentripplanner.ext.updater.trip.unified.regression.RecordingTimetableRepository;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.spi.UpdateSuccess;
import org.opentripplanner.updater.trip.UpdateIncrementality;
import org.opentripplanner.updater.trip.gtfs.GtfsRealtimeFuzzyTripMatcher;
import org.opentripplanner.updater.trip.gtfs.GtfsTripUpdateHandler;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;
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
  private final RecordingTimetableRepository recordingBuffer;

  @Nullable
  private final Path outputDirectory;

  ShadowGtfsTripUpdateHandler(
    GtfsTripUpdateHandler primaryHandler,
    GtfsNewTripUpdateHandler shadowHandler,
    RecordingTimetableRepository recordingBuffer,
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

    // Clear the buffer once, before the first trip, so that the primary and the shadow path both
    // start from the same state. The primary asks to clear again on every per-trip invocation
    // below; the recording buffer ignores those repeats for the rest of this batch.
    recordingBuffer.startBatch();
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
        updateIncrementality,
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
    UpdateIncrementality updateIncrementality,
    String feedId,
    RealTimeTripUpdateComparator comparator,
    List<UpdateSuccess> successes,
    List<UpdateError> errors
  ) {
    var tripId = update.getTrip().getTripId();

    // 1. SHADOW FIRST: parse + apply but do NOT write to buffer
    AdapterOutcome shadowOutcome;
    try {
      shadowOutcome = new AdapterOutcome.Published(
        shadowHandler.parseAndExecute(update).realTimeTripUpdate()
      );
    } catch (UpdateException e) {
      shadowOutcome = new AdapterOutcome.Rejected(e.errorType());
      LOG.warn("Shadow failed for trip {}: {}", tripId, e.errorType());
    } catch (Exception e) {
      shadowOutcome = new AdapterOutcome.Crashed(e.toString());
      LOG.warn("Shadow adapter error for trip {}", tripId, e);
    }

    // 2. PRIMARY SECOND: call through the primary handler per-trip, with the incrementality the
    // caller gave us — it decides how the primary treats CANCELED and DUPLICATED trips, so
    // substituting a different value here would compare against a behaviour production never
    // runs. The recording buffer captures the RealTimeTripUpdate the primary produces.
    recordingBuffer.clearLastUpdate();
    var primaryResult = primaryHandler.applyTripUpdates(
      fuzzyTripMatcher,
      forwardsDelayPropagationType,
      backwardsDelayPropagationType,
      updateIncrementality,
      List.of(update),
      feedId
    );
    var primaryOutcome = AdapterOutcome.ofPrimary(primaryResult, recordingBuffer.lastUpdate());

    // 3. COMPARE
    comparator.compare(primaryOutcome, shadowOutcome, tripId, update::toString);

    // Forward the primary result (single trip -> single result)
    if (!primaryResult.errors().isEmpty()) {
      errors.add(primaryResult.errors().getFirst());
    } else if (!primaryResult.successes().isEmpty()) {
      successes.add(primaryResult.successes().getFirst());
    } else {
      // The primary reported neither a success nor an error. Counting that as a success would
      // inflate the success rate for a trip nothing was written for.
      errors.add(new UpdateError(null, UpdateErrorType.UNKNOWN, null, null, tripId));
    }
  }
}
