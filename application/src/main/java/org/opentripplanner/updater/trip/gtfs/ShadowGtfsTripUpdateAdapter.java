package org.opentripplanner.updater.trip.gtfs;

import static org.opentripplanner.updater.trip.UpdateIncrementality.DIFFERENTIAL;
import static org.opentripplanner.updater.trip.UpdateIncrementality.FULL_DATASET;

import com.google.transit.realtime.GtfsRealtime;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.spi.UpdateSuccess;
import org.opentripplanner.updater.trip.FuzzyTripMatcher;
import org.opentripplanner.updater.trip.GtfsTripMatcher;
import org.opentripplanner.updater.trip.NoOpFuzzyTripMatcher;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;
import org.opentripplanner.updater.trip.TripUpdateApplierFactory;
import org.opentripplanner.updater.trip.UpdateIncrementality;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;
import org.opentripplanner.updater.trip.handlers.GtfsRtRouteCreationStrategy;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;
import org.opentripplanner.updater.trip.patterncache.TripPatternIdGenerator;
import org.opentripplanner.updater.trip.regression.RealTimeTripUpdateComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shadow adapter that runs both the primary (legacy) and the new (unified) GTFS-RT adapters on
 * every trip, comparing the {@link RealTimeTripUpdate} records they produce. Only the primary
 * adapter writes to the snapshot buffer; the shadow adapter is read-only.
 * <p>
 * Per-trip interleaving guarantees that both adapters see identical buffer state:
 * <ol>
 *   <li>Shadow runs first (reads buffer, produces record, does NOT write)</li>
 *   <li>Primary runs second (reads same buffer, produces record, writes to buffer)</li>
 *   <li>Compare the two records</li>
 * </ol>
 */
public class ShadowGtfsTripUpdateAdapter implements GtfsTripUpdateAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(ShadowGtfsTripUpdateAdapter.class);

  private final GtfsRealTimeTripUpdateAdapter primaryAdapter;
  private final TimetableSnapshotManager snapshotManager;
  private final GtfsRtTripUpdateParser parser;
  private final DeduplicatorService deduplicator;
  private final TransitEditorService transitEditorService;
  private final TripPatternCache tripPatternCache;
  private final boolean fuzzyMatchingEnabled;
  private final String feedId;
  private final Map<FeedScopedId, Route> realtimeRouteCache = new HashMap<>();

  @Nullable
  private final Path outputDirectory;

  public ShadowGtfsTripUpdateAdapter(
    GtfsRealTimeTripUpdateAdapter primaryAdapter,
    TimetableRepository timetableRepository,
    DeduplicatorService deduplicator,
    TimetableSnapshotManager snapshotManager,
    ForwardsDelayPropagationType forwardsDelayPropagationType,
    BackwardsDelayPropagationType backwardsDelayPropagationType,
    boolean fuzzyMatchingEnabled,
    String feedId
  ) {
    this(
      primaryAdapter,
      timetableRepository,
      deduplicator,
      snapshotManager,
      forwardsDelayPropagationType,
      backwardsDelayPropagationType,
      fuzzyMatchingEnabled,
      feedId,
      null
    );
  }

  public ShadowGtfsTripUpdateAdapter(
    GtfsRealTimeTripUpdateAdapter primaryAdapter,
    TimetableRepository timetableRepository,
    DeduplicatorService deduplicator,
    TimetableSnapshotManager snapshotManager,
    ForwardsDelayPropagationType forwardsDelayPropagationType,
    BackwardsDelayPropagationType backwardsDelayPropagationType,
    boolean fuzzyMatchingEnabled,
    String feedId,
    @Nullable Path outputDirectory
  ) {
    this.primaryAdapter = primaryAdapter;
    this.snapshotManager = snapshotManager;
    this.deduplicator = deduplicator;
    this.fuzzyMatchingEnabled = fuzzyMatchingEnabled;
    this.feedId = feedId;
    this.outputDirectory = outputDirectory;

    this.transitEditorService = new DefaultTransitService(
      timetableRepository,
      snapshotManager.getTimetableSnapshotBuffer()
    );

    this.tripPatternCache = new TripPatternCache(
      new TripPatternIdGenerator(),
      transitEditorService::findPattern
    );

    this.parser = new GtfsRtTripUpdateParser(
      forwardsDelayPropagationType,
      backwardsDelayPropagationType,
      feedId,
      transitEditorService.getTimeZone(),
      () -> LocalDate.now(transitEditorService.getTimeZone())
    );
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
      snapshotManager.clearBuffer(feedId);
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
      FuzzyTripMatcher fuzzyMatcher = fuzzyMatchingEnabled
        ? new GtfsTripMatcher(transitEditorService)
        : NoOpFuzzyTripMatcher.INSTANCE;

      var applier = TripUpdateApplierFactory.create(
        this.feedId,
        transitEditorService.getTimeZone(),
        transitEditorService,
        deduplicator,
        snapshotManager,
        tripPatternCache,
        fuzzyMatcher,
        new GtfsRtRouteCreationStrategy(this.feedId, realtimeRouteCache::get)
      );

      var parsedUpdate = parser.parse(update);
      var applyResult = applier.apply(parsedUpdate);
      shadowRecord = applyResult.realTimeTripUpdate();
    } catch (UpdateException e) {
      shadowFailureReason = "failed: " + e.errorType();
      LOG.warn("Shadow failed for trip {}: {}", tripId, e.errorType());
    } catch (Exception e) {
      shadowFailureReason = "exception: " + e.getMessage();
      LOG.warn("Shadow adapter error for trip {}", tripId, e);
    }

    // 2. PRIMARY SECOND: call through the primary adapter per-trip
    // Install listener to capture the RealTimeTripUpdate the primary produces
    RealTimeTripUpdate[] primaryRecord = { null };
    snapshotManager.setUpdateBufferListener(update2 -> primaryRecord[0] = update2);
    try {
      var primaryResult = primaryAdapter.applyTripUpdates(
        fuzzyTripMatcher,
        forwardsDelayPropagationType,
        backwardsDelayPropagationType,
        DIFFERENTIAL,
        List.of(update),
        feedId
      );

      // 3. COMPARE
      String primaryFailureReason = null;
      if (
        primaryRecord[0] == null && primaryResult.failed() > 0 && !primaryResult.errors().isEmpty()
      ) {
        primaryFailureReason = primaryResult.errors().getFirst().toString();
      }
      comparator.compare(
        primaryRecord[0],
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
    } finally {
      snapshotManager.setUpdateBufferListener(null);
    }
  }
}
