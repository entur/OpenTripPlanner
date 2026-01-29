package org.opentripplanner.updater.trip.siri;

import static org.opentripplanner.updater.trip.UpdateIncrementality.FULL_DATASET;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;

/**
 * New implementation of the SIRI-ET trip update adapter using the common trip update infrastructure.
 * This uses {@link SiriTripUpdateParser} to parse SIRI messages into {@link org.opentripplanner.updater.trip.model.ParsedTripUpdate}
 * and {@link DefaultTripUpdateApplier} to apply them.
 * <p>
 * This is a drop-in replacement for {@link SiriRealTimeTripUpdateAdapter} when the new implementation
 * is enabled via the {@code useNewUpdaterImplementation} configuration option.
 */
public class SiriNewTripUpdateAdapter implements SiriTripUpdateAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(SiriNewTripUpdateAdapter.class);

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

  private final SiriTripUpdateParser parser;
  private final DefaultTripUpdateApplier applier;
  private final TransitEditorService transitEditorService;
  private final TimetableSnapshotManager snapshotManager;

  public SiriNewTripUpdateAdapter(
    TimetableRepository timetableRepository,
    TimetableSnapshotManager snapshotManager
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
    this.parser = new SiriTripUpdateParser();
    this.applier = new DefaultTripUpdateApplier(transitEditorService);
  }

  /**
   * Apply estimated timetables to the timetable snapshot.
   *
   * @param fuzzyTripMatcher Optional fuzzy trip matcher for matching trips
   * @param entityResolver Entity resolver for the feed
   * @param feedId The feed ID
   * @param incrementality The incrementality of the update
   * @param updates The SIRI EstimatedTimetable deliveries
   * @return Result of applying the updates
   */
  @Override
  public UpdateResult applyEstimatedTimetable(
    @Nullable SiriFuzzyTripMatcher fuzzyTripMatcher,
    EntityResolver entityResolver,
    String feedId,
    UpdateIncrementality incrementality,
    List<EstimatedTimetableDeliveryStructure> updates
  ) {
    if (updates == null) {
      LOG.warn("updates is null");
      return UpdateResult.empty();
    }

    List<Result<UpdateSuccess, UpdateError>> results = new ArrayList<>();

    if (incrementality == FULL_DATASET) {
      // Remove all updates from the buffer
      snapshotManager.clearBuffer(feedId);
    }

    // Create parser context
    var parserContext = new TripUpdateParserContext(
      feedId,
      transitEditorService.getTimeZone(),
      LocalDate::now
    );

    // Create applier context with the trip ID resolver and stop resolver
    var tripResolver = new TripResolver(transitEditorService);
    var serviceDateResolver = new ServiceDateResolver(tripResolver);
    var stopResolver = new StopResolver(transitEditorService);
    var applierContext = new TripUpdateApplierContext(
      feedId,
      snapshotManager,
      tripResolver,
      serviceDateResolver,
      stopResolver,
      tripPatternCache
    );

    for (var etDelivery : updates) {
      for (var estimatedJourneyVersion : etDelivery.getEstimatedJourneyVersionFrames()) {
        var journeys = estimatedJourneyVersion.getEstimatedVehicleJourneies();
        LOG.debug("Handling {} EstimatedVehicleJourneys.", journeys.size());
        for (EstimatedVehicleJourney journey : journeys) {
          results.add(apply(journey, parserContext, applierContext));
        }
      }
    }

    LOG.debug("message contains {} trip updates", updates.size());

    return UpdateResult.ofResults(results);
  }

  private Result<UpdateSuccess, UpdateError> apply(
    EstimatedVehicleJourney journey,
    TripUpdateParserContext parserContext,
    TripUpdateApplierContext applierContext
  ) {
    // Parse the SIRI message
    var parseResult = parser.parse(journey, parserContext);
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
