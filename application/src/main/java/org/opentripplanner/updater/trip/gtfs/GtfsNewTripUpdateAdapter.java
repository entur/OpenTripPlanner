package org.opentripplanner.updater.trip.gtfs;

import static org.opentripplanner.updater.trip.UpdateIncrementality.FULL_DATASET;

import com.google.transit.realtime.GtfsRealtime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.spi.UpdateSuccess;
import org.opentripplanner.updater.trip.DefaultTripUpdateApplier;
import org.opentripplanner.updater.trip.ServiceDateResolver;
import org.opentripplanner.updater.trip.StopResolver;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;
import org.opentripplanner.updater.trip.TripResolver;
import org.opentripplanner.updater.trip.TripUpdateApplierContext;
import org.opentripplanner.updater.trip.TripUpdateParserContext;
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

  private final GtfsRtTripUpdateParser parser;
  private final DefaultTripUpdateApplier applier;
  private final TransitEditorService transitEditorService;
  private final TimetableSnapshotManager snapshotManager;

  public GtfsNewTripUpdateAdapter(
    TimetableRepository timetableRepository,
    TimetableSnapshotManager snapshotManager,
    ForwardsDelayPropagationType forwardsDelayPropagationType,
    BackwardsDelayPropagationType backwardsDelayPropagationType
  ) {
    this.snapshotManager = snapshotManager;
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
      backwardsDelayPropagationType
    );
    this.applier = new DefaultTripUpdateApplier(transitEditorService);
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

    // Create parser context
    var parserContext = new TripUpdateParserContext(
      feedId,
      transitEditorService.getTimeZone(),
      () -> LocalDate.now(transitEditorService.getTimeZone())
    );

    // Create applier context with the trip ID resolver and stop resolver
    var tripResolver = new TripResolver(transitEditorService);
    var serviceDateResolver = new ServiceDateResolver(tripResolver, transitEditorService);
    var stopResolver = new StopResolver(transitEditorService);
    var applierContext = new TripUpdateApplierContext(
      feedId,
      transitEditorService.getTimeZone(),
      snapshotManager,
      tripResolver,
      serviceDateResolver,
      stopResolver,
      tripPatternCache
    );

    for (GtfsRealtime.TripUpdate update : updates) {
      results.add(apply(update, parserContext, applierContext));
    }

    LOG.debug("message contains {} trip updates", updates.size());

    return UpdateResult.ofResults(results);
  }

  private Result<UpdateSuccess, UpdateError> apply(
    GtfsRealtime.TripUpdate update,
    TripUpdateParserContext parserContext,
    TripUpdateApplierContext applierContext
  ) {
    // Parse the GTFS-RT message
    var parseResult = parser.parse(update, parserContext);
    if (parseResult.isFailure()) {
      return parseResult.toFailureResult();
    }

    var parsedUpdate = parseResult.successValue();

    // Apply the parsed update
    var applyResult = applier.apply(parsedUpdate, applierContext);
    if (applyResult.isFailure()) {
      return applyResult.toFailureResult();
    }

    var realTimeTripUpdate = applyResult.successValue();

    // Commit the update to the snapshot
    return snapshotManager.updateBuffer(realTimeTripUpdate);
  }
}
