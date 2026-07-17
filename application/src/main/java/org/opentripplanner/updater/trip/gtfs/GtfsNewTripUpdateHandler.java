package org.opentripplanner.updater.trip.gtfs;

import static org.opentripplanner.updater.spi.UpdateErrorType.NOT_IMPLEMENTED_DIFFERENTIAL_DUPLICATED;
import static org.opentripplanner.updater.trip.UpdateIncrementality.DIFFERENTIAL;
import static org.opentripplanner.updater.trip.UpdateIncrementality.FULL_DATASET;

import com.google.transit.realtime.GtfsRealtime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.repository.MutableTimetableSnapshot;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.spi.UpdateSuccess;
import org.opentripplanner.updater.trip.TripUpdateApplier;
import org.opentripplanner.updater.trip.TripUpdateDispatcher;
import org.opentripplanner.updater.trip.TripUpdateResult;
import org.opentripplanner.updater.trip.UpdateIncrementality;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;
import org.opentripplanner.updater.trip.model.TripDuplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Update-scoped task produced by {@link GtfsNewTripUpdateAdapter#forUpdate}. Parses each
 * TripUpdate message, dispatches it to the matching domain operation and writes the result to
 * the mutable timetable snapshot of the current update task.
 * <p>
 * The fuzzy trip matcher and delay propagation types passed to {@link #applyTripUpdates} are
 * ignored: the unified path configures fuzzy matching and delay interpolation once, in the
 * application-scoped {@link GtfsNewTripUpdateAdapter}.
 */
class GtfsNewTripUpdateHandler implements GtfsTripUpdateHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GtfsNewTripUpdateHandler.class);

  private final GtfsRtTripUpdateParser parser;
  private final TripUpdateDispatcher dispatcher;
  private final MutableTimetableSnapshot buffer;
  private final Map<FeedScopedId, Route> realtimeRouteCache;

  GtfsNewTripUpdateHandler(
    GtfsRtTripUpdateParser parser,
    TripUpdateDispatcher dispatcher,
    MutableTimetableSnapshot buffer,
    Map<FeedScopedId, Route> realtimeRouteCache
  ) {
    this.parser = parser;
    this.dispatcher = dispatcher;
    this.buffer = buffer;
    this.realtimeRouteCache = realtimeRouteCache;
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
      }
    }

    LOG.debug("message contains {} trip updates", updates.size());

    return UpdateResult.of(successes, errors);
  }

  private UpdateSuccess apply(GtfsRealtime.TripUpdate update, UpdateIncrementality incrementality) {
    // Parse the GTFS-RT message
    var parsedUpdate = parser.parse(update);

    // out of precaution we don't allow the combination of differential and DUPLICATED
    // it's not clear what the semantics of this would be and particular how cancellation of a
    // duplicated trip would work.
    // please get in touch with the dev team if you need this functionality.
    if (parsedUpdate instanceof TripDuplication && incrementality == DIFFERENTIAL) {
      throw UpdateException.of(
        parsedUpdate.tripReference().tripId(),
        NOT_IMPLEMENTED_DIFFERENTIAL_DUPLICATED
      );
    }

    // Apply the parsed update
    var tripUpdateResult = dispatcher.apply(parsedUpdate);
    var realTimeTripUpdate = tripUpdateResult.realTimeTripUpdate();
    cacheCreatedRoute(realTimeTripUpdate);

    // Commit the update to the snapshot and add any warnings
    return TripUpdateApplier.apply(buffer, realTimeTripUpdate).addWarnings(
      tripUpdateResult.warnings()
    );
  }

  /**
   * Parse the GTFS-RT message and dispatch it to the matching domain operation, without writing
   * the result to the snapshot buffer. Used by the shadow-comparison mode to dry-run the unified
   * path.
   */
  TripUpdateResult parseAndDispatch(GtfsRealtime.TripUpdate update) {
    return dispatcher.apply(parser.parse(update));
  }

  /**
   * Cache the route if it's a new trip with route creation.
   */
  private void cacheCreatedRoute(RealTimeTripUpdate realTimeTripUpdate) {
    if (realTimeTripUpdate.tripCreation() && realTimeTripUpdate.routeCreation()) {
      Route route = realTimeTripUpdate.pattern().getRoute();
      realtimeRouteCache.put(route.getId(), route);
    }
  }
}
