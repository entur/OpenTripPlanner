package org.opentripplanner.ext.updater.trip.unified.gtfs;

import static org.opentripplanner.updater.spi.UpdateErrorType.NOT_IMPLEMENTED_DIFFERENTIAL_DUPLICATED;
import static org.opentripplanner.updater.spi.UpdateErrorType.UNKNOWN;
import static org.opentripplanner.updater.trip.UpdateIncrementality.DIFFERENTIAL;
import static org.opentripplanner.updater.trip.UpdateIncrementality.FULL_DATASET;

import com.google.transit.realtime.GtfsRealtime;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.ext.updater.trip.unified.TripUpdateDispatcher;
import org.opentripplanner.ext.updater.trip.unified.TripUpdateParser;
import org.opentripplanner.ext.updater.trip.unified.model.change.TripUpdateResult;
import org.opentripplanner.ext.updater.trip.unified.model.command.DuplicateTrip;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.repository.TimetableRepository;
import org.opentripplanner.updater.spi.DataValidationExceptionMapper;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.spi.UpdateSuccess;
import org.opentripplanner.updater.trip.TripUpdateApplier;
import org.opentripplanner.updater.trip.UpdateIncrementality;
import org.opentripplanner.updater.trip.gtfs.GtfsRealtimeFuzzyTripMatcher;
import org.opentripplanner.updater.trip.gtfs.GtfsTripUpdateHandler;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Update-scoped task produced by {@link GtfsNewTripUpdateAdapter#forUpdate}. Parses each
 * TripUpdate message into a command, executes it through the {@link TripUpdateDispatcher} and
 * writes the result to the mutable timetable snapshot of the current update task.
 * <p>
 * The fuzzy trip matcher and delay propagation types passed to {@link #applyTripUpdates} are
 * ignored: the unified path configures fuzzy matching and delay interpolation once, in the
 * application-scoped {@link GtfsNewTripUpdateAdapter}.
 */
class GtfsNewTripUpdateHandler implements GtfsTripUpdateHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GtfsNewTripUpdateHandler.class);

  private final TripUpdateParser<GtfsRealtime.TripUpdate> parser;
  private final TripUpdateDispatcher dispatcher;
  private final TimetableRepository buffer;

  GtfsNewTripUpdateHandler(
    TripUpdateParser<GtfsRealtime.TripUpdate> parser,
    TripUpdateDispatcher dispatcher,
    TimetableRepository buffer
  ) {
    this.parser = parser;
    this.dispatcher = dispatcher;
    this.buffer = buffer;
  }

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

    List<UpdateSuccess> successes = new ArrayList<>();
    List<UpdateError> errors = new ArrayList<>();

    if (updateIncrementality == FULL_DATASET) {
      // Remove all updates from the buffer
      buffer.clear(feedId);
    }

    for (GtfsRealtime.TripUpdate update : updates) {
      try {
        successes.add(apply(update, updateIncrementality));
      } catch (UpdateException e) {
        errors.add(e.toError());
      } catch (DataValidationException e) {
        errors.add(DataValidationExceptionMapper.map(e).toError());
      } catch (Exception e) {
        // A trip update the unified path cannot even reject cleanly is a defect in this adapter,
        // but it has to cost that update alone. Letting it escape discards every update after it,
        // and for a full dataset the buffer has already been cleared - so the feed would lose all
        // of its real-time data until the next message arrives.
        LOG.warn("TripUpdate for trip {} failed.", update.getTrip().getTripId(), e);
        errors.add(UpdateException.noTripId(UNKNOWN).toError());
      }
    }

    LOG.debug("message contains {} trip updates", updates.size());

    return UpdateResult.of(successes, errors);
  }

  private UpdateSuccess apply(GtfsRealtime.TripUpdate update, UpdateIncrementality incrementality) {
    // Parse the GTFS-RT message
    var command = parser.parse(update);

    // out of precaution we don't allow the combination of differential and DUPLICATED
    // it's not clear what the semantics of this would be and particular how cancellation of a
    // duplicated trip would work.
    // please get in touch with the dev team if you need this functionality.
    if (command instanceof DuplicateTrip && incrementality == DIFFERENTIAL) {
      throw UpdateException.of(
        command.tripReference().tripId(),
        NOT_IMPLEMENTED_DIFFERENTIAL_DUPLICATED
      );
    }

    // Execute the command
    var tripUpdateResult = dispatcher.execute(command);

    // Commit the update to the snapshot and add any warnings
    return TripUpdateApplier.apply(buffer, tripUpdateResult.realTimeTripUpdate()).addWarnings(
      tripUpdateResult.warnings()
    );
  }

  /**
   * Parse the GTFS-RT message and execute the resulting command, without writing
   * the result to the snapshot buffer. Used by the shadow-comparison mode to dry-run the unified
   * path.
   */
  TripUpdateResult parseAndExecute(GtfsRealtime.TripUpdate update) {
    return dispatcher.execute(parser.parse(update));
  }
}
