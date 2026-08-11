package org.opentripplanner.ext.updater.trip.unified.gtfs;

import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_ARRIVAL_TIME;
import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_DEPARTURE_TIME;
import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_INPUT_STRUCTURE;
import static org.opentripplanner.updater.spi.UpdateErrorType.NOT_IMPLEMENTED_UNSCHEDULED;

import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.TripUpdateParser;
import org.opentripplanner.ext.updater.trip.unified.TripUpdateType;
import org.opentripplanner.ext.updater.trip.unified.model.ServiceTime;
import org.opentripplanner.ext.updater.trip.unified.model.StopSequence;
import org.opentripplanner.ext.updater.trip.unified.model.command.AddTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.CancelTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.DeleteTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.DuplicateTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.ModifyTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.ParsedStopTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.ReviseTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.RouteCreationInfo;
import org.opentripplanner.ext.updater.trip.unified.model.command.StopReference;
import org.opentripplanner.ext.updater.trip.unified.model.command.StopResolutionStrategy;
import org.opentripplanner.ext.updater.trip.unified.model.command.TimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripCreationInfo;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripReference;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripUpdateCommand;
import org.opentripplanner.ext.updater.trip.unified.model.command.VehicleDescription;
import org.opentripplanner.ext.updater.trip.unified.policy.FormatPolicy;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.gtfs.mapping.DirectionMapper;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.model.AddedRoute;
import org.opentripplanner.updater.trip.gtfs.model.StopTimeUpdate;
import org.opentripplanner.updater.trip.gtfs.model.TripUpdate;
import org.opentripplanner.utils.time.ServiceDateUtils;

/**
 * Parser for GTFS-RT TripUpdate messages into the common TripUpdateCommand model.
 */
public class GtfsRtTripUpdateParser implements TripUpdateParser<GtfsRealtime.TripUpdate> {

  /**
   * How far past the start of its service day a call of a trip that brings its own schedule may
   * lie. GTFS bounds a service day at 48 hours, and a time outside the day the message names is a
   * producer error - typically a timestamp on the wrong day - that would publish the trip on the
   * wrong service day.
   */
  private static final long MAX_ARRIVAL_DEPARTURE_TIME = 48 * 60 * 60;

  private final ForwardsDelayPropagationType forwardsDelayPropagationType;
  private final BackwardsDelayPropagationType backwardsDelayPropagationType;
  private final boolean fuzzyMatchingEnabled;
  private final String feedId;
  private final ZoneId timeZone;
  private final Supplier<LocalDate> localDateNow;
  private final DirectionMapper directionMapper = new DirectionMapper(DataImportIssueStore.NOOP);

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

    var tripReference = buildTripReference(tripId, tripUpdate, startTime);
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

    var stopTimeUpdates = parseStopTimeUpdates(
      tripUpdate.stopTimeUpdates(),
      serviceDate,
      updateType == TripUpdateType.ADD_NEW_TRIP || updateType == TripUpdateType.MODIFY_TRIP
    );

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
        buildTripCreationInfo(tripId, tripUpdate)
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

  private FeedScopedId createId(String entityId) {
    return new FeedScopedId(feedId, entityId);
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

  private TripReference buildTripReference(
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

  /**
   * @param reportsOwnSchedule whether the trip brings its own schedule with it, as NEW, ADDED and
   *                           REPLACEMENT trips do. Such a trip gets a pattern of its own, built
   *                           from the times its calls report, so a call that reports only a
   *                           scheduled time still has to produce one.
   */
  private List<ParsedStopTimeUpdate> parseStopTimeUpdates(
    List<StopTimeUpdate> updates,
    LocalDate serviceDate,
    boolean reportsOwnSchedule
  ) {
    var result = new ArrayList<ParsedStopTimeUpdate>();
    long startOfService = startOfServiceSecondsSinceEpoch(serviceDate);

    for (var i = 0; i < updates.size(); i++) {
      var update = updates.get(i);
      var stopId = update.stopId().map(this::createId);
      var assignedStopId = update.assignedStopId().map(this::createId).orElse(null);
      var stopSequence = parseStopSequence(update);

      // Both stop_id and stop_sequence are missing — invalid stop time update
      if (stopId.isEmpty() && stopSequence == null) {
        throw UpdateException.of(UpdateErrorType.INVALID_STOP_REFERENCE);
      }

      // Create StopReference - may have null stopId if only stopSequence is provided
      var stopReference = stopId.isPresent()
        ? StopReference.ofStopId(stopId.get(), assignedStopId)
        : new StopReference(null, assignedStopId, StopResolutionStrategy.DIRECT);

      var builder = ParsedStopTimeUpdate.builder(stopReference);

      if (stopSequence != null) {
        builder.withStopSequence(stopSequence);
      }

      var status = mapStopTimeStatus(update);
      builder.withStatus(status);

      // An arrival or departure of a trip running to an existing schedule must state a time or a
      // delay - an event stating neither is a producer error, not an unreported call, so the
      // whole entity is rejected rather than letting the interpolator fill the call in. A trip
      // that brings its own schedule is exempt: its calls may state only a scheduled time.
      if (!reportsOwnSchedule && status == ParsedStopTimeUpdate.StopUpdateStatus.SCHEDULED) {
        if (!update.isArrivalValid()) {
          throw UpdateException.ofStopPosition(INVALID_ARRIVAL_TIME, i);
        }
        if (!update.isDepartureValid()) {
          throw UpdateException.ofStopPosition(INVALID_DEPARTURE_TIME, i);
        }
      }

      // A trip that brings its own schedule places its calls by the scheduled times it reports,
      // so each of them must lie within the service day the message names - from its start to
      // the 48-hour limit. A time outside it is a producer error, typically a timestamp on the
      // wrong day, that would publish the trip on the wrong service day.
      if (reportsOwnSchedule) {
        if (
          !isWithinServiceDay(update.scheduledArrivalTimeWithRealTimeFallback(), startOfService)
        ) {
          throw UpdateException.ofStopPosition(INVALID_ARRIVAL_TIME, i);
        }
        if (
          !isWithinServiceDay(update.scheduledDepartureTimeWithRealTimeFallback(), startOfService)
        ) {
          throw UpdateException.ofStopPosition(INVALID_DEPARTURE_TIME, i);
        }
      }

      parseStopTimeUpdateTimes(update, builder, startOfService, reportsOwnSchedule);

      update.stopHeadsign().ifPresent(builder::withStopHeadsign);

      update.pickup().ifPresent(builder::withPickup);
      update.dropoff().ifPresent(builder::withDropoff);

      result.add(builder.build());
    }

    return result;
  }

  /**
   * The {@code stop_sequence} the call reports, or {@code null} when it reports none. A negative
   * value - the protobuf uint32 read overflowing the Java int - has already been rejected as
   * INVALID_STOP_SEQUENCE by the wrapper validation {@link #parse} runs first, so the value
   * object's own check is an invariant here, not a rejection path.
   */
  @Nullable
  private StopSequence parseStopSequence(StopTimeUpdate update) {
    var stopSequence = update.stopSequence();
    return stopSequence.isPresent() ? StopSequence.of(stopSequence.getAsInt()) : null;
  }

  /**
   * Whether the scheduled time the call reports lies within its service day - from the start of
   * service to the 48-hour limit. A call that reports no scheduled time has nothing to hold
   * against the day.
   */
  private static boolean isWithinServiceDay(OptionalLong scheduledTime, long startOfService) {
    if (scheduledTime.isEmpty()) {
      return true;
    }
    long secondsPastMidnight = scheduledTime.getAsLong() - startOfService;
    return 0 <= secondsPastMidnight && secondsPastMidnight <= MAX_ARRIVAL_DEPARTURE_TIME;
  }

  private ParsedStopTimeUpdate.StopUpdateStatus mapStopTimeStatus(StopTimeUpdate update) {
    if (update.isSkipped()) {
      return ParsedStopTimeUpdate.StopUpdateStatus.SKIPPED;
    }
    if (update.isNoData()) {
      return ParsedStopTimeUpdate.StopUpdateStatus.NO_DATA;
    }
    return ParsedStopTimeUpdate.StopUpdateStatus.SCHEDULED;
  }

  private void parseStopTimeUpdateTimes(
    StopTimeUpdate update,
    ParsedStopTimeUpdate.Builder builder,
    long startOfService,
    boolean reportsOwnSchedule
  ) {
    var arrival = parseTimeUpdate(
      update.arrivalTime(),
      update.arrivalDelay(),
      update.scheduledArrivalTimeWithRealTimeFallback(),
      startOfService,
      reportsOwnSchedule
    );
    if (arrival != null) {
      builder.withArrivalUpdate(arrival);
    }

    var departure = parseTimeUpdate(
      update.departureTime(),
      update.departureDelay(),
      update.scheduledDepartureTimeWithRealTimeFallback(),
      startOfService,
      reportsOwnSchedule
    );
    if (departure != null) {
      builder.withDepartureUpdate(departure);
    }
  }

  /**
   * The update for one end of a call - its arrival or its departure - or {@code null} if the
   * message states nothing about it.
   * <p>
   * A predicted time is taken as it is given, and the scheduled time the message reports alongside
   * it is carried along as the aimed time. A call of a trip that reports its own schedule may state
   * only that scheduled time, and then the call runs to the schedule it reported, offset by the
   * delay if it stated one. Otherwise the only thing left to go by is the delay, which is
   * meaningful just for a trip that already has a scheduled timetable to apply it to.
   *
   * @param time           the predicted time, as an absolute timestamp
   * @param delay          the delay against the scheduled time
   * @param aimedTime      the scheduled time as reported by the message, as an absolute timestamp -
   *                       derived from {@code time - delay} where the message states no scheduled
   *                       time
   * @param startOfService the origin the absolute timestamps are measured from
   * @param reportsOwnSchedule whether the trip brings its own schedule with it - see
   *                           {@link #parseStopTimeUpdates}
   */
  @Nullable
  private TimeUpdate parseTimeUpdate(
    OptionalLong time,
    OptionalInt delay,
    OptionalLong aimedTime,
    long startOfService,
    boolean reportsOwnSchedule
  ) {
    ServiceTime aimed = aimedTime.isPresent()
      ? ServiceTime.ofSecondsPastMidnight((int) (aimedTime.getAsLong() - startOfService))
      : null;

    if (time.isPresent()) {
      return TimeUpdate.ofAbsolute(
        ServiceTime.ofSecondsPastMidnight((int) (time.getAsLong() - startOfService)),
        aimed
      );
    }
    if (reportsOwnSchedule && aimed != null) {
      return TimeUpdate.ofAbsolute(aimed.plusSeconds(delay.orElse(0)), aimed);
    }
    if (delay.isPresent()) {
      return TimeUpdate.ofDelay(delay.getAsInt());
    }
    return null;
  }

  /**
   * The origin absolute {@code StopTimeEvent} timestamps are measured from: the start of the GTFS
   * service day, which is noon minus twelve hours and not calendar midnight. The two differ by the
   * offset shift on a service date containing a daylight-saving transition.
   */
  private long startOfServiceSecondsSinceEpoch(LocalDate serviceDate) {
    return ServiceDateUtils.asStartOfService(serviceDate, timeZone).toEpochSecond();
  }

  private TripCreationInfo buildTripCreationInfo(FeedScopedId tripId, TripUpdate tripUpdate) {
    var builder = TripCreationInfo.builder(tripId);

    // Get route ID from trip update
    var routeId = tripUpdate.routeId().orElse(null);

    if (routeId != null) {
      builder.withRouteId(routeId);
    }

    tripUpdate.tripShortName().ifPresent(builder::withTripShortName);

    // Extract route creation info from MFDZ extensions
    var addedRoute = AddedRoute.ofTripDescriptor(tripUpdate);
    if (routeId != null && (addedRoute.routeUrl() != null || addedRoute.routeLongName() != null)) {
      var agencyId = addedRoute.agencyId() != null
        ? new FeedScopedId(tripId.getFeedId(), addedRoute.agencyId())
        : null;
      var mode = org.opentripplanner.gtfs.mapping.TransitModeMapper.mapMode(addedRoute.routeType());
      var routeCreationInfo = new RouteCreationInfo(
        addedRoute.routeLongName(),
        mode,
        null,
        null,
        addedRoute.routeUrl(),
        agencyId,
        addedRoute.routeType()
      );
      builder.withRouteCreationInfo(routeCreationInfo);
    }

    return builder.build();
  }
}
