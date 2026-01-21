package org.opentripplanner.updater.trip.siri;

import static org.opentripplanner.updater.trip.UpdateIncrementality.FULL_DATASET;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.spi.UpdateSuccess;
import org.opentripplanner.updater.trip.DefaultTripUpdateApplier;
import org.opentripplanner.updater.trip.SiriTripMatcher;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;
import org.opentripplanner.updater.trip.TripUpdateApplier;
import org.opentripplanner.updater.trip.TripUpdateApplierContext;
import org.opentripplanner.updater.trip.TripUpdateParserContext;
import org.opentripplanner.updater.trip.UpdateIncrementality;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;

/**
 * Adapts from SIRI-ET EstimatedTimetables to OTP's internal real-time data model.
 * Uses the unified {@link DefaultTripUpdateApplier} with optional {@link SiriTripMatcher}
 * for fuzzy trip matching.
 */
public class SiriRealTimeTripUpdateAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(SiriRealTimeTripUpdateAdapter.class);

  private final String feedId;
  private final TransitEditorService transitEditorService;
  private final TimetableSnapshotManager snapshotManager;
  private final TripUpdateApplier applier;
  private final SiriTripUpdateParser parser;

  public SiriRealTimeTripUpdateAdapter(
    String feedId,
    TimetableRepository timetableRepository,
    TimetableSnapshotManager snapshotManager,
    @Nullable SiriTripMatcher tripMatcher
  ) {
    this.feedId = feedId;
    this.snapshotManager = snapshotManager;
    this.transitEditorService = new DefaultTransitService(
      timetableRepository,
      snapshotManager.getTimetableSnapshotBuffer()
    );
    this.applier = new DefaultTripUpdateApplier(transitEditorService, tripMatcher);
    this.parser = new SiriTripUpdateParser();
  }

  /**
   * Method to apply estimated timetables to the most recent version of the timetable snapshot.
   *
   * @param entityResolver  entity resolver for looking up trips, routes, stops
   * @param incrementality  the incrementality of the update, for example if updates represent all
   *                        updates that are active right now, i.e. all previous updates should be
   *                        disregarded
   * @param updates         SIRI EstimatedTimetable deliveries that should be applied atomically.
   */
  public UpdateResult applyEstimatedTimetable(
    EntityResolver entityResolver,
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

    // Create parser and applier contexts
    TripUpdateParserContext parserContext = new TripUpdateParserContext(
      feedId,
      transitEditorService.getTimeZone(),
      LocalDate::now
    );
    TripUpdateApplierContext applierContext = new TripUpdateApplierContext(feedId, snapshotManager);

    for (var etDelivery : updates) {
      for (var estimatedJourneyVersion : etDelivery.getEstimatedJourneyVersionFrames()) {
        var journeys = estimatedJourneyVersion.getEstimatedVehicleJourneies();
        LOG.debug("Handling {} EstimatedVehicleJourneys.", journeys.size());
        for (EstimatedVehicleJourney journey : journeys) {
          // Parse SIRI message to ParsedTripUpdate
          Result<ParsedTripUpdate, UpdateError> parseResult = parser.parse(journey, parserContext);
          if (parseResult.isFailure()) {
            results.add(parseResult.toFailureResult());
            continue;
          }

          // Apply the parsed update
          Result<RealTimeTripUpdate, UpdateError> applyResult = applier.apply(
            parseResult.successValue(),
            applierContext
          );
          if (applyResult.isFailure()) {
            results.add(applyResult.toFailureResult());
            continue;
          }

          // Commit to snapshot manager
          Result<UpdateSuccess, UpdateError> commitResult = snapshotManager.updateBuffer(
            applyResult.successValue()
          );
          results.add(commitResult);
        }
      }
    }

    LOG.debug("message contains {} trip updates", updates.size());

    return UpdateResult.ofResults(results);
  }
}
