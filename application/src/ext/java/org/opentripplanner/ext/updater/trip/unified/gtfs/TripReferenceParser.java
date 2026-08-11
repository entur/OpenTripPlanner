package org.opentripplanner.ext.updater.trip.unified.gtfs;

import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.model.ServiceTime;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripReference;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.gtfs.mapping.DirectionMapper;
import org.opentripplanner.updater.trip.gtfs.model.TripUpdate;

/**
 * Parses the trip reference of one GTFS-RT TripUpdate - the fields the message names its trip by.
 */
final class TripReferenceParser {

  private final DirectionMapper directionMapper = new DirectionMapper(DataImportIssueStore.NOOP);

  /**
   * @param tripId the trip id the message reports - {@code null} for a message that names its trip
   *               by the schedule instead
   * @param startTime the start time the message reports, or {@code null} when it reports none
   */
  TripReference parse(
    @Nullable FeedScopedId tripId,
    TripUpdate tripUpdate,
    @Nullable ServiceTime startTime
  ) {
    // Only the date the feed reported, not the service date resolved from it: the reference says what
    // the feed said about the trip, and a fuzzy match may only identify a trip by a reported date.
    var builder = TripReference.builder();

    if (tripId != null) {
      builder.withTripId(tripId);
    }

    tripUpdate.reportedStartDate().ifPresent(builder::withStartDate);

    tripUpdate.routeId().ifPresent(builder::withRouteId);

    if (startTime != null) {
      builder.withStartTime(startTime);
    }

    tripUpdate
      .descriptor()
      .directionId()
      .ifPresent(dirId -> builder.withDirection(directionMapper.map(dirId)));

    return builder.build();
  }
}
