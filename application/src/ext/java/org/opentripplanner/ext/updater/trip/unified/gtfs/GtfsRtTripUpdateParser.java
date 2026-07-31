package org.opentripplanner.ext.updater.trip.unified.gtfs;

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
import org.opentripplanner.utils.time.TimeUtils;

/**
 * Parser for GTFS-RT TripUpdate messages into the common TripUpdateCommand model.
 */
public class GtfsRtTripUpdateParser implements TripUpdateParser<GtfsRealtime.TripUpdate> {

  private final ForwardsDelayPropagationType forwardsDelayPropagationType;
  private final BackwardsDelayPropagationType backwardsDelayPropagationType;
  private final String feedId;
  private final ZoneId timeZone;
  private final Supplier<LocalDate> localDateNow;
  private final DirectionMapper directionMapper = new DirectionMapper(DataImportIssueStore.NOOP);

  public GtfsRtTripUpdateParser(
    ForwardsDelayPropagationType forwardsDelayPropagationType,
    BackwardsDelayPropagationType backwardsDelayPropagationType,
    String feedId,
    ZoneId timeZone,
    Supplier<LocalDate> localDateNow
  ) {
    this.forwardsDelayPropagationType = forwardsDelayPropagationType;
    this.backwardsDelayPropagationType = backwardsDelayPropagationType;
    this.feedId = Objects.requireNonNull(feedId);
    this.timeZone = Objects.requireNonNull(timeZone);
    this.localDateNow = Objects.requireNonNull(localDateNow);
  }

  @Override
  public TripUpdateCommand parse(GtfsRealtime.TripUpdate update) {
    var tripUpdate = new TripUpdate(feedId, update, localDateNow);

    tripUpdate.validate();

    var tripId = tripUpdate.tripId();
    var scheduleRelationship = tripUpdate.scheduleRelationship();
    LocalDate serviceDate = tripUpdate.startDate();

    var tripReference = buildTripReference(tripId, tripUpdate);
    var updateType = mapScheduleRelationship(scheduleRelationship);

    if (updateType == null) {
      throw switch (scheduleRelationship) {
        case UNSCHEDULED -> UpdateException.of(tripId, NOT_IMPLEMENTED_UNSCHEDULED);
        default -> UpdateException.of(tripId, INVALID_INPUT_STRUCTURE);
      };
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
      tripUpdate.validateDuplicated();
      return new DuplicateTrip(tripReference, serviceDate, tripUpdate.startTime().orElseThrow());
    }

    var stopTimeUpdates = parseStopTimeUpdates(
      tripId,
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

  @Nullable
  private TripUpdateType mapScheduleRelationship(ScheduleRelationship relationship) {
    return switch (relationship) {
      case SCHEDULED -> TripUpdateType.UPDATE_EXISTING;
      case CANCELED -> TripUpdateType.CANCEL_TRIP;
      case DELETED -> TripUpdateType.DELETE_TRIP;
      case NEW, ADDED -> TripUpdateType.ADD_NEW_TRIP;
      case REPLACEMENT -> TripUpdateType.MODIFY_TRIP;
      case DUPLICATED -> TripUpdateType.DUPLICATE_TRIP;
      case UNSCHEDULED -> null;
    };
  }

  private TripReference buildTripReference(FeedScopedId tripId, TripUpdate tripUpdate) {
    // Only the date the feed reported, not the service date resolved from it: the reference says what
    // the feed said about the trip, and a fuzzy match may only identify a trip by a reported date.
    var builder = TripReference.builder().withTripId(tripId);

    tripUpdate.reportedStartDate().ifPresent(builder::withStartDate);

    tripUpdate.routeId().ifPresent(builder::withRouteId);

    tripUpdate
      .startTime()
      .ifPresent(time -> builder.withStartTime(TimeUtils.timeToStrCompact(time.toSecondOfDay())));

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
    FeedScopedId tripId,
    List<StopTimeUpdate> updates,
    LocalDate serviceDate,
    boolean reportsOwnSchedule
  ) {
    var result = new ArrayList<ParsedStopTimeUpdate>();

    for (var update : updates) {
      var stopId = update.stopId().map(this::createId);
      var assignedStopId = update.assignedStopId().map(this::createId).orElse(null);
      var stopSequence = update.stopSequence();

      // Both stop_id and stop_sequence are missing — invalid stop time update
      if (stopId.isEmpty() && stopSequence.isEmpty()) {
        throw UpdateException.of(tripId, UpdateErrorType.INVALID_STOP_REFERENCE);
      }

      // Create StopReference - may have null stopId if only stopSequence is provided
      var stopReference = stopId.isPresent()
        ? StopReference.ofStopId(stopId.get(), assignedStopId)
        : new StopReference(null, assignedStopId, StopResolutionStrategy.DIRECT);

      var builder = ParsedStopTimeUpdate.builder(stopReference);

      stopSequence.ifPresent(builder::withStopSequence);

      var status = mapStopTimeStatus(update);
      builder.withStatus(status);

      parseStopTimeUpdateTimes(update, builder, serviceDate, reportsOwnSchedule);

      update.stopHeadsign().ifPresent(builder::withStopHeadsign);

      update.pickup().ifPresent(builder::withPickup);
      update.dropoff().ifPresent(builder::withDropoff);

      result.add(builder.build());
    }

    return result;
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
    LocalDate serviceDate,
    boolean reportsOwnSchedule
  ) {
    long startOfService = startOfServiceSecondsSinceEpoch(serviceDate);

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
    Integer aimed = aimedTime.isPresent() ? (int) (aimedTime.getAsLong() - startOfService) : null;

    if (time.isPresent()) {
      return TimeUpdate.ofAbsolute((int) (time.getAsLong() - startOfService), aimed);
    }
    if (reportsOwnSchedule && aimed != null) {
      return TimeUpdate.ofAbsolute(aimed + delay.orElse(0), aimed);
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
