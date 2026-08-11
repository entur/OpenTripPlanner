package org.opentripplanner.ext.updater.trip.unified.gtfs;

import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_INPUT_STRUCTURE;
import static org.opentripplanner.updater.spi.UpdateErrorType.NOT_IMPLEMENTED_UNSCHEDULED;

import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.TripUpdateParser;
import org.opentripplanner.ext.updater.trip.unified.TripUpdateType;
import org.opentripplanner.ext.updater.trip.unified.model.ServiceTime;
import org.opentripplanner.ext.updater.trip.unified.model.command.AddTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.CancelTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.DeleteTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.DuplicateTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.ModifyTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.ReviseTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripUpdateCommand;
import org.opentripplanner.ext.updater.trip.unified.model.command.VehicleDescription;
import org.opentripplanner.ext.updater.trip.unified.policy.FormatPolicy;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.model.TripUpdate;

/**
 * Parser for GTFS-RT TripUpdate messages into the common TripUpdateCommand model.
 */
public class GtfsRtTripUpdateParser implements TripUpdateParser<GtfsRealtime.TripUpdate> {

  private final ForwardsDelayPropagationType forwardsDelayPropagationType;
  private final BackwardsDelayPropagationType backwardsDelayPropagationType;
  private final boolean fuzzyMatchingEnabled;
  private final String feedId;
  private final ZoneId timeZone;
  private final Supplier<LocalDate> localDateNow;
  private final TripReferenceParser tripReferenceParser = new TripReferenceParser();

  public GtfsRtTripUpdateParser(
    ForwardsDelayPropagationType forwardsDelayPropagationType,
    BackwardsDelayPropagationType backwardsDelayPropagationType,
    boolean fuzzyMatchingEnabled,
    String feedId,
    ZoneId timeZone,
    Supplier<LocalDate> localDateNow
  ) {
    this.forwardsDelayPropagationType = forwardsDelayPropagationType;
    this.backwardsDelayPropagationType = backwardsDelayPropagationType;
    this.fuzzyMatchingEnabled = fuzzyMatchingEnabled;
    this.feedId = Objects.requireNonNull(feedId);
    this.timeZone = Objects.requireNonNull(timeZone);
    this.localDateNow = Objects.requireNonNull(localDateNow);
  }

  @Override
  public TripUpdateCommand parse(GtfsRealtime.TripUpdate update) {
    var tripUpdate = new TripUpdate(feedId, update, localDateNow);
    var tripId = tripUpdate.tripIdOrNull();
    try {
      return parseCommand(update, tripUpdate, tripId);
    } catch (UpdateException e) {
      // Only one trip identity is in play within a message, so rejections are thrown below
      // without one and the id is attached in this single place.
      throw tripId == null ? e : e.withTripId(tripId);
    }
  }

  private TripUpdateCommand parseCommand(
    GtfsRealtime.TripUpdate update,
    TripUpdate tripUpdate,
    @Nullable FeedScopedId tripId
  ) {
    var startTime = parseStartTime(update.getTrip());

    // GTFS-RT names a trip by its trip_id. A feed whose producer cannot supply one names it by its
    // schedule instead - route, direction, start time and start date, all four - and the
    // deployment declares that with the fuzzyTripMatching config parameter. A message that names
    // its trip neither way names no trip. Whether the schedule tuple names a trip that exists is
    // not knowable here; the fuzzy matcher gives that verdict at resolution.
    if (tripId == null) {
      if (!fuzzyMatchingEnabled || !identifiesTripBySchedule(tripUpdate, startTime)) {
        throw UpdateException.noTripId(INVALID_INPUT_STRUCTURE);
      }
      tripUpdate.validateWithoutTripId();
    } else {
      tripUpdate.validate();
    }

    var scheduleRelationship = tripUpdate.scheduleRelationship();
    LocalDate serviceDate = tripUpdate.startDate();

    var tripReference = tripReferenceParser.parse(tripId, tripUpdate, startTime);
    var updateType = mapScheduleRelationship(scheduleRelationship);

    // An added trip is created under the id the message gives it, so no match can supply one and
    // a message adding a trip without naming it is invalid. Legacy instead binds such a message to
    // the id of whatever scheduled trip its tuple happens to match and then rejects it as
    // TRIP_ALREADY_EXISTS - the same rejection, reached through a model lookup whose only possible
    // product is that rejection. An added trip that does name itself is taken at its word: the
    // matcher is never consulted for an addition, where legacy's blanket pre-parse rewrite fires
    // for it too.
    if (updateType == TripUpdateType.ADD_NEW_TRIP && tripId == null) {
      throw UpdateException.noTripId(INVALID_INPUT_STRUCTURE);
    }

    var gtfsPolicy = FormatPolicy.gtfsRt(
      forwardsDelayPropagationType,
      backwardsDelayPropagationType
    );

    if (updateType == TripUpdateType.CANCEL_TRIP) {
      return new CancelTrip(tripReference, serviceDate, null, null);
    }
    if (updateType == TripUpdateType.DELETE_TRIP) {
      return new DeleteTrip(tripReference, serviceDate, null, null);
    }
    if (updateType == TripUpdateType.DUPLICATE_TRIP) {
      // A duplication runs the copy at the start time and date the message reports, so it is
      // incomplete without them. Not the shared wrapper's validateDuplicated(), whose LocalTime
      // reading of the start time cannot express a duplicate departing after midnight.
      if (startTime == null || tripUpdate.reportedStartDate().isEmpty()) {
        throw UpdateException.of(INVALID_INPUT_STRUCTURE);
      }
      return new DuplicateTrip(tripReference, serviceDate, startTime);
    }

    var stopTimeUpdates = new StopTimeUpdateParser(
      feedId,
      serviceDate,
      timeZone,
      updateType == TripUpdateType.ADD_NEW_TRIP || updateType == TripUpdateType.MODIFY_TRIP
    ).parse(tripUpdate.stopTimeUpdates());

    var vehicle = VehicleDescription.of(
      tripUpdate.vehicleId().orElse(null),
      tripUpdate.wheelchairAccessibility().orElse(null)
    );
    var tripHeadsign = tripUpdate.tripHeadsign().orElse(null);

    return switch (updateType) {
      case UPDATE_EXISTING -> ReviseTrip.builder(tripReference, serviceDate)
        .withFormatPolicy(gtfsPolicy)
        .withVehicleDescription(vehicle)
        .withTripHeadsign(tripHeadsign)
        .withStopTimeUpdates(stopTimeUpdates)
        .build();
      case MODIFY_TRIP -> ModifyTrip.builder(tripReference, serviceDate)
        .withFormatPolicy(gtfsPolicy)
        .withVehicleDescription(vehicle)
        .withTripHeadsign(tripHeadsign)
        .withStopTimeUpdates(stopTimeUpdates)
        .build();
      case ADD_NEW_TRIP -> AddTrip.builder(
        tripReference,
        serviceDate,
        TripCreationInfoParser.parse(tripId, tripUpdate)
      )
        .withFormatPolicy(gtfsPolicy)
        .withVehicleDescription(vehicle)
        .withTripHeadsign(tripHeadsign)
        .withStopTimeUpdates(stopTimeUpdates)
        .build();
      case CANCEL_TRIP, DELETE_TRIP, DUPLICATE_TRIP -> throw new IllegalStateException(
        "Unexpected update type: " + updateType
      );
    };
  }

  private TripUpdateType mapScheduleRelationship(ScheduleRelationship relationship) {
    return switch (relationship) {
      case SCHEDULED -> TripUpdateType.UPDATE_EXISTING;
      case CANCELED -> TripUpdateType.CANCEL_TRIP;
      case DELETED -> TripUpdateType.DELETE_TRIP;
      case NEW, ADDED -> TripUpdateType.ADD_NEW_TRIP;
      case REPLACEMENT -> TripUpdateType.MODIFY_TRIP;
      case DUPLICATED -> TripUpdateType.DUPLICATE_TRIP;
      case UNSCHEDULED -> throw UpdateException.of(NOT_IMPLEMENTED_UNSCHEDULED);
    };
  }

  /**
   * The {@code start_time} the message reports, or {@code null} when it reports none. Read off the
   * protobuf directly: a GTFS time is relative to the service date's midnight and passes 24:00:00
   * for a trip that starts after midnight - the spec's own example is 25:15:00 - a form the shared
   * wrapper's {@link java.time.LocalTime} reading cannot express.
   */
  @Nullable
  private ServiceTime parseStartTime(GtfsRealtime.TripDescriptor tripDescriptor) {
    if (!tripDescriptor.hasStartTime()) {
      return null;
    }
    try {
      return ServiceTime.parse(tripDescriptor.getStartTime());
    } catch (IllegalArgumentException e) {
      throw UpdateException.of(INVALID_INPUT_STRUCTURE);
    }
  }

  /**
   * Whether the message names its trip by the schedule: route, direction, start time and start
   * date, all four - the same fields the fuzzy matcher requires, because no subset of them
   * identifies one trip.
   */
  private boolean identifiesTripBySchedule(TripUpdate tripUpdate, @Nullable ServiceTime startTime) {
    return (
      tripUpdate.routeId().isPresent() &&
      tripUpdate.descriptor().directionId().isPresent() &&
      startTime != null &&
      tripUpdate.reportedStartDate().isPresent()
    );
  }
}
