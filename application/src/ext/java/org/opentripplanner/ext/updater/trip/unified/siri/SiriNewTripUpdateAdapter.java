package org.opentripplanner.ext.updater.trip.unified.siri;

import javax.annotation.Nullable;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.ext.updater.trip.unified.TripUpdateDispatcher;
import org.opentripplanner.ext.updater.trip.unified.resolver.FuzzyTripMatcher;
import org.opentripplanner.ext.updater.trip.unified.resolver.NoOpFuzzyTripMatcher;
import org.opentripplanner.ext.updater.trip.unified.resolver.StopResolver;
import org.opentripplanner.transit.repository.TimetableRepository;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TransitRepository;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;
import org.opentripplanner.updater.trip.patterncache.TripPatternIdGenerator;
import org.opentripplanner.updater.trip.siri.SiriTripUpdateAdapter;

/**
 * New implementation of the SIRI-ET trip update adapter using the common trip update
 * infrastructure. It produces per-task handlers that use {@link SiriTripUpdateParser} to parse
 * SIRI messages into {@link org.opentripplanner.ext.updater.trip.unified.model.command.TripUpdateCommand} and
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

  private final TransitRepository transitRepository;
  private final DeduplicatorService deduplicator;
  private final SiriTripUpdateParser parser;
  private final String feedId;

  @Nullable
  private final SiriTripMatcherCache fuzzyTripMatcherCache;

  public SiriNewTripUpdateAdapter(
    TransitRepository transitRepository,
    DeduplicatorService deduplicator,
    boolean fuzzyTripMatching,
    String feedId
  ) {
    this.transitRepository = transitRepository;
    this.deduplicator = deduplicator;
    this.feedId = feedId;
    this.tripPatternCache = new TripPatternCache(tripPatternIdGenerator);
    this.parser = new SiriTripUpdateParser(feedId, transitRepository.getTimeZone());
    this.fuzzyTripMatcherCache = fuzzyTripMatching
      ? new SiriTripMatcherCache(transitRepository)
      : null;
  }

  @Override
  public SiriNewTripUpdateHandler forUpdate(TimetableRepository buffer) {
    var transitService = new DefaultTransitService(transitRepository, buffer);
    var timeZone = transitRepository.getTimeZone();

    FuzzyTripMatcher fuzzyMatcher = fuzzyTripMatcherCache != null
      ? new SiriTripMatcher(
          fuzzyTripMatcherCache,
          transitService,
          new StopResolver(transitService),
          timeZone
        )
      : NoOpFuzzyTripMatcher.INSTANCE;

    var dispatcher = TripUpdateDispatcher.create(
      timeZone,
      transitService,
      deduplicator,
      tripPatternCache,
      fuzzyMatcher,
      new SiriRouteCreationStrategy()
    );

    return new SiriNewTripUpdateHandler(parser, dispatcher, buffer);
  }
}
