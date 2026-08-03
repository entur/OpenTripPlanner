package org.opentripplanner.ext.updater.trip.unified.siri;

import static org.opentripplanner.updater.spi.UpdateErrorType.UNKNOWN;
import static org.opentripplanner.updater.trip.UpdateIncrementality.FULL_DATASET;

import java.util.ArrayList;
import java.util.List;
import org.opentripplanner.ext.updater.trip.unified.TripUpdateDispatcher;
import org.opentripplanner.ext.updater.trip.unified.TripUpdateParser;
import org.opentripplanner.ext.updater.trip.unified.model.change.TripUpdateResult;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.repository.MutableTimetableSnapshot;
import org.opentripplanner.updater.spi.DataValidationExceptionMapper;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.spi.UpdateSuccess;
import org.opentripplanner.updater.trip.TripUpdateApplier;
import org.opentripplanner.updater.trip.UpdateIncrementality;
import org.opentripplanner.updater.trip.siri.EntityResolver;
import org.opentripplanner.updater.trip.siri.SiriTripUpdateHandler;
import org.opentripplanner.updater.trip.siri.support.TripReferenceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;

/**
 * Update-scoped task produced by {@link SiriNewTripUpdateAdapter#forUpdate}. Parses each
 * EstimatedVehicleJourney into a command, executes it through the {@link TripUpdateDispatcher}
 * and writes the result to the mutable timetable snapshot of the current update task.
 * <p>
 * The entity resolver passed to {@link #applyEstimatedTimetable} is ignored: the unified path
 * resolves entities with its own resolvers, wired into the {@link TripUpdateDispatcher}.
 */
class SiriNewTripUpdateHandler implements SiriTripUpdateHandler {

  private static final Logger LOG = LoggerFactory.getLogger(SiriNewTripUpdateHandler.class);

  private final TripUpdateParser<EstimatedVehicleJourney> parser;
  private final TripUpdateDispatcher dispatcher;
  private final MutableTimetableSnapshot buffer;

  SiriNewTripUpdateHandler(
    TripUpdateParser<EstimatedVehicleJourney> parser,
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
            errors.add(toError(e, journey));
          } catch (DataValidationException e) {
            errors.add(toError(DataValidationExceptionMapper.map(e), journey));
          } catch (Exception e) {
            // A journey the unified path cannot even reject cleanly is a defect in this adapter,
            // but it has to cost that journey alone. Letting it escape discards every journey
            // after it, and for a full dataset the buffer has already been cleared - so the feed
            // would lose all of its real-time data until the next message arrives.
            LOG.warn("EstimatedJourney {} failed.", TripReferenceHelper.tripReference(journey), e);
            errors.add(toError(UpdateException.noTripId(UNKNOWN), journey));
          }
        }
      }
    }

    LOG.debug("message contains {} trip updates", updates.size());

    return UpdateResult.of(successes, errors);
  }

  /**
   * Report which journey failed and why. The trip reference is a best-effort identifier for the
   * journey, used when the update could not be resolved to a trip in the transit model.
   */
  private static UpdateError toError(UpdateException e, EstimatedVehicleJourney journey) {
    return e
      .withTripReference(TripReferenceHelper.tripReference(journey))
      .toError(journey.getDataSource());
  }

  private UpdateSuccess apply(EstimatedVehicleJourney journey) {
    var tripUpdateResult = parseAndExecute(journey);

    // Commit the update to the snapshot and add any warnings
    return TripUpdateApplier.apply(buffer, tripUpdateResult.realTimeTripUpdate()).addWarnings(
      tripUpdateResult.warnings()
    );
  }

  /**
   * Parse the SIRI message and execute the resulting command, without writing the
   * result to the snapshot buffer. Used by the shadow-comparison mode to dry-run the unified path.
   */
  TripUpdateResult parseAndExecute(EstimatedVehicleJourney journey) {
    return dispatcher.execute(parser.parse(journey));
  }
}
