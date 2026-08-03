package org.opentripplanner.ext.updater.trip.unified.siri;

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
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.TripUpdateParser;
import org.opentripplanner.ext.updater.trip.unified.TripUpdateType;
import org.opentripplanner.ext.updater.trip.unified.model.command.AddTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.CancelTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.DeferredTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.ModifyTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.ParsedStopTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.ReviseTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.StopReference;
import org.opentripplanner.ext.updater.trip.unified.model.command.TimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripCreationInfo;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripReference;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripUpdateCommand;
import org.opentripplanner.ext.updater.trip.unified.model.command.VehicleDescription;
import org.opentripplanner.ext.updater.trip.unified.policy.FormatPolicy;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.timetable.OccupancyStatus;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.siri.CallWrapper;
import org.opentripplanner.updater.trip.siri.EstimatedVehicleJourneyWrapper;
import org.opentripplanner.updater.trip.siri.VehicleJourneyIdAndServiceDate;
import org.opentripplanner.utils.lang.StringUtils;
import org.opentripplanner.utils.time.ServiceDateUtils;
import org.rutebanken.netex.model.BusSubmodeEnumeration;
import org.rutebanken.netex.model.RailSubmodeEnumeration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.EstimatedVehicleJourney;

/**
 * Parser for SIRI EstimatedVehicleJourney messages into the common TripUpdateCommand model.
 * This parser only parses SIRI messages - entity resolution and validation happen in the change
 * factories.
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
  public TripUpdateCommand parse(EstimatedVehicleJourney rawJourney) {
    // The wrapper parses the calls once and rejects unmonitored (non-cancelled) journeys.
    var journey = EstimatedVehicleJourneyWrapper.of(rawJourney);
    var calls = journey.calls();

    // Determine update type, service date, and trip reference
    var updateType = determineUpdateType(journey);

    // For ADD_NEW_TRIP, EstimatedVehicleJourneyCode is required (SIRI Profile requirement)
    if (updateType == TripUpdateType.ADD_NEW_TRIP && journey.code().isEmpty()) {
      LOG.debug("ADD_NEW_TRIP requires EstimatedVehicleJourneyCode");
      throw UpdateException.noTripId(UNKNOWN);
    }

    ServiceDateParser.ParsedServiceDate psd = new ServiceDateParser(journey, feedId).parse();

    if (psd.isEmpty()) {
      throw UpdateException.noTripId(NO_START_DATE);
    }

    var tripReference = buildTripReference(journey, updateType, psd);

    // SIRI-ET says nothing about the accessibility of the vehicle.
    var vehicle = VehicleDescription.of(journey.vehicleRef().orElse(null), null);

    // Handle plain cancellation (no stop times needed).
    // Exceptions where the cancellation flag is instead carried on the command:
    // - MODIFY_TRIP (extra call): carried into ModifyTrip so TripModifier can mark the
    //   trip cancelled on the extra-call pattern, preserving the extra stop information.
    // - ADD_NEW_TRIP (extra journey): carried into AddTrip so the extra journey is added
    //   in cancelled state rather than rejected as a cancellation of a non-existent trip.
    if (
      journey.isCancellation() &&
      updateType != TripUpdateType.MODIFY_TRIP &&
      updateType != TripUpdateType.ADD_NEW_TRIP
    ) {
      return new CancelTrip(
        tripReference,
        psd.serviceDate(),
        psd.aimedDepartureTime(),
        journey.dataSource().orElse(null),
        vehicle
      );
    }

    // Parse stop time updates
    var stopTimeUpdates = parseStopTimeUpdates(
      calls,
      psd.serviceDate(),
      journey.occupancy().orElse(null),
      journey.isPredictionInaccurate()
    );

    return switch (updateType) {
      case UPDATE_EXISTING -> {
        var builder = ReviseTrip.builder(tripReference, psd.serviceDate())
          .withFormatPolicy(FormatPolicy.siri())
          .withDataSource(journey.dataSource().orElse(null))
          .withVehicleDescription(vehicle)
          .withStopTimeUpdates(stopTimeUpdates);
        if (psd.aimedDepartureTime() != null) {
          builder.withAimedDepartureTime(psd.aimedDepartureTime());
        }
        yield builder.build();
      }
      case MODIFY_TRIP -> {
        var builder = ModifyTrip.builder(tripReference, psd.serviceDate())
          .withFormatPolicy(FormatPolicy.siri())
          .withDataSource(journey.dataSource().orElse(null))
          .withVehicleDescription(vehicle)
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
        var builder = AddTrip.builder(tripReference, psd.serviceDate(), creationInfo)
          .withFormatPolicy(FormatPolicy.siri())
          .withDataSource(journey.dataSource().orElse(null))
          .withVehicleDescription(vehicle)
          .withTripHeadsign(extraJourneyHeadsign(journey))
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

    journey.lineRef().ifPresent(lineRef -> builder.withRouteId(createId(lineRef)));

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
    if (journey.isRail()) {
      journey.vehicleRef().ifPresent(builder::withInternalPlanningCode);
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
    var vehicleJourneyId = journey
      .vehicleJourneyIdAndServiceDate()
      .map(VehicleJourneyIdAndServiceDate::vehicleJourneyId)
      .orElse(null);
    if (vehicleJourneyId != null) {
      return createId(vehicleJourneyId);
    }

    // EstimatedVehicleJourneyCode contains an encoded Trip ID
    return journey
      .code()
      .map(code -> createId(code.asServiceJourneyId()))
      .orElse(null);
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
      // Service date is unknown - create DeferredTimeUpdate for resolution in the change factory
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

  // Capture the pick/drop intent of each call end. The parser has no scheduled pattern, so it
  // records the raw routability intent the SIRI message reports (SCHEDULED/NONE/CANCELLED); the
  // change side's PickDropPolicy reconciles it against the actual scheduled pickup/dropoff.
  private void parsePickDropTypes(CallWrapper call, ParsedStopTimeUpdate.Builder builder) {
    call.dropOff().intent().ifPresent(builder::withDropoff);
    call.pickUp().intent().ifPresent(builder::withPickup);
  }

  @Nullable
  private TripCreationInfo buildTripCreationInfo(EstimatedVehicleJourneyWrapper journey) {
    var code = journey.code().orElse(null);
    if (code == null) {
      return null;
    }

    var tripId = createId(code.asServiceJourneyId());
    var builder = TripCreationInfo.builder(tripId);

    var lineRef = journey.lineRef().orElse(null);
    if (lineRef != null) {
      builder.withRouteId(createId(lineRef));

      // Set replacedRouteId from ExternalLineRef (only if it differs from LineRef)
      var externalLineRef = journey.externalLineRef().orElse(null);
      if (externalLineRef != null && !externalLineRef.equals(lineRef)) {
        builder.withReplacedRouteId(createId(externalLineRef));
      }
    }

    // The added trip on service date is identified by the DatedServiceJourney form of the code,
    // while the trip itself takes the ServiceJourney form.
    builder.withTripOnServiceDateId(createId(code.asDatedServiceJourneyId()));

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

    journey.operatorRef().ifPresent(operatorRef -> builder.withOperatorId(createId(operatorRef)));

    // Extract replacement trip references
    // The replaced dated vehicle journey ref indicates which trip this extra journey replaces
    journey
      .replacedDatedVehicleJourneyRef()
      .ifPresent(ref -> builder.addReplacedTrip(createId(ref)));

    // Additional refs contain further trips being replaced
    for (var ref : journey.additionalReplacedDatedVehicleJourneyRefs()) {
      if (ref != null && ref.vehicleJourneyId() != null) {
        builder.addReplacedTrip(createId(ref.vehicleJourneyId()));
      }
    }

    return builder.build();
  }

  /**
   * The destination name of an extra journey is the headsign it displays. SIRI only states it for
   * extra journeys - for an update to an existing trip the headsign of that trip stands, and the
   * per-call destination displays are carried by the stop time updates.
   */
  @Nullable
  private I18NString extraJourneyHeadsign(EstimatedVehicleJourneyWrapper journey) {
    String destinationName = journey.destinationName();
    return destinationName.isEmpty() ? null : new NonLocalizedString(destinationName);
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
