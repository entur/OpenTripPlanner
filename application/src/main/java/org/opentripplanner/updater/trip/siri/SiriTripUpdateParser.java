package org.opentripplanner.updater.trip.siri;

import static java.lang.Boolean.TRUE;
import static org.opentripplanner.updater.alert.siri.mapping.SiriTransportModeMapper.mapTransitMainMode;
import static org.opentripplanner.updater.spi.UpdateError.UpdateErrorType.EMPTY_STOP_POINT_REF;
import static org.opentripplanner.updater.spi.UpdateError.UpdateErrorType.NOT_MONITORED;
import static org.opentripplanner.updater.spi.UpdateError.UpdateErrorType.NO_START_DATE;
import static org.opentripplanner.updater.spi.UpdateError.UpdateErrorType.UNKNOWN;
import static org.opentripplanner.updater.trip.siri.support.NaturalLanguageStringHelper.getFirstStringFromList;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model.basic.Accessibility;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.TripUpdateParser;
import org.opentripplanner.updater.trip.model.DeferredTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedAddNewTrip;
import org.opentripplanner.updater.trip.model.ParsedCancelTrip;
import org.opentripplanner.updater.trip.model.ParsedModifyTrip;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.ParsedUpdateExisting;
import org.opentripplanner.updater.trip.model.StopReference;
import org.opentripplanner.updater.trip.model.TimeUpdate;
import org.opentripplanner.updater.trip.model.TripCreationInfo;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripUpdateOptions;
import org.opentripplanner.updater.trip.model.TripUpdateType;
import org.opentripplanner.updater.trip.siri.mapping.OccupancyMapper;
import org.opentripplanner.updater.trip.siri.mapping.PickDropMapper;
import org.opentripplanner.utils.lang.StringUtils;
import org.opentripplanner.utils.time.ServiceDateUtils;
import org.rutebanken.netex.model.BusSubmodeEnumeration;
import org.rutebanken.netex.model.RailSubmodeEnumeration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.EstimatedVehicleJourney;

/**
 * Parser for SIRI EstimatedVehicleJourney messages into the common ParsedTripUpdate model.
 * This parser only parses SIRI messages - entity resolution and validation is done by the applier.
 */
public class SiriTripUpdateParser implements TripUpdateParser<EstimatedVehicleJourney> {

  private static final Logger LOG = LoggerFactory.getLogger(SiriTripUpdateParser.class);

  private final String feedId;
  private final ZoneId timeZone;

  public SiriTripUpdateParser(String feedId, ZoneId timeZone) {
    this.feedId = Objects.requireNonNull(feedId);
    this.timeZone = Objects.requireNonNull(timeZone);
  }

  @Override
  public Result<ParsedTripUpdate, UpdateError> parse(EstimatedVehicleJourney journey) {
    List<CallWrapper> calls = CallWrapper.of(journey);

    // Validate stop point refs exist
    for (var call : calls) {
      if (StringUtils.hasNoValueOrNullAsString(call.getStopPointRef())) {
        return UpdateError.result(null, EMPTY_STOP_POINT_REF, journey.getDataSource());
      }
    }

    // Check if journey is monitored (unless cancelled)
    if (!TRUE.equals(journey.isMonitored()) && !TRUE.equals(journey.isCancellation())) {
      return UpdateError.result(null, NOT_MONITORED, journey.getDataSource());
    }

    // Determine update type, service date, and trip reference
    var updateType = determineUpdateType(journey, calls);

    // For ADD_NEW_TRIP, EstimatedVehicleJourneyCode is required (SIRI Profile requirement)
    if (
      updateType == TripUpdateType.ADD_NEW_TRIP && journey.getEstimatedVehicleJourneyCode() == null
    ) {
      LOG.debug("ADD_NEW_TRIP requires EstimatedVehicleJourneyCode");
      return UpdateError.result(null, UNKNOWN, journey.getDataSource());
    }

    ServiceDateParser.ParsedServiceDate psd = new ServiceDateParser(journey, feedId).parse();

    if (psd.isEmpty()) {
      return UpdateError.result(null, NO_START_DATE, journey.getDataSource());
    }

    var tripReference = buildTripReference(journey, updateType, psd);

    // Handle cancellation (no stop times needed)
    if (TRUE.equals(journey.isCancellation())) {
      return Result.success(
        new ParsedCancelTrip(
          tripReference,
          psd.serviceDate(),
          psd.aimedDepartureTime(),
          journey.getDataSource()
        )
      );
    }

    // Parse stop time updates
    var stopTimeUpdates = parseStopTimeUpdates(
      calls,
      psd.serviceDate(),
      journey.getOccupancy(),
      journey.isPredictionInaccurate()
    );

    return switch (updateType) {
      case UPDATE_EXISTING -> {
        var builder = ParsedUpdateExisting.builder(tripReference, psd.serviceDate())
          .withOptions(TripUpdateOptions.siriDefaults())
          .withDataSource(journey.getDataSource())
          .withStopTimeUpdates(stopTimeUpdates);
        if (psd.aimedDepartureTime() != null) {
          builder.withAimedDepartureTime(psd.aimedDepartureTime());
        }
        yield Result.success(builder.build());
      }
      case MODIFY_TRIP -> {
        var builder = ParsedModifyTrip.builder(tripReference, psd.serviceDate())
          .withOptions(TripUpdateOptions.siriDefaults())
          .withDataSource(journey.getDataSource())
          .withStopTimeUpdates(stopTimeUpdates);
        if (psd.aimedDepartureTime() != null) {
          builder.withAimedDepartureTime(psd.aimedDepartureTime());
        }
        yield Result.success(builder.build());
      }
      case ADD_NEW_TRIP -> {
        var creationInfo = buildTripCreationInfo(journey);
        if (creationInfo == null) {
          yield UpdateError.result(null, UNKNOWN, journey.getDataSource());
        }
        var builder = ParsedAddNewTrip.builder(tripReference, psd.serviceDate(), creationInfo)
          .withOptions(TripUpdateOptions.siriDefaults())
          .withDataSource(journey.getDataSource())
          .withStopTimeUpdates(stopTimeUpdates);
        if (psd.aimedDepartureTime() != null) {
          builder.withAimedDepartureTime(psd.aimedDepartureTime());
        }
        yield Result.success(builder.build());
      }
      case CANCEL_TRIP, DELETE_TRIP -> throw new IllegalStateException(
        "Unexpected update type: " + updateType
      );
    };
  }

  private FeedScopedId createId(String entityId) {
    return new FeedScopedId(feedId, entityId);
  }

  private TripUpdateType determineUpdateType(
    EstimatedVehicleJourney journey,
    List<CallWrapper> calls
  ) {
    if (calls.stream().anyMatch(CallWrapper::isExtraCall)) {
      return TripUpdateType.MODIFY_TRIP;
    }
    if (TRUE.equals(journey.isCancellation())) {
      return TripUpdateType.CANCEL_TRIP;
    }
    if (TRUE.equals(journey.isExtraJourney())) {
      return TripUpdateType.ADD_NEW_TRIP;
    }
    return TripUpdateType.UPDATE_EXISTING;
  }

  private TripReference buildTripReference(
    EstimatedVehicleJourney journey,
    TripUpdateType updateType,
    ServiceDateParser.ParsedServiceDate psd
  ) {
    var builder = TripReference.builder().withStartDate(psd.serviceDate());

    var tripId = resolveTripId(journey);
    if (tripId != null) {
      builder.withTripId(tripId);
    }

    // For ADD_NEW_TRIP, the tripOnServiceDateId is the ID of the NEW trip being created,
    // not an existing TripOnServiceDate to resolve. Don't set it in the reference.
    // For other update types, set it so we can look up the existing TripOnServiceDate.
    if (updateType != TripUpdateType.ADD_NEW_TRIP) {
      var tripOnServiceDateId = psd.tripOnServiceDateId();
      if (tripOnServiceDateId != null) {
        builder.withTripOnServiceDateId(tripOnServiceDateId);
      }
    }

    if (journey.getLineRef() != null) {
      builder.withRouteId(createId(journey.getLineRef().getValue()));
    }

    // Get aimed start time from first call
    ZonedDateTime aimedStartTime = null;
    for (var call : CallWrapper.of(journey)) {
      aimedStartTime = call.getAimedDepartureTime();
      if (aimedStartTime != null) {
        break;
      }
    }
    if (aimedStartTime != null && psd.serviceDate() != null) {
      ZonedDateTime startOfService = ServiceDateUtils.asStartOfService(psd.serviceDate(), timeZone);
      int seconds = ServiceDateUtils.secondsSinceStartOfService(startOfService, aimedStartTime);
      builder.withStartTime(org.opentripplanner.utils.time.TimeUtils.timeToStrCompact(seconds));
    }

    if (journey.getDirectionRef() != null) {
      try {
        int directionInt = Integer.parseInt(journey.getDirectionRef().getValue());
        builder.withDirection(mapDirection(directionInt));
      } catch (NumberFormatException ignored) {}
    }

    // For SIRI, fuzzy matching is always allowed as a fallback for UPDATE_EXISTING
    // The old SiriFuzzyTripMatcher was used whenever exact match failed
    builder.withFuzzyMatchingHint(
      updateType == TripUpdateType.ADD_NEW_TRIP
        ? TripReference.FuzzyMatchingHint.EXACT_MATCH_REQUIRED
        : TripReference.FuzzyMatchingHint.FUZZY_MATCH_ALLOWED
    );

    return builder.build();
  }

  /**
   * Resolve the Trip ID (service journey id) from the EstimatedVehicleJourney.
   * This only returns an ID when it's actually a Trip ID, not a TripOnServiceDate ID.
   */
  @Nullable
  private FeedScopedId resolveTripId(EstimatedVehicleJourney journey) {
    // FramedVehicleJourneyRef.getDatedVehicleJourneyRef contains the actual Trip ID
    if (journey.getFramedVehicleJourneyRef() != null) {
      var ref = journey.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef();
      if (ref != null) {
        return createId(ref);
      }
    }

    // EstimatedVehicleJourneyCode contains an encoded Trip ID
    if (journey.getEstimatedVehicleJourneyCode() != null) {
      var adapter = new EstimatedVehicleJourneyCodeAdapter(
        journey.getEstimatedVehicleJourneyCode()
      );
      return createId(adapter.getServiceJourneyId());
    }

    return null;
  }

  private List<ParsedStopTimeUpdate> parseStopTimeUpdates(
    List<CallWrapper> calls,
    LocalDate serviceDate,
    @Nullable uk.org.siri.siri21.OccupancyEnumeration journeyOccupancy,
    @Nullable Boolean journeyPredictionInaccurate
  ) {
    var result = new ArrayList<ParsedStopTimeUpdate>();
    int totalStops = calls.size();

    int stopIndex = 0;
    for (var call : calls) {
      if (StringUtils.hasNoValueOrNullAsString(call.getStopPointRef())) {
        continue;
      }

      var stopId = createId(call.getStopPointRef());
      var stopReference = StopReference.ofScheduledStopPointOrStopId(stopId);
      var builder = ParsedStopTimeUpdate.builder(stopReference);

      builder.withStatus(determineStopStatus(call));
      parseStopTimes(call, builder, serviceDate, stopIndex, totalStops);

      if (call.isExtraCall()) {
        builder.withIsExtraCall(true);
      }
      if (TRUE.equals(call.isPredictionInaccurate()) || TRUE.equals(journeyPredictionInaccurate)) {
        builder.withPredictionInaccurate(true);
      }
      if (call.getActualArrivalTime() != null || call.getActualDepartureTime() != null) {
        builder.withRecorded(true);
      }

      parsePickDropTypes(call, builder);

      var displays = call.getDestinationDisplays();
      if (displays != null && !displays.isEmpty()) {
        String headsign = getFirstStringFromList(displays);
        if (!headsign.isEmpty()) {
          builder.withStopHeadsign(new NonLocalizedString(headsign));
        }
      }

      var effectiveOccupancy = call.getOccupancy() != null ? call.getOccupancy() : journeyOccupancy;
      if (effectiveOccupancy != null) {
        builder.withOccupancy(OccupancyMapper.mapOccupancyStatus(effectiveOccupancy));
      }

      result.add(builder.build());
      stopIndex++;
    }

    return result;
  }

  private ParsedStopTimeUpdate.StopUpdateStatus determineStopStatus(CallWrapper call) {
    if (TRUE.equals(call.isCancellation())) {
      return ParsedStopTimeUpdate.StopUpdateStatus.CANCELLED;
    }
    if (call.isExtraCall()) {
      return ParsedStopTimeUpdate.StopUpdateStatus.ADDED;
    }
    return ParsedStopTimeUpdate.StopUpdateStatus.SCHEDULED;
  }

  private void parseStopTimes(
    CallWrapper call,
    ParsedStopTimeUpdate.Builder builder,
    @Nullable LocalDate serviceDate,
    int stopIndex,
    int totalStops
  ) {
    // Resolve times using the same fallback logic as TimetableHelper
    var resolvedTimes = SiriTimeResolver.resolveTimes(call, stopIndex, totalStops);
    var resolvedAimedTimes = SiriTimeResolver.resolveAimedTimes(call, stopIndex, totalStops);

    if (serviceDate != null) {
      // Service date is known - create resolved TimeUpdate
      ZonedDateTime startOfService = ServiceDateUtils.asStartOfService(serviceDate, timeZone);

      // Create arrival TimeUpdate
      if (resolvedTimes.arrivalTime() != null) {
        int seconds = ServiceDateUtils.secondsSinceStartOfService(
          startOfService,
          resolvedTimes.arrivalTime()
        );
        Integer scheduled = resolvedAimedTimes.arrivalTime() != null
          ? ServiceDateUtils.secondsSinceStartOfService(
              startOfService,
              resolvedAimedTimes.arrivalTime()
            )
          : null;
        builder.withArrivalUpdate(TimeUpdate.ofAbsolute(seconds, scheduled));
      }

      // Create departure TimeUpdate
      if (resolvedTimes.departureTime() != null) {
        int seconds = ServiceDateUtils.secondsSinceStartOfService(
          startOfService,
          resolvedTimes.departureTime()
        );
        Integer scheduled = resolvedAimedTimes.departureTime() != null
          ? ServiceDateUtils.secondsSinceStartOfService(
              startOfService,
              resolvedAimedTimes.departureTime()
            )
          : null;
        builder.withDepartureUpdate(TimeUpdate.ofAbsolute(seconds, scheduled));
      }
    } else {
      // Service date is unknown - create DeferredTimeUpdate for resolution in applier stage
      if (resolvedTimes.arrivalTime() != null) {
        builder.withArrivalUpdate(
          DeferredTimeUpdate.of(resolvedTimes.arrivalTime(), resolvedAimedTimes.arrivalTime())
        );
      }

      if (resolvedTimes.departureTime() != null) {
        builder.withDepartureUpdate(
          DeferredTimeUpdate.of(resolvedTimes.departureTime(), resolvedAimedTimes.departureTime())
        );
      }
    }
  }

  private void parsePickDropTypes(CallWrapper call, ParsedStopTimeUpdate.Builder builder) {
    PickDropMapper.mapDropOffType(call, PickDrop.SCHEDULED).ifPresent(builder::withDropoff);
    PickDropMapper.mapPickUpType(call, PickDrop.SCHEDULED).ifPresent(builder::withPickup);
  }

  @Nullable
  private TripCreationInfo buildTripCreationInfo(EstimatedVehicleJourney journey) {
    String code = journey.getEstimatedVehicleJourneyCode();
    if (code == null) {
      return null;
    }

    var adapter = new EstimatedVehicleJourneyCodeAdapter(code);
    var tripId = createId(adapter.getServiceJourneyId());
    var builder = TripCreationInfo.builder(tripId);

    if (journey.getLineRef() != null) {
      builder.withRouteId(createId(journey.getLineRef().getValue()));

      // Set replacedRouteId from ExternalLineRef (only if it differs from LineRef)
      if (journey.getExternalLineRef() != null) {
        var externalLineRef = journey.getExternalLineRef().getValue();
        if (!externalLineRef.equals(journey.getLineRef().getValue())) {
          builder.withReplacedRouteId(createId(externalLineRef));
        }
      }
    }

    String datedServiceJourneyId = adapter.getDatedServiceJourneyId();
    if (datedServiceJourneyId != null) {
      builder.withServiceId(createId(datedServiceJourneyId));
    }

    if (journey.getDestinationNames() != null && !journey.getDestinationNames().isEmpty()) {
      String destinationName = getFirstStringFromList(journey.getDestinationNames());
      if (!destinationName.isEmpty()) {
        builder.withHeadsign(new NonLocalizedString(destinationName));
      }
    }

    if (journey.getPublishedLineNames() != null && !journey.getPublishedLineNames().isEmpty()) {
      String name = getFirstStringFromList(journey.getPublishedLineNames());
      if (!name.isEmpty()) {
        builder.withShortName(name);
      }
    }

    var modes = journey.getVehicleModes();
    if (modes != null && !modes.isEmpty()) {
      var mode = modes.isEmpty() ? null : modes.get(0);
      if (mode != null) {
        builder.withMode(mapTransitMainMode(List.of(mode)));
        String submode = mapSubMode(mode);
        if (submode != null) {
          builder.withSubmode(submode);
        }
      }
    }

    if (journey.getOperatorRef() != null) {
      builder.withOperatorId(createId(journey.getOperatorRef().getValue()));
    }

    // Extract replacement trip references
    // VehicleJourneyRef indicates which trip this extra journey replaces
    var vehicleJourneyRef = journey.getVehicleJourneyRef();
    if (vehicleJourneyRef != null && vehicleJourneyRef.getValue() != null) {
      builder.addReplacedTrip(createId(vehicleJourneyRef.getValue()));
    }

    // AdditionalVehicleJourneyReves contains additional trips being replaced
    for (var ref : journey.getAdditionalVehicleJourneyReves()) {
      if (ref != null && ref.getDatedVehicleJourneyRef() != null) {
        builder.addReplacedTrip(createId(ref.getDatedVehicleJourneyRef()));
      }
    }

    builder.withWheelchairAccessibility(Accessibility.NO_INFORMATION);
    return builder.build();
  }

  @Nullable
  private String mapSubMode(uk.org.siri.siri21.VehicleModesEnumeration mode) {
    if (mode == uk.org.siri.siri21.VehicleModesEnumeration.BUS) {
      return BusSubmodeEnumeration.LOCAL_BUS.value();
    } else if (mode == uk.org.siri.siri21.VehicleModesEnumeration.RAIL) {
      return RailSubmodeEnumeration.LOCAL.value();
    }
    return null;
  }

  private org.opentripplanner.transit.model.timetable.Direction mapDirection(int directionInt) {
    return switch (directionInt) {
      case 0 -> org.opentripplanner.transit.model.timetable.Direction.OUTBOUND;
      case 1 -> org.opentripplanner.transit.model.timetable.Direction.INBOUND;
      default -> org.opentripplanner.transit.model.timetable.Direction.UNKNOWN;
    };
  }
}
