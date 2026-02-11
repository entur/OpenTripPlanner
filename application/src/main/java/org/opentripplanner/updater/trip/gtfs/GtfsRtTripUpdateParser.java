package org.opentripplanner.updater.trip.gtfs;

import static org.opentripplanner.updater.spi.UpdateError.UpdateErrorType.INVALID_INPUT_STRUCTURE;
import static org.opentripplanner.updater.spi.UpdateError.UpdateErrorType.NOT_IMPLEMENTED_DUPLICATED;
import static org.opentripplanner.updater.spi.UpdateError.UpdateErrorType.NOT_IMPLEMENTED_UNSCHEDULED;

import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.basic.Accessibility;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.timetable.Direction;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.TripUpdateParser;
import org.opentripplanner.updater.trip.TripUpdateParserContext;
import org.opentripplanner.updater.trip.gtfs.model.AddedRoute;
import org.opentripplanner.updater.trip.gtfs.model.StopTimeUpdate;
import org.opentripplanner.updater.trip.gtfs.model.TripDescriptor;
import org.opentripplanner.updater.trip.gtfs.model.TripUpdate;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.RouteCreationInfo;
import org.opentripplanner.updater.trip.model.StopReference;
import org.opentripplanner.updater.trip.model.StopResolutionStrategy;
import org.opentripplanner.updater.trip.model.TimeUpdate;
import org.opentripplanner.updater.trip.model.TripCreationInfo;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripUpdateOptions;
import org.opentripplanner.updater.trip.model.TripUpdateType;

/**
 * Parser for GTFS-RT TripUpdate messages into the common ParsedTripUpdate model.
 */
public class GtfsRtTripUpdateParser implements TripUpdateParser<GtfsRealtime.TripUpdate> {

  private final ForwardsDelayPropagationType forwardsDelayPropagationType;
  private final BackwardsDelayPropagationType backwardsDelayPropagationType;
  private final boolean fuzzyMatchingEnabled;

  public GtfsRtTripUpdateParser(
    ForwardsDelayPropagationType forwardsDelayPropagationType,
    BackwardsDelayPropagationType backwardsDelayPropagationType
  ) {
    this(forwardsDelayPropagationType, backwardsDelayPropagationType, false);
  }

  public GtfsRtTripUpdateParser(
    ForwardsDelayPropagationType forwardsDelayPropagationType,
    BackwardsDelayPropagationType backwardsDelayPropagationType,
    boolean fuzzyMatchingEnabled
  ) {
    this.forwardsDelayPropagationType = forwardsDelayPropagationType;
    this.backwardsDelayPropagationType = backwardsDelayPropagationType;
    this.fuzzyMatchingEnabled = fuzzyMatchingEnabled;
  }

  @Override
  public Result<ParsedTripUpdate, UpdateError> parse(
    GtfsRealtime.TripUpdate update,
    TripUpdateParserContext context
  ) {
    var tripUpdate = new TripUpdate(update);
    var tripDescriptor = tripUpdate.tripDescriptor();

    var tripIdOpt = tripDescriptor.tripId().map(id -> context.createId(id));
    if (tripIdOpt.isEmpty()) {
      return Result.failure(UpdateError.noTripId(INVALID_INPUT_STRUCTURE));
    }

    var tripId = tripIdOpt.get();
    var scheduleRelationship = tripDescriptor.scheduleRelationship();

    LocalDate serviceDate;
    try {
      serviceDate = tripDescriptor.startDate().orElse(context.localDateNow().get());
    } catch (ParseException e) {
      return UpdateError.result(tripId, INVALID_INPUT_STRUCTURE, e.getMessage());
    }

    var tripReference = buildTripReference(tripId, tripDescriptor, serviceDate);
    var updateType = mapScheduleRelationship(scheduleRelationship);

    if (updateType == null) {
      return switch (scheduleRelationship) {
        case UNSCHEDULED -> UpdateError.result(tripId, NOT_IMPLEMENTED_UNSCHEDULED);
        case DUPLICATED -> UpdateError.result(tripId, NOT_IMPLEMENTED_DUPLICATED);
        default -> UpdateError.result(
          tripId,
          INVALID_INPUT_STRUCTURE,
          "Unknown schedule relationship"
        );
      };
    }

    var builder = ParsedTripUpdate.builder(updateType, tripReference, serviceDate).withOptions(
      TripUpdateOptions.gtfsRtDefaults(forwardsDelayPropagationType, backwardsDelayPropagationType)
    );

    if (updateType == TripUpdateType.CANCEL_TRIP || updateType == TripUpdateType.DELETE_TRIP) {
      return Result.success(builder.build());
    }

    var stopTimeUpdates = parseStopTimeUpdates(
      tripUpdate.stopTimeUpdates(),
      context,
      serviceDate,
      updateType == TripUpdateType.ADD_NEW_TRIP
    );
    builder.withStopTimeUpdates(stopTimeUpdates);

    if (updateType == TripUpdateType.ADD_NEW_TRIP || updateType == TripUpdateType.MODIFY_TRIP) {
      var creationInfo = buildTripCreationInfo(tripId, tripDescriptor, tripUpdate);
      builder.withTripCreationInfo(creationInfo);
    }

    return Result.success(builder.build());
  }

  @Nullable
  private TripUpdateType mapScheduleRelationship(ScheduleRelationship relationship) {
    return switch (relationship) {
      case SCHEDULED -> TripUpdateType.UPDATE_EXISTING;
      case CANCELED -> TripUpdateType.CANCEL_TRIP;
      case DELETED -> TripUpdateType.DELETE_TRIP;
      case NEW, ADDED -> TripUpdateType.ADD_NEW_TRIP;
      case REPLACEMENT -> TripUpdateType.MODIFY_TRIP;
      case UNSCHEDULED, DUPLICATED -> null;
    };
  }

  private TripReference buildTripReference(
    FeedScopedId tripId,
    TripDescriptor descriptor,
    LocalDate serviceDate
  ) {
    var builder = TripReference.builder().withTripId(tripId).withStartDate(serviceDate);

    descriptor
      .routeId()
      .map(id -> new FeedScopedId(tripId.getFeedId(), id))
      .ifPresent(builder::withRouteId);

    descriptor
      .startTime()
      .ifPresent(time ->
        builder.withStartTime(org.opentripplanner.utils.time.TimeUtils.timeToStrCompact(time))
      );

    descriptor.directionId().ifPresent(dirId -> builder.withDirection(mapDirection(dirId)));

    if (fuzzyMatchingEnabled) {
      builder.withFuzzyMatchingHint(TripReference.FuzzyMatchingHint.FUZZY_MATCH_ALLOWED);
    }

    return builder.build();
  }

  private Direction mapDirection(int gtfsDirectionId) {
    return switch (gtfsDirectionId) {
      case 0 -> Direction.OUTBOUND;
      case 1 -> Direction.INBOUND;
      default -> Direction.UNKNOWN;
    };
  }

  private List<ParsedStopTimeUpdate> parseStopTimeUpdates(
    List<StopTimeUpdate> updates,
    TripUpdateParserContext context,
    LocalDate serviceDate,
    boolean isNewTrip
  ) {
    var result = new ArrayList<ParsedStopTimeUpdate>();

    for (var update : updates) {
      var stopId = update.stopId().map(context::createId);
      var assignedStopId = update.assignedStopId().map(context::createId).orElse(null);
      var stopSequence = update.stopSequence();

      // Skip only if BOTH stop_id and stop_sequence are missing
      // GTFS-RT allows using either for matching
      if (stopId.isEmpty() && stopSequence.isEmpty()) {
        continue;
      }

      // Create StopReference - may have null stopId if only stopSequence is provided
      var stopReference = stopId.isPresent()
        ? StopReference.ofStopId(stopId.get(), assignedStopId)
        : new StopReference(null, assignedStopId, StopResolutionStrategy.DIRECT);

      var builder = ParsedStopTimeUpdate.builder(stopReference);

      stopSequence.ifPresent(builder::withStopSequence);

      var status = mapStopTimeStatus(update);
      builder.withStatus(status);

      if (isNewTrip) {
        parseNewTripStopTimeUpdate(update, builder, serviceDate, context);
      } else {
        parseScheduledTripStopTimeUpdate(update, builder, serviceDate, context);
      }

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

  private void parseScheduledTripStopTimeUpdate(
    StopTimeUpdate update,
    ParsedStopTimeUpdate.Builder builder,
    LocalDate serviceDate,
    TripUpdateParserContext context
  ) {
    // Calculate midnight seconds for absolute time conversion
    var midnight = serviceDate.atStartOfDay(context.timeZone());
    long midnightSecondsSinceEpoch = midnight.toEpochSecond();

    // Handle arrival time: prefer absolute time, fall back to delay
    var arrivalTime = update.arrivalTime();
    var arrivalDelay = update.arrivalDelay();
    if (arrivalTime.isPresent()) {
      int time = (int) (arrivalTime.getAsLong() - midnightSecondsSinceEpoch);
      // Get scheduled time if available for proper TimeUpdate
      var scheduledArrival = update.scheduledArrivalTimeWithRealTimeFallback().isPresent()
        ? (int) (update.scheduledArrivalTimeWithRealTimeFallback().getAsLong() -
            midnightSecondsSinceEpoch)
        : null;
      builder.withArrivalUpdate(TimeUpdate.ofAbsolute(time, scheduledArrival));
    } else if (arrivalDelay.isPresent()) {
      builder.withArrivalUpdate(TimeUpdate.ofDelay(arrivalDelay.getAsInt()));
    }

    // Handle departure time: prefer absolute time, fall back to delay
    var departureTime = update.departureTime();
    var departureDelay = update.departureDelay();
    if (departureTime.isPresent()) {
      int time = (int) (departureTime.getAsLong() - midnightSecondsSinceEpoch);
      // Get scheduled time if available for proper TimeUpdate
      var scheduledDeparture = update.scheduledDepartureTimeWithRealTimeFallback().isPresent()
        ? (int) (update.scheduledDepartureTimeWithRealTimeFallback().getAsLong() -
            midnightSecondsSinceEpoch)
        : null;
      builder.withDepartureUpdate(TimeUpdate.ofAbsolute(time, scheduledDeparture));
    } else if (departureDelay.isPresent()) {
      builder.withDepartureUpdate(TimeUpdate.ofDelay(departureDelay.getAsInt()));
    }
  }

  private void parseNewTripStopTimeUpdate(
    StopTimeUpdate update,
    ParsedStopTimeUpdate.Builder builder,
    LocalDate serviceDate,
    TripUpdateParserContext context
  ) {
    // Calculate midnight seconds for absolute time conversion
    var midnight = serviceDate.atStartOfDay(context.timeZone());
    long midnightSecondsSinceEpoch = midnight.toEpochSecond();

    var arrivalTimeOpt = update.arrivalTime();
    var departureTimeOpt = update.departureTime();
    var scheduledArrivalOpt = update.scheduledArrivalTimeWithRealTimeFallback();
    var scheduledDepartureOpt = update.scheduledDepartureTimeWithRealTimeFallback();

    if (arrivalTimeOpt.isPresent()) {
      int arrivalTime = (int) (arrivalTimeOpt.getAsLong() - midnightSecondsSinceEpoch);
      Integer scheduledArrival = scheduledArrivalOpt.isPresent()
        ? (int) (scheduledArrivalOpt.getAsLong() - midnightSecondsSinceEpoch)
        : null;
      builder.withArrivalUpdate(TimeUpdate.ofAbsolute(arrivalTime, scheduledArrival));
    }

    if (departureTimeOpt.isPresent()) {
      int departureTime = (int) (departureTimeOpt.getAsLong() - midnightSecondsSinceEpoch);
      Integer scheduledDeparture = scheduledDepartureOpt.isPresent()
        ? (int) (scheduledDepartureOpt.getAsLong() - midnightSecondsSinceEpoch)
        : null;
      builder.withDepartureUpdate(TimeUpdate.ofAbsolute(departureTime, scheduledDeparture));
    }
  }

  private TripCreationInfo buildTripCreationInfo(
    FeedScopedId tripId,
    TripDescriptor descriptor,
    TripUpdate tripUpdate
  ) {
    var builder = TripCreationInfo.builder(tripId);

    // Get route ID from descriptor
    var routeId = descriptor
      .routeId()
      .map(id -> new FeedScopedId(tripId.getFeedId(), id))
      .orElse(null);

    if (routeId != null) {
      builder.withRouteId(routeId);
    }

    tripUpdate.tripHeadsign().ifPresent(builder::withHeadsign);
    tripUpdate.tripShortName().ifPresent(builder::withShortName);

    tripUpdate
      .vehicle()
      .ifPresent(vehicle -> {
        if (vehicle.hasWheelchairAccessible()) {
          var accessibility = mapWheelchairAccessibility(
            vehicle.getWheelchairAccessible().getNumber()
          );
          builder.withWheelchairAccessibility(accessibility);
        }
      });

    // Extract route creation info from MFDZ extensions
    var addedRoute = AddedRoute.ofTripDescriptor(descriptor);
    if (routeId != null && (addedRoute.routeUrl() != null || addedRoute.routeLongName() != null)) {
      var agencyId = addedRoute.agencyId() != null
        ? new FeedScopedId(tripId.getFeedId(), addedRoute.agencyId())
        : null;
      var mode = org.opentripplanner.gtfs.mapping.TransitModeMapper.mapMode(addedRoute.routeType());
      var routeCreationInfo = new RouteCreationInfo(
        routeId,
        addedRoute.routeLongName(),
        mode,
        null,
        null,
        addedRoute.routeUrl(),
        agencyId
      );
      builder.withRouteCreationInfo(routeCreationInfo);
    }

    return builder.build();
  }

  private Accessibility mapWheelchairAccessibility(int value) {
    return switch (value) {
      case 2 -> Accessibility.POSSIBLE;
      case 3 -> Accessibility.NOT_POSSIBLE;
      default -> Accessibility.NO_INFORMATION;
    };
  }
}
