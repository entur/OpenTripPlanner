package org.opentripplanner.updater.trip.siri;

import static java.lang.Boolean.TRUE;
import static org.opentripplanner.updater.spi.UpdateErrorType.NO_START_DATE;
import static org.opentripplanner.updater.spi.UpdateErrorType.UNKNOWN;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.accessibility.Accessibility;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.timetable.OccupancyStatus;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.TripUpdateParser;
import org.opentripplanner.updater.trip.model.DeferredTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.StopReference;
import org.opentripplanner.updater.trip.model.TimeUpdate;
import org.opentripplanner.updater.trip.model.TripAddition;
import org.opentripplanner.updater.trip.model.TripCancellation;
import org.opentripplanner.updater.trip.model.TripCreationInfo;
import org.opentripplanner.updater.trip.model.TripModification;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripRevision;
import org.opentripplanner.updater.trip.model.TripUpdateType;
import org.opentripplanner.updater.trip.policy.FormatPolicy;
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
  public ParsedTripUpdate parse(EstimatedVehicleJourney rawJourney) {
    // The wrapper parses the calls once and rejects unmonitored (non-cancelled) journeys.
    var journey = EstimatedVehicleJourneyWrapper.of(rawJourney);
    var calls = journey.calls();

    // Determine update type, service date, and trip reference
    var updateType = determineUpdateType(journey);

    // For ADD_NEW_TRIP, EstimatedVehicleJourneyCode is required (SIRI Profile requirement)
    if (updateType == TripUpdateType.ADD_NEW_TRIP && journey.code() == null) {
      LOG.debug("ADD_NEW_TRIP requires EstimatedVehicleJourneyCode");
      throw UpdateException.noTripId(UNKNOWN);
    }

    ServiceDateParser.ParsedServiceDate psd = new ServiceDateParser(journey, feedId).parse();

    if (psd.isEmpty()) {
      throw UpdateException.noTripId(NO_START_DATE);
    }

    var tripReference = buildTripReference(journey, updateType, psd);

    // Handle plain cancellation (no stop times needed).
    // Exceptions where the cancellation flag is instead carried on the parsed update:
    // - MODIFY_TRIP (extra call): carried into TripModification so TripModifier can mark the
    //   trip cancelled on the extra-call pattern, preserving the extra stop information.
    // - ADD_NEW_TRIP (extra journey): carried into TripAddition so the extra journey is added
    //   in cancelled state rather than rejected as a cancellation of a non-existent trip.
    if (
      journey.isCancellation() &&
      updateType != TripUpdateType.MODIFY_TRIP &&
      updateType != TripUpdateType.ADD_NEW_TRIP
    ) {
      return new TripCancellation(
        tripReference,
        psd.serviceDate(),
        psd.aimedDepartureTime(),
        journey.dataSource()
      );
    }

    // Parse stop time updates
    var stopTimeUpdates = parseStopTimeUpdates(
      calls,
      psd.serviceDate(),
      journey.occupancy(),
      journey.isPredictionInaccurate()
    );

    return switch (updateType) {
      case UPDATE_EXISTING -> {
        var builder = TripRevision.builder(tripReference, psd.serviceDate())
          .withFormatPolicy(FormatPolicy.siri())
          .withDataSource(journey.dataSource())
          .withStopTimeUpdates(stopTimeUpdates);
        if (psd.aimedDepartureTime() != null) {
          builder.withAimedDepartureTime(psd.aimedDepartureTime());
        }
        yield builder.build();
      }
      case MODIFY_TRIP -> {
        var builder = TripModification.builder(tripReference, psd.serviceDate())
          .withFormatPolicy(FormatPolicy.siri())
          .withDataSource(journey.dataSource())
          .withStopTimeUpdates(stopTimeUpdates)
          .withCancellation(journey.isCancellation())
          .withExtraJourney(journey.isExtraJourney());
        if (psd.aimedDepartureTime() != null) {
          builder.withAimedDepartureTime(psd.aimedDepartureTime());
        }
        yield builder.build();
      }
      case ADD_NEW_TRIP -> {
        var creationInfo = buildTripCreationInfo(journey);
        if (creationInfo == null) {
          throw UpdateException.noTripId(UNKNOWN);
        }
        var builder = TripAddition.builder(tripReference, psd.serviceDate(), creationInfo)
          .withFormatPolicy(FormatPolicy.siri())
          .withDataSource(journey.dataSource())
          .withStopTimeUpdates(stopTimeUpdates)
          .withCancellation(journey.isCancellation());
        if (psd.aimedDepartureTime() != null) {
          builder.withAimedDepartureTime(psd.aimedDepartureTime());
        }
        yield builder.build();
      }
      case CANCEL_TRIP, DELETE_TRIP, DUPLICATE_TRIP -> throw new IllegalStateException(
        "Unexpected update type: " + updateType
      );
    };
  }

  private FeedScopedId createId(String entityId) {
    return new FeedScopedId(feedId, entityId);
  }

  private TripUpdateType determineUpdateType(EstimatedVehicleJourneyWrapper journey) {
    if (journey.hasExtraCall()) {
      return TripUpdateType.MODIFY_TRIP;
    }
    // An extra journey is always an addition, even when cancelled: a cancelled extra journey is
    // added in cancelled state rather than treated as a cancellation of a non-existent trip.
    if (journey.isExtraJourney()) {
      return TripUpdateType.ADD_NEW_TRIP;
    }
    if (journey.isCancellation()) {
      return TripUpdateType.CANCEL_TRIP;
    }
    return TripUpdateType.UPDATE_EXISTING;
  }

  private TripReference buildTripReference(
    EstimatedVehicleJourneyWrapper journey,
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

    if (journey.lineRef() != null) {
      builder.withRouteId(createId(journey.lineRef()));
    }

    // Get aimed start time from first call
    ZonedDateTime aimedStartTime = null;
    for (var call : journey.calls()) {
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

    // For RAIL trips, extract VehicleRef as internal planning code for fuzzy matching.
    // BNR producer sends numeric DatedVehicleJourneyRef values that don't match trip IDs,
    // but the VehicleRef corresponds to Trip.netexInternalPlanningCode.
    if (journey.internalPlanningCode() != null && journey.isRail()) {
      builder.withInternalPlanningCode(journey.internalPlanningCode());
    }

    if (journey.directionRef() != null) {
      try {
        int directionInt = Integer.parseInt(journey.directionRef());
        builder.withDirection(
          org.opentripplanner.transit.model.timetable.Direction.ofGtfsCode(directionInt)
        );
      } catch (NumberFormatException ignored) {}
    }

    return builder.build();
  }

  /**
   * Resolve the Trip ID (service journey id) from the EstimatedVehicleJourney.
   * This only returns an ID when it's actually a Trip ID, not a TripOnServiceDate ID.
   */
  @Nullable
  private FeedScopedId resolveTripId(EstimatedVehicleJourneyWrapper journey) {
    // The framed vehicle journey id is the actual Trip ID
    var vehicleJourneyIdAndServiceDate = journey.vehicleJourneyIdAndServiceDate();
    if (
      vehicleJourneyIdAndServiceDate != null &&
      vehicleJourneyIdAndServiceDate.vehicleJourneyId() != null
    ) {
      return createId(vehicleJourneyIdAndServiceDate.vehicleJourneyId());
    }

    // EstimatedVehicleJourneyCode contains an encoded Trip ID
    var code = journey.code();
    if (code != null) {
      return createId(code.asServiceJourneyId());
    }

    return null;
  }

  private List<ParsedStopTimeUpdate> parseStopTimeUpdates(
    List<CallWrapper> calls,
    LocalDate serviceDate,
    @Nullable OccupancyStatus journeyOccupancy,
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

      var resolvedTimes = SiriTimeResolver.resolveTimes(call, stopIndex, totalStops);
      builder.withStatus(determineStopStatus(call, resolvedTimes));
      parseStopTimes(call, builder, resolvedTimes, serviceDate, stopIndex, totalStops);

      if (call.isExtraCall()) {
        builder.withIsExtraCall(true);
      }
      if (TRUE.equals(call.isPredictionInaccurate()) || TRUE.equals(journeyPredictionInaccurate)) {
        builder.withPredictionInaccurate(true);
      }
      if (call.hasArrived()) {
        builder.withHasArrived(true);
      }
      if (call.hasDeparted()) {
        builder.withHasDeparted(true);
      }

      parsePickDropTypes(call, builder);

      String headsign = call.destinationDisplay();
      if (!headsign.isEmpty()) {
        builder.withStopHeadsign(new NonLocalizedString(headsign));
      }

      var effectiveOccupancy = call.getOccupancy() != null ? call.getOccupancy() : journeyOccupancy;
      if (effectiveOccupancy != null) {
        builder.withOccupancy(effectiveOccupancy);
      }

      result.add(builder.build());
      stopIndex++;
    }

    return result;
  }

  private ParsedStopTimeUpdate.StopUpdateStatus determineStopStatus(
    CallWrapper call,
    SiriTimeResolver.ResolvedTimes resolvedTimes
  ) {
    if (TRUE.equals(call.isCancellation())) {
      return ParsedStopTimeUpdate.StopUpdateStatus.CANCELLED;
    }
    if (call.isExtraCall()) {
      return ParsedStopTimeUpdate.StopUpdateStatus.ADDED;
    }
    // A call carrying no real-time arrival or departure time is reported as NO_DATA: the scheduled
    // times are kept, the stop is flagged, and the trip is not, by this stop alone, modified.
    if (resolvedTimes.arrivalTime() == null && resolvedTimes.departureTime() == null) {
      return ParsedStopTimeUpdate.StopUpdateStatus.NO_DATA;
    }
    return ParsedStopTimeUpdate.StopUpdateStatus.SCHEDULED;
  }

  private void parseStopTimes(
    CallWrapper call,
    ParsedStopTimeUpdate.Builder builder,
    SiriTimeResolver.ResolvedTimes resolvedTimes,
    @Nullable LocalDate serviceDate,
    int stopIndex,
    int totalStops
  ) {
    // Resolve aimed times using the same fallback logic as TimetableHelper
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

  // Capture the pick/drop intent of each call end without the scheduled pattern's values, which
  // the parser doesn't have. The wrapper's PickDropChange normalizes the SIRI boarding activity;
  // resolving it against a non-routable placeholder yields the pure routability intent
  // (SCHEDULED/NONE/CANCELLED). The apply side's PickDropPolicy then reconciles this intent against
  // the actual scheduled pickup/dropoff from the pattern.
  private void parsePickDropTypes(CallWrapper call, ParsedStopTimeUpdate.Builder builder) {
    call.dropOff().applyTo(PickDrop.NONE).ifPresent(builder::withDropoff);
    call.pickUp().applyTo(PickDrop.NONE).ifPresent(builder::withPickup);
  }

  @Nullable
  private TripCreationInfo buildTripCreationInfo(EstimatedVehicleJourneyWrapper journey) {
    var code = journey.code();
    if (code == null) {
      return null;
    }

    var tripId = createId(code.asServiceJourneyId());
    var builder = TripCreationInfo.builder(tripId);

    if (journey.lineRef() != null) {
      builder.withRouteId(createId(journey.lineRef()));

      // Set replacedRouteId from ExternalLineRef (only if it differs from LineRef)
      if (
        journey.externalLineRef() != null && !journey.externalLineRef().equals(journey.lineRef())
      ) {
        builder.withReplacedRouteId(createId(journey.externalLineRef()));
      }
    }

    String datedServiceJourneyId = code.asDatedServiceJourneyId();
    if (datedServiceJourneyId != null) {
      builder.withServiceId(createId(datedServiceJourneyId));
    }

    String destinationName = journey.destinationName();
    if (!destinationName.isEmpty()) {
      builder.withHeadsign(new NonLocalizedString(destinationName));
    }

    String shortName = journey.publishedLineName();
    if (!shortName.isEmpty()) {
      builder.withShortName(shortName);
    }

    var mode = journey.transitMode();
    builder.withMode(mode);
    String submode = mapSubMode(mode);
    if (submode != null) {
      builder.withSubmode(submode);
    }

    if (journey.operatorRef() != null) {
      builder.withOperatorId(createId(journey.operatorRef()));
    }

    // Extract replacement trip references
    // The replaced dated vehicle journey ref indicates which trip this extra journey replaces
    var replacedDatedVehicleJourneyRef = journey.replacedDatedVehicleJourneyRef();
    if (replacedDatedVehicleJourneyRef != null) {
      builder.addReplacedTrip(createId(replacedDatedVehicleJourneyRef));
    }

    // Additional refs contain further trips being replaced
    for (var ref : journey.additionalReplacedDatedVehicleJourneyRefs()) {
      if (ref != null && ref.vehicleJourneyId() != null) {
        builder.addReplacedTrip(createId(ref.vehicleJourneyId()));
      }
    }

    builder.withWheelchairAccessibility(Accessibility.NO_INFORMATION);
    return builder.build();
  }

  @Nullable
  private String mapSubMode(TransitMode mode) {
    if (mode == TransitMode.BUS) {
      return BusSubmodeEnumeration.LOCAL_BUS.value();
    } else if (mode == TransitMode.RAIL) {
      return RailSubmodeEnumeration.LOCAL.value();
    }
    return null;
  }
}
