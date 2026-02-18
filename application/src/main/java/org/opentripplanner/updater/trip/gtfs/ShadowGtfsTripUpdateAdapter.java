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
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.DeduplicatorService;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.spi.UpdateSuccess;
import org.opentripplanner.updater.trip.DefaultTripUpdateApplier;
import org.opentripplanner.updater.trip.FuzzyTripMatcher;
import org.opentripplanner.updater.trip.RouteDirectionTimeMatcher;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;
import org.opentripplanner.updater.trip.UpdateIncrementality;
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
    List<Result<UpdateSuccess, UpdateError>> results = new ArrayList<>();

    for (GtfsRealtime.TripUpdate update : updates) {
      results.add(
        processOneTrip(
          update,
          fuzzyTripMatcher,
          forwardsDelayPropagationType,
          backwardsDelayPropagationType,
          feedId,
          comparator
        )
      );
    }

    comparator.logSummary();

    LOG.debug("Shadow: message contains {} trip updates", results.size());
    return UpdateResult.ofResults(results);
  }

  private Result<UpdateSuccess, UpdateError> processOneTrip(
    GtfsRealtime.TripUpdate update,
    @Nullable GtfsRealtimeFuzzyTripMatcher fuzzyTripMatcher,
    ForwardsDelayPropagationType forwardsDelayPropagationType,
    BackwardsDelayPropagationType backwardsDelayPropagationType,
    String feedId,
    RealTimeTripUpdateComparator comparator
  ) {
    var tripId = update.getTrip().getTripId();

    // 1. SHADOW FIRST: parse + apply but do NOT write to buffer
    RealTimeTripUpdate shadowRecord = null;
    String shadowFailureReason = null;
    try {
      FuzzyTripMatcher fuzzyMatcher = fuzzyMatchingEnabled
        ? new RouteDirectionTimeMatcher(transitEditorService)
        : null;

      var applier = new DefaultTripUpdateApplier(
        this.feedId,
        transitEditorService.getTimeZone(),
        transitEditorService,
        deduplicator,
        snapshotManager,
        tripPatternCache,
        fuzzyMatcher,
        new GtfsRtRouteCreationStrategy(this.feedId, realtimeRouteCache::get)
      );

      var parseResult = parser.parse(update);
      if (parseResult.isSuccess()) {
        var applyResult = applier.apply(parseResult.successValue());
        if (applyResult.isSuccess()) {
          shadowRecord = applyResult.successValue().realTimeTripUpdate();
        } else {
          shadowFailureReason = "apply failed: " + applyResult.failureValue();
          LOG.warn("Shadow apply failed for trip {}: {}", tripId, applyResult.failureValue());
        }
      } else {
        shadowFailureReason = "parse failed: " + parseResult.failureValue();
        LOG.warn("Shadow parse failed for trip {}: {}", tripId, parseResult.failureValue());
      }
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
        return Result.failure(primaryResult.errors().getFirst());
      }
      if (!primaryResult.successes().isEmpty()) {
        return Result.success(primaryResult.successes().getFirst());
      }
      return Result.success(UpdateSuccess.noWarnings());
    } finally {
      snapshotManager.setUpdateBufferListener(null);
    }
  }
}
