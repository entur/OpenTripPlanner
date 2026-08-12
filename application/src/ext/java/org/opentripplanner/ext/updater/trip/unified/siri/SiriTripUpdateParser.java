package org.opentripplanner.ext.updater.trip.unified.siri;

import static java.lang.Boolean.TRUE;
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
import org.opentripplanner.ext.updater.trip.unified.model.ServiceTime;
import org.opentripplanner.ext.updater.trip.unified.model.command.AddTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.CancelTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.DeferredTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.JourneyEndpoints;
import org.opentripplanner.ext.updater.trip.unified.model.command.ModifyTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.ParsedStopTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.ReplacedTripReference;
import org.opentripplanner.ext.updater.trip.unified.model.command.ReviseTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.StopReference;
import org.opentripplanner.ext.updater.trip.unified.model.command.TimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripCreationInfo;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripReference;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripUpdateCommand;
import org.opentripplanner.ext.updater.trip.unified.model.command.VehicleDescription;
import org.opentripplanner.ext.updater.trip.unified.policy.FormatPolicy;
import org.opentripplanner.transit.model.timetable.OccupancyStatus;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.siri.CallWrapper;
import org.opentripplanner.updater.trip.siri.EstimatedVehicleJourneyWrapper;
import org.opentripplanner.updater.trip.siri.VehicleJourneyIdAndServiceDate;
import org.opentripplanner.utils.lang.StringUtils;
import org.opentripplanner.utils.time.ServiceDateUtils;
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

    // A journey that says nothing about its service date is rejected with NO_START_DATE by the
    // command constructors below.
    ServiceDateParser.ParsedServiceDate psd = new ServiceDateParser(journey, feedId).parse();

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
        vehicle,
        parseJourneyEndpoints(calls)
      );
    }

    // Any command that is not a plain cancellation is built from the calls, so they must first
    // describe the journey the way the Nordic profile requires. Legacy instead tolerates the
    // holes - NO_DATA flags, scheduled-time fallbacks, created trips built from aimed times
    // alone - so rejecting here is an accepted divergence: profile-invalid input is rejected
    // loudly rather than compensated for.
    journey.validateCallTimes();

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

  /**
   * The origin and destination of the journey, read off its first and last call. A cancellation
   * describes its journey with these alone, and a producer whose journey ids name no trip is
   * identified by them; the times are the aimed ones only, and are taken as the message states them
   * - the Nordic profile rules the calls of a served journey must keep do not apply to a cancelled
   * one, which announces a journey that will not run.
   *
   * @return the endpoints, or null if the message lists no call naming a stop
   */
  @Nullable
  private JourneyEndpoints parseJourneyEndpoints(List<CallWrapper> calls) {
    var callsNamingAStop = calls
      .stream()
      .filter(call -> !StringUtils.hasNoValueOrNullAsString(call.getStopPointRef()))
      .toList();
    if (callsNamingAStop.isEmpty()) {
      return null;
    }
    var origin = callsNamingAStop.getFirst();
    var destination = callsNamingAStop.getLast();

    // A last call that reports only a departure is still the end of the journey; the departure it
    // aims for stands in for the arrival, the way legacy SIRI fuzzy matching reads it.
    var aimedArrival = destination.getAimedArrivalTime() != null
      ? destination.getAimedArrivalTime()
      : destination.getAimedDepartureTime();

    return new JourneyEndpoints(
      stopReference(origin),
      origin.getAimedDepartureTime(),
      stopReference(destination),
      aimedArrival
    );
  }

  private StopReference stopReference(CallWrapper call) {
    return StopReference.ofScheduledStopPointOrStopId(createId(call.getStopPointRef()));
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

    // No start time: it is the GTFS-RT way of naming a trip by its schedule, and nothing on the
    // SIRI side reads it - SIRI fuzzy matching identifies a trip by the internal planning code
    // and the arrival at the last call instead.

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

      var builder = ParsedStopTimeUpdate.builder(stopReference(call));

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
        builder.withArrivalUpdate(
          TimeUpdate.ofAbsolute(
            ServiceTime.of(startOfService, resolvedTimes.arrivalTime()),
            ServiceTime.ofNullable(startOfService, resolvedAimedTimes.arrivalTime())
          )
        );
      }

      // Create departure TimeUpdate
      if (resolvedTimes.departureTime() != null) {
        builder.withDepartureUpdate(
          TimeUpdate.ofAbsolute(
            ServiceTime.of(startOfService, resolvedTimes.departureTime()),
            ServiceTime.ofNullable(startOfService, resolvedAimedTimes.departureTime())
          )
        );
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
    var dropOff = call.dropOff();
    var pickUp = call.pickUp();
    // A cancelled call end carries no intent - what it does to boarding depends on the scheduled
    // value - so the cancellation itself is what has to travel. Asking the call rather than reading
    // the SIRI statuses keeps the two forms of cancellation together, and keeps recorded calls out:
    // they report no status, so only their Cancellation element counts.
    builder.withDropoffCancelled(dropOff.isCancelled());
    builder.withPickupCancelled(pickUp.isCancelled());
    dropOff.intent().ifPresent(builder::withDropoff);
    pickUp.intent().ifPresent(builder::withPickup);
  }

  @Nullable
  private TripCreationInfo buildTripCreationInfo(EstimatedVehicleJourneyWrapper journey) {
    var code = journey.code().orElse(null);
    if (code == null) {
      return null;
    }

    var tripId = createId(code.asServiceJourneyId());
    var builder = TripCreationInfo.builder(tripId);

    // LineRef is required for extra journeys (SIRI Profile requirement). Without it there is no
    // line to run the journey on, and a route made up for it would be published with the trip.
    var lineRef = journey
      .lineRef()
      .orElseThrow(() -> {
        LOG.debug("ADD_NEW_TRIP requires LineRef");
        return UpdateException.noTripId(UNKNOWN);
      });
    builder.withRouteId(createId(lineRef));

    // Set replacedRouteId from ExternalLineRef (only if it differs from LineRef)
    var externalLineRef = journey.externalLineRef().orElse(null);
    if (externalLineRef != null && !externalLineRef.equals(lineRef)) {
      builder.withReplacedRouteId(createId(externalLineRef));
    }

    // The added trip on service date is identified by the DatedServiceJourney form of the code,
    // while the trip itself takes the ServiceJourney form.
    builder.withTripOnServiceDateId(createId(code.asDatedServiceJourneyId()));

    // The published line name names the line, not the journey: it is only used to name a route
    // created for this trip. The trip itself carries no short name - SIRI states none.
    String publishedLineName = journey.publishedLineName();
    if (!publishedLineName.isEmpty()) {
      builder.withPublishedLineName(publishedLineName);
    }

    // SIRI states a mode but no submode: the submode of an extra journey is derived from the mode
    // and the line the journey is classified against, which only the transit model knows.
    builder.withMode(journey.transitMode());

    // OperatorRef is required for extra journeys (SIRI Profile requirement). Only the reference
    // itself is required here: an operator the transit model does not know is later dropped by
    // TripAdditionFactory, and the trip falls back to the operator of the line it runs on.
    var operatorRef = journey
      .operatorRef()
      .orElseThrow(() -> {
        LOG.debug("ADD_NEW_TRIP requires OperatorRef");
        return UpdateException.noTripId(UNKNOWN);
      });
    builder.withOperatorId(createId(operatorRef));

    // The primary replaced-journey ref carries the DatedServiceJourney id of the replaced trip
    journey
      .replacedDatedVehicleJourneyRef()
      .ifPresent(ref ->
        builder.addReplacedTrip(new ReplacedTripReference.DatedTripRef(createId(ref)))
      );

    // An additional ref is a framed ref, naming a further replaced trip by (ServiceJourney id,
    // service date). A framed ref missing either part names no dated trip and is dropped.
    for (var ref : journey.additionalReplacedDatedVehicleJourneyRefs()) {
      if (ref != null && ref.vehicleJourneyId() != null && ref.serviceDate() != null) {
        builder.addReplacedTrip(
          new ReplacedTripReference.TripOnDateRef(
            createId(ref.vehicleJourneyId()),
            ref.serviceDate()
          )
        );
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
}
