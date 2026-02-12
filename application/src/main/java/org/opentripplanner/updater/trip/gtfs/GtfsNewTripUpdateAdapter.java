package org.opentripplanner.updater.trip.gtfs;

import static org.opentripplanner.updater.trip.UpdateIncrementality.FULL_DATASET;

import com.google.transit.realtime.GtfsRealtime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.Route;
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
import org.opentripplanner.updater.trip.siri.SiriTripPatternCache;
import org.opentripplanner.updater.trip.siri.SiriTripPatternIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * New implementation of the GTFS-RT trip update adapter using the common trip update infrastructure.
 * This uses {@link GtfsRtTripUpdateParser} to parse GTFS-RT messages into {@link org.opentripplanner.updater.trip.model.ParsedTripUpdate}
 * and {@link DefaultTripUpdateApplier} to apply them.
 * <p>
 * This is a drop-in replacement for {@link GtfsRealTimeTripUpdateAdapter} when the new implementation
 * is enabled via the {@code useNewUpdaterImplementation} configuration option.
 */
public class GtfsNewTripUpdateAdapter implements GtfsTripUpdateAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(GtfsNewTripUpdateAdapter.class);

  /**
   * Use an id generator to generate TripPattern ids for new TripPatterns created by RealTime
   * updates.
   */
  private final SiriTripPatternIdGenerator tripPatternIdGenerator =
    new SiriTripPatternIdGenerator();

  /**
   * A synchronized cache of trip patterns that are added to the graph due to real-time
   * messages.
   */
  private final SiriTripPatternCache tripPatternCache;

  /**
   * A cache of routes created by real-time updates that persists across buffer clears.
   * This is needed because FULL_DATASET clears the buffer, but we want to reuse routes
   * when the same trip update is applied again.
   */
  private final Map<FeedScopedId, Route> realtimeRouteCache = new HashMap<>();

  private final GtfsRtTripUpdateParser parser;
  private final TransitEditorService transitEditorService;
  private final TimetableSnapshotManager snapshotManager;
  private final boolean fuzzyMatchingEnabled;

  public GtfsNewTripUpdateAdapter(
    TimetableRepository timetableRepository,
    TimetableSnapshotManager snapshotManager,
    ForwardsDelayPropagationType forwardsDelayPropagationType,
    BackwardsDelayPropagationType backwardsDelayPropagationType,
    boolean fuzzyMatchingEnabled,
    String feedId
  ) {
    this.snapshotManager = snapshotManager;
    this.fuzzyMatchingEnabled = fuzzyMatchingEnabled;
    this.transitEditorService = new DefaultTransitService(
      timetableRepository,
      snapshotManager.getTimetableSnapshotBuffer()
    );
    this.tripPatternCache = new SiriTripPatternCache(
      tripPatternIdGenerator,
      transitEditorService::findPattern
    );
    this.parser = new GtfsRtTripUpdateParser(
      forwardsDelayPropagationType,
      backwardsDelayPropagationType,
      fuzzyMatchingEnabled,
      feedId,
      transitEditorService.getTimeZone(),
      () -> LocalDate.now(transitEditorService.getTimeZone())
    );
  }

  /**
   * Apply GTFS-RT trip updates to the timetable snapshot.
   *
   * @param fuzzyTripMatcher Optional fuzzy trip matcher for matching trips
   * @param forwardsDelayPropagationType How to propagate delays forward (passed to parser)
   * @param backwardsDelayPropagationType How to propagate delays backward (passed to parser)
   * @param updateIncrementality Whether this is a full dataset or differential update
   * @param updates The GTFS-RT TripUpdate messages
   * @param feedId The feed ID
   * @return Result of applying the updates
   */
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

    List<Result<UpdateSuccess, UpdateError>> results = new ArrayList<>();

    if (updateIncrementality == FULL_DATASET) {
      // Remove all updates from the buffer
      snapshotManager.clearBuffer(feedId);
    }

    // Create fuzzy matcher if fuzzy matching is enabled
    FuzzyTripMatcher fuzzyMatcher = null;
    if (fuzzyMatchingEnabled) {
      fuzzyMatcher = new RouteDirectionTimeMatcher(transitEditorService);
    }

    var applier = new DefaultTripUpdateApplier(
      feedId,
      transitEditorService.getTimeZone(),
      transitEditorService,
      snapshotManager,
      tripPatternCache,
      fuzzyMatcher,
      realtimeRouteCache::get
    );

    for (GtfsRealtime.TripUpdate update : updates) {
      results.add(apply(update, applier));
    }

    LOG.debug("message contains {} trip updates", updates.size());

    return UpdateResult.ofResults(results);
  }

  private Result<UpdateSuccess, UpdateError> apply(
    GtfsRealtime.TripUpdate update,
    DefaultTripUpdateApplier applier
  ) {
    // Parse the GTFS-RT message
    var parseResult = parser.parse(update);
    if (parseResult.isFailure()) {
      return parseResult.toFailureResult();
    }

    var parsedUpdate = parseResult.successValue();

    // Apply the parsed update
    var applyResult = applier.apply(parsedUpdate);
    if (applyResult.isFailure()) {
      return applyResult.toFailureResult();
    }

    var tripUpdateResult = applyResult.successValue();
    var realTimeTripUpdate = tripUpdateResult.realTimeTripUpdate();

    // Cache the route if it's a new trip with route creation
    if (realTimeTripUpdate.tripCreation() && realTimeTripUpdate.routeCreation()) {
      Route route = realTimeTripUpdate.pattern().getRoute();
      realtimeRouteCache.put(route.getId(), route);
    }

    // Commit the update to the snapshot and add any warnings
    return snapshotManager
      .updateBuffer(realTimeTripUpdate)
      .mapSuccess(s -> s.addWarnings(tripUpdateResult.warnings()));
  }
}
