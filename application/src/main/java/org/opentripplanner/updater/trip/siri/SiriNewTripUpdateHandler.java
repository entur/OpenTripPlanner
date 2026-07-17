package org.opentripplanner.updater.trip.siri;

import static org.opentripplanner.updater.trip.UpdateIncrementality.FULL_DATASET;

import java.util.ArrayList;
import java.util.List;
import org.opentripplanner.transit.repository.MutableTimetableSnapshot;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.spi.UpdateSuccess;
import org.opentripplanner.updater.trip.TripUpdateApplier;
import org.opentripplanner.updater.trip.TripUpdateDispatcher;
import org.opentripplanner.updater.trip.TripUpdateResult;
import org.opentripplanner.updater.trip.UpdateIncrementality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;

/**
 * Update-scoped task produced by {@link SiriNewTripUpdateAdapter#forUpdate}. Parses each
 * EstimatedVehicleJourney, dispatches it to the matching domain operation and writes the result
 * to the mutable timetable snapshot of the current update task.
 * <p>
 * The entity resolver passed to {@link #applyEstimatedTimetable} is ignored: the unified path
 * resolves entities with its own resolvers, wired into the {@link TripUpdateDispatcher}.
 */
class SiriNewTripUpdateHandler implements SiriTripUpdateHandler {

  private static final Logger LOG = LoggerFactory.getLogger(SiriNewTripUpdateHandler.class);

  private final SiriTripUpdateParser parser;
  private final TripUpdateDispatcher dispatcher;
  private final MutableTimetableSnapshot buffer;

  SiriNewTripUpdateHandler(
    SiriTripUpdateParser parser,
    TripUpdateDispatcher dispatcher,
    MutableTimetableSnapshot buffer
  ) {
    this.parser = parser;
    this.dispatcher = dispatcher;
    this.buffer = buffer;
  }

  @Override
  public UpdateResult applyEstimatedTimetable(
    EntityResolver entityResolver,
    String feedId,
    UpdateIncrementality incrementality,
    List<EstimatedTimetableDeliveryStructure> updates
  ) {
    if (updates == null) {
      LOG.warn("updates is null");
      return UpdateResult.empty();
    }

    List<UpdateSuccess> successes = new ArrayList<>();
    List<UpdateError> errors = new ArrayList<>();

    if (incrementality == FULL_DATASET) {
      // Remove all updates from the buffer
      buffer.clear(feedId);
    }

    for (var etDelivery : updates) {
      for (var estimatedJourneyVersion : etDelivery.getEstimatedJourneyVersionFrames()) {
        var journeys = estimatedJourneyVersion.getEstimatedVehicleJourneies();
        LOG.debug("Handling {} EstimatedVehicleJourneys.", journeys.size());
        for (EstimatedVehicleJourney journey : journeys) {
          try {
            successes.add(apply(journey));
          } catch (UpdateException e) {
            errors.add(e.toError(journey.getDataSource()));
          }
        }
      }
    }

    LOG.debug("message contains {} trip updates", updates.size());

    return UpdateResult.of(successes, errors);
  }

  private UpdateSuccess apply(EstimatedVehicleJourney journey) {
    var tripUpdateResult = parseAndDispatch(journey);

    // Commit the update to the snapshot and add any warnings
    return TripUpdateApplier.apply(buffer, tripUpdateResult.realTimeTripUpdate()).addWarnings(
      tripUpdateResult.warnings()
    );
  }

  /**
   * Parse the SIRI message and dispatch it to the matching domain operation, without writing the
   * result to the snapshot buffer. Used by the shadow-comparison mode to dry-run the unified path.
   */
  TripUpdateResult parseAndDispatch(EstimatedVehicleJourney journey) {
    return dispatcher.apply(parser.parse(journey));
  }
}
