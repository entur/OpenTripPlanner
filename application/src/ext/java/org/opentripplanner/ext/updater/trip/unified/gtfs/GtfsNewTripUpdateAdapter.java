package org.opentripplanner.ext.updater.trip.unified.gtfs;

import java.time.LocalDate;
import java.util.function.Supplier;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.ext.updater.trip.unified.TripUpdateDispatcher;
import org.opentripplanner.ext.updater.trip.unified.resolver.FuzzyTripMatcher;
import org.opentripplanner.ext.updater.trip.unified.resolver.NoOpFuzzyTripMatcher;
import org.opentripplanner.transit.repository.TimetableRepository;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TransitRepository;
import org.opentripplanner.updater.trip.gtfs.GtfsTripUpdateAdapter;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;
import org.opentripplanner.updater.trip.patterncache.TripPatternIdGenerator;

/**
 * New implementation of the GTFS-RT trip update adapter using the common trip update
 * infrastructure. It produces per-task handlers that use {@link GtfsRtTripUpdateParser} to parse
 * GTFS-RT messages into {@link org.opentripplanner.ext.updater.trip.unified.model.command.TripUpdateCommand} and
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

  private final TransitRepository transitRepository;
  private final DeduplicatorService deduplicator;
  private final GtfsRtTripUpdateParser parser;
  private final boolean fuzzyMatchingEnabled;
  private final String feedId;

  public GtfsNewTripUpdateAdapter(
    TransitRepository transitRepository,
    DeduplicatorService deduplicator,
    ForwardsDelayPropagationType forwardsDelayPropagationType,
    BackwardsDelayPropagationType backwardsDelayPropagationType,
    boolean fuzzyMatchingEnabled,
    String feedId,
    Supplier<LocalDate> localDateNow
  ) {
    this.transitRepository = transitRepository;
    this.deduplicator = deduplicator;
    this.fuzzyMatchingEnabled = fuzzyMatchingEnabled;
    this.feedId = feedId;
    this.tripPatternCache = new TripPatternCache(tripPatternIdGenerator);
    this.parser = new GtfsRtTripUpdateParser(
      forwardsDelayPropagationType,
      backwardsDelayPropagationType,
      feedId,
      transitRepository.getTimeZone(),
      localDateNow
    );
  }

  @Override
  public GtfsNewTripUpdateHandler forUpdate(TimetableRepository buffer) {
    var transitService = new DefaultTransitService(transitRepository, buffer);

    FuzzyTripMatcher fuzzyMatcher = fuzzyMatchingEnabled
      ? new GtfsTripMatcher(transitService)
      : NoOpFuzzyTripMatcher.INSTANCE;

    var dispatcher = TripUpdateDispatcher.create(
      transitRepository.getTimeZone(),
      transitService,
      deduplicator,
      tripPatternCache,
      fuzzyMatcher,
      new GtfsRtRouteCreationStrategy(feedId)
    );

    return new GtfsNewTripUpdateHandler(parser, dispatcher, buffer);
  }
}
