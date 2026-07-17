package org.opentripplanner.updater.trip.gtfs;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.repository.MutableTimetableSnapshot;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.updater.trip.FuzzyTripMatcher;
import org.opentripplanner.updater.trip.GtfsRtRouteCreationStrategy;
import org.opentripplanner.updater.trip.GtfsTripMatcher;
import org.opentripplanner.updater.trip.NoOpFuzzyTripMatcher;
import org.opentripplanner.updater.trip.TripUpdateDispatcher;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;
import org.opentripplanner.updater.trip.patterncache.TripPatternIdGenerator;

/**
 * New implementation of the GTFS-RT trip update adapter using the common trip update
 * infrastructure. It produces per-task handlers that use {@link GtfsRtTripUpdateParser} to parse
 * GTFS-RT messages into {@link org.opentripplanner.updater.trip.model.ParsedTripUpdate} and
 * {@link TripUpdateDispatcher} to apply them.
 * <p>
 * This is a drop-in replacement for {@link GtfsRealTimeTripUpdateAdapter} when the new
 * implementation is enabled via the {@code useNewUpdaterImplementation} configuration option.
 */
public class GtfsNewTripUpdateAdapter implements GtfsTripUpdateAdapter {

  /**
   * Use an id generator to generate TripPattern ids for new TripPatterns created by RealTime
   * updates.
   */
  private final TripPatternIdGenerator tripPatternIdGenerator = new TripPatternIdGenerator();

  /**
   * A synchronized cache of trip patterns that are added to the graph due to real-time
   * messages.
   */
  private final TripPatternCache tripPatternCache;

  /**
   * A cache of routes created by real-time updates that persists across buffer clears.
   * This is needed because FULL_DATASET clears the buffer, but we want to reuse routes
   * when the same trip update is applied again.
   */
  private final Map<FeedScopedId, Route> realtimeRouteCache = new HashMap<>();

  private final TimetableRepository timetableRepository;
  private final DeduplicatorService deduplicator;
  private final GtfsRtTripUpdateParser parser;
  private final boolean fuzzyMatchingEnabled;
  private final String feedId;

  public GtfsNewTripUpdateAdapter(
    TimetableRepository timetableRepository,
    DeduplicatorService deduplicator,
    ForwardsDelayPropagationType forwardsDelayPropagationType,
    BackwardsDelayPropagationType backwardsDelayPropagationType,
    boolean fuzzyMatchingEnabled,
    String feedId
  ) {
    this.timetableRepository = timetableRepository;
    this.deduplicator = deduplicator;
    this.fuzzyMatchingEnabled = fuzzyMatchingEnabled;
    this.feedId = feedId;
    this.tripPatternCache = new TripPatternCache(tripPatternIdGenerator);
    var timeZone = timetableRepository.getTimeZone();
    this.parser = new GtfsRtTripUpdateParser(
      forwardsDelayPropagationType,
      backwardsDelayPropagationType,
      feedId,
      timeZone,
      () -> LocalDate.now(timeZone)
    );
  }

  @Override
  public GtfsNewTripUpdateHandler forUpdate(MutableTimetableSnapshot buffer) {
    var transitService = new DefaultTransitService(timetableRepository, buffer);

    FuzzyTripMatcher fuzzyMatcher = fuzzyMatchingEnabled
      ? new GtfsTripMatcher(transitService)
      : NoOpFuzzyTripMatcher.INSTANCE;

    var dispatcher = TripUpdateDispatcher.create(
      feedId,
      timetableRepository.getTimeZone(),
      transitService,
      deduplicator,
      tripPatternCache,
      fuzzyMatcher,
      new GtfsRtRouteCreationStrategy(feedId, realtimeRouteCache::get)
    );

    return new GtfsNewTripUpdateHandler(parser, dispatcher, buffer, realtimeRouteCache);
  }
}
