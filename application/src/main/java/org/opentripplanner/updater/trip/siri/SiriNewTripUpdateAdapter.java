package org.opentripplanner.updater.trip.siri;

import javax.annotation.Nullable;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.transit.repository.MutableTimetableSnapshot;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.updater.trip.FuzzyTripMatcher;
import org.opentripplanner.updater.trip.NoOpFuzzyTripMatcher;
import org.opentripplanner.updater.trip.SiriRouteCreationStrategy;
import org.opentripplanner.updater.trip.SiriTripMatcher;
import org.opentripplanner.updater.trip.SiriTripMatcherCache;
import org.opentripplanner.updater.trip.StopResolver;
import org.opentripplanner.updater.trip.TripUpdateDispatcher;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;
import org.opentripplanner.updater.trip.patterncache.TripPatternIdGenerator;

/**
 * New implementation of the SIRI-ET trip update adapter using the common trip update
 * infrastructure. It produces per-task handlers that use {@link SiriTripUpdateParser} to parse
 * SIRI messages into {@link org.opentripplanner.updater.trip.model.ParsedTripUpdate} and
 * {@link TripUpdateDispatcher} to apply them.
 * <p>
 * This is a drop-in replacement for {@link SiriRealTimeTripUpdateAdapter} when the new
 * implementation is enabled via the {@code useNewUpdaterImplementation} configuration option.
 */
public class SiriNewTripUpdateAdapter implements SiriTripUpdateAdapter {

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

  private final TimetableRepository timetableRepository;
  private final DeduplicatorService deduplicator;
  private final SiriTripUpdateParser parser;
  private final String feedId;

  @Nullable
  private final SiriTripMatcherCache fuzzyTripMatcherCache;

  public SiriNewTripUpdateAdapter(
    TimetableRepository timetableRepository,
    DeduplicatorService deduplicator,
    boolean fuzzyTripMatching,
    String feedId
  ) {
    this.timetableRepository = timetableRepository;
    this.deduplicator = deduplicator;
    this.feedId = feedId;
    this.tripPatternCache = new TripPatternCache(tripPatternIdGenerator);
    this.parser = new SiriTripUpdateParser(feedId, timetableRepository.getTimeZone());
    this.fuzzyTripMatcherCache = fuzzyTripMatching
      ? new SiriTripMatcherCache(timetableRepository)
      : null;
  }

  @Override
  public SiriNewTripUpdateHandler forUpdate(MutableTimetableSnapshot buffer) {
    var transitService = new DefaultTransitService(timetableRepository, buffer);
    var timeZone = timetableRepository.getTimeZone();

    FuzzyTripMatcher fuzzyMatcher = fuzzyTripMatcherCache != null
      ? new SiriTripMatcher(
          fuzzyTripMatcherCache,
          transitService,
          new StopResolver(transitService),
          timeZone
        )
      : NoOpFuzzyTripMatcher.INSTANCE;

    var dispatcher = TripUpdateDispatcher.create(
      feedId,
      timeZone,
      transitService,
      deduplicator,
      tripPatternCache,
      fuzzyMatcher,
      new SiriRouteCreationStrategy(feedId)
    );

    return new SiriNewTripUpdateHandler(parser, dispatcher, buffer);
  }
}
