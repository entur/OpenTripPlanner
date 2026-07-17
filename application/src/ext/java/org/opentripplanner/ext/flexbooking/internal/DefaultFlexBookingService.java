package org.opentripplanner.ext.flexbooking.internal;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.locationtech.jts.geom.Envelope;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.carpooling.model.CarpoolStop;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.opentripplanner.ext.carpooling.model.CarpoolTripBuilder;
import org.opentripplanner.ext.carpooling.routing.CarpoolStreetRouter;
import org.opentripplanner.ext.carpooling.routing.CarpoolTripWithVertices;
import org.opentripplanner.ext.carpooling.routing.InsertionCandidate;
import org.opentripplanner.ext.carpooling.routing.InsertionEvaluator;
import org.opentripplanner.ext.carpooling.routing.InsertionPositionFinder;
import org.opentripplanner.ext.carpooling.routing.PassengerSnap;
import org.opentripplanner.ext.carpooling.util.BeelineEstimator;
import org.opentripplanner.ext.carpooling.util.CarAccessibleVertexSnapper;
import org.opentripplanner.ext.carpooling.util.GraphPathUtils;
import org.opentripplanner.ext.carpooling.util.StreetVertexUtils;
import org.opentripplanner.ext.flex.FlexParameters;
import org.opentripplanner.ext.flex.FlexibleTransitLeg;
import org.opentripplanner.ext.flex.filter.FilterMapper;
import org.opentripplanner.ext.flex.trip.FlexTrip;
import org.opentripplanner.ext.flex.trip.UnscheduledTrip;
import org.opentripplanner.ext.flexbooking.FlexBookingRepository;
import org.opentripplanner.ext.flexbooking.FlexBookingService;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.routing.algorithm.raptoradapter.router.AdditionalSearchDays;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.response.InputField;
import org.opentripplanner.routing.api.response.RoutingError;
import org.opentripplanner.routing.api.response.RoutingErrorCode;
import org.opentripplanner.routing.error.RoutingValidationException;
import org.opentripplanner.routing.linking.internal.VertexCreationService;
import org.opentripplanner.street.geometry.GeometryUtils;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.street.linking.TemporaryVerticesContainer;
import org.opentripplanner.street.model.StreetMode;
import org.opentripplanner.street.service.StreetLimitationParametersService;
import org.opentripplanner.streetadapter.StreetSearchRequestMapper;
import org.opentripplanner.transit.model.filter.transit.TripMatcherFactory;
import org.opentripplanner.transit.model.site.AreaStop;
import org.opentripplanner.transit.model.timetable.TripIdAndServiceDate;
import org.opentripplanner.transit.model.timetable.booking.RoutingBookingInfo;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.utils.time.ServiceDateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link FlexBookingService}.
 * <p>
 * The orchestration mirrors the carpooling direct-routing flow and reuses its insertion
 * machinery; candidate selection differs because flex trips have explicit service areas: a trip
 * qualifies when its pattern lets the passenger board in an area containing the origin and
 * alight in an area containing the destination.
 *
 * <h2>End anchor</h2>
 * The insertion machinery only inserts strictly between a tour's first and last element, so
 * before running insertion the stored tour gets a synthetic end anchor appended: a copy of the
 * last stop whose deviation budget is the remaining service window (NeTEx window end at the
 * alight position minus the tour's expected end). This is what makes "append the new passenger
 * after the last booked dropoff, capped by the service window" expressible; the anchor's phantom
 * return segment makes the check conservative.
 */
public class DefaultFlexBookingService implements FlexBookingService {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultFlexBookingService.class);

  private final FlexBookingRepository repository;
  private final StreetLimitationParametersService streetLimitationParametersService;
  private final TransitService transitService;
  private final VertexCreationService vertexCreationService;
  private final InsertionPositionFinder positionFinder;
  private final FlexBookingItineraryMapper itineraryMapper = new FlexBookingItineraryMapper();

  public DefaultFlexBookingService(
    FlexBookingRepository repository,
    StreetLimitationParametersService streetLimitationParametersService,
    TransitService transitService,
    VertexCreationService vertexCreationService
  ) {
    this.repository = repository;
    this.streetLimitationParametersService = streetLimitationParametersService;
    this.transitService = transitService;
    this.vertexCreationService = vertexCreationService;
    this.positionFinder = new InsertionPositionFinder(
      new BeelineEstimator(streetLimitationParametersService.maxCarSpeed())
    );
  }

  @Override
  public boolean containsRealTimeManagedLeg(Itinerary itinerary) {
    return itinerary
      .legs()
      .stream()
      .anyMatch(
        leg ->
          leg instanceof FlexibleTransitLeg flexLeg &&
          isRealTimeManaged(flexLeg.trip().getId(), flexLeg.serviceDate())
      );
  }

  /**
   * Whether the given flex trip and service date is governed by a stored real-time tour.
   */
  public boolean isRealTimeManaged(FeedScopedId tripId, LocalDate serviceDate) {
    return (
      repository.findTour(new TripIdAndServiceDate(tripId, serviceDate)).isPresent() &&
      transitService.getFlexIndex().getTripById(tripId) instanceof UnscheduledTrip
    );
  }

  @Override
  public List<Itinerary> routeDirect(
    RouteRequest request,
    FlexParameters flexParameters,
    AdditionalSearchDays additionalSearchDays
  ) {
    if (!StreetMode.FLEXIBLE.equals(request.journey().direct().mode())) {
      return List.of();
    }
    validateRequest(request);

    var from = request.from().wgsCoordinate();
    var to = request.to().wgsCoordinate();
    var timeZone = transitService.getTimeZone();

    var originAreas = areaStopsContaining(from);
    var destinationAreas = areaStopsContaining(to);
    if (originAreas.isEmpty() || destinationAreas.isEmpty()) {
      return List.of();
    }

    var candidates = findCandidateTrips(request, originAreas, destinationAreas);
    if (candidates.isEmpty()) {
      return List.of();
    }

    var datedCandidates = findDatedCandidatesWithTour(
      candidates,
      LocalDate.ofInstant(request.dateTime(), timeZone),
      additionalSearchDays
    );
    if (datedCandidates.isEmpty()) {
      return List.of();
    }

    var itineraries = new ArrayList<Itinerary>();
    try (var temporaryVerticesContainer = new TemporaryVerticesContainer()) {
      var streetVertexUtils = new StreetVertexUtils(
        vertexCreationService,
        temporaryVerticesContainer
      );
      var router = new CarpoolStreetRouter(streetLimitationParametersService);
      var stopDuration = request.preferences().car().pickupTime();
      var streetSearchRequest = StreetSearchRequestMapper.map(request).build();

      var passengerPickupVertex = streetVertexUtils.createPassengerVertex(from);
      var passengerDropoffVertex = streetVertexUtils.createPassengerVertex(to);
      if (passengerPickupVertex == null || passengerDropoffVertex == null) {
        LOG.warn("Could not link passenger origin/destination to graph");
        return List.of();
      }

      var pickupSnap = CarAccessibleVertexSnapper.snapPickup(
        streetSearchRequest,
        passengerPickupVertex,
        flexParameters.maxAccessWalkDuration()
      );
      var dropoffSnap = CarAccessibleVertexSnapper.snapDropoff(
        streetSearchRequest,
        passengerDropoffVertex,
        flexParameters.maxEgressWalkDuration()
      );
      if (pickupSnap == null || dropoffSnap == null) {
        LOG.debug("No car-accessible pickup/dropoff reachable from passenger origin/destination");
        return List.of();
      }

      var insertionEvaluator = new InsertionEvaluator(router, stopDuration);
      var snappedPickup = new WgsCoordinate(pickupSnap.vertex().getCoordinate());
      var snappedDropoff = new WgsCoordinate(dropoffSnap.vertex().getCoordinate());
      var snap = new PassengerSnap(
        pickupSnap.vertex(),
        dropoffSnap.vertex(),
        pickupSnap.walkPath(),
        dropoffSnap.walkPath()
      );
      var walkToPickup = GraphPathUtils.durationOrZero(pickupSnap.walkPath());
      var walkFromDropoff = GraphPathUtils.durationOrZero(dropoffSnap.walkPath());
      var startOfServiceByDate = new HashMap<LocalDate, ZonedDateTime>();

      for (var dated : datedCandidates) {
        var trip = dated.candidate().trip();
        var boardPos = dated.candidate().boardStopPosition();
        var alightPos = dated.candidate().alightStopPosition();
        var startOfService = startOfServiceByDate.computeIfAbsent(dated.serviceDate(), date ->
          ServiceDateUtils.asStartOfService(date, timeZone)
        );

        var routingBookingInfo = request.bookingTime() == null
          ? RoutingBookingInfo.unrestricted()
          : RoutingBookingInfo.of(
              ServiceDateUtils.secondsSinceStartOfTime(startOfService, request.bookingTime()),
              trip.getPickupBookingInfo(boardPos)
            );
        if (routingBookingInfo.exceedsLatestBookingTime()) {
          LOG.debug("Trip {} rejected: request exceeds the latest booking time", trip.getId());
          continue;
        }

        var insertionTour = appendEndAnchor(dated.tour(), trip, alightPos, startOfService);

        var viablePositions = positionFinder.findViablePositions(
          insertionTour,
          snappedPickup,
          snappedDropoff,
          stopDuration
        );
        if (viablePositions.isEmpty()) {
          LOG.debug("No viable insertion positions for tour {}", insertionTour.getId());
          continue;
        }

        var tourWithVertices = CarpoolTripWithVertices.create(insertionTour, streetVertexUtils);
        if (tourWithVertices == null) {
          LOG.warn("Could not resolve vertices for tour {}", insertionTour.getId());
          continue;
        }

        // Pick the cheapest insertion that passes the time feasibility checks. Evaluating every
        // viable position (rather than only the globally cheapest) matters when the vehicle
        // passes the pickup area more than once: a later, slightly costlier pass may satisfy
        // the requested departure time when the cheapest one does not.
        InsertionCandidate cheapest = null;
        FlexInsertionFeasibility.TimedInsertion cheapestTimes = null;
        for (var candidate : insertionEvaluator.findInsertions(
          tourWithVertices,
          viablePositions,
          snap
        )) {
          var times = FlexInsertionFeasibility.evaluate(
            insertionTour.startTime(),
            candidate.getDurationUntilPickupArrival(),
            candidate.getPassengerRideDuration(),
            trip,
            boardPos,
            alightPos,
            startOfService,
            request.dateTime(),
            request.arriveBy(),
            walkToPickup,
            walkFromDropoff,
            routingBookingInfo
          );
          if (times.isEmpty()) {
            continue;
          }
          if (
            cheapest == null ||
            candidate.totalTripDuration().compareTo(cheapest.totalTripDuration()) < 0
          ) {
            cheapest = candidate;
            cheapestTimes = times.get();
          }
        }
        if (cheapest == null) {
          LOG.debug("No feasible insertion for tour {}", insertionTour.getId());
          continue;
        }

        var itinerary = itineraryMapper.toItinerary(
          cheapest,
          trip,
          boardPos,
          alightPos,
          dated.serviceDate(),
          cheapestTimes,
          flexParameters
        );
        if (itinerary != null) {
          itineraries.add(itinerary);
        }
      }
    }

    LOG.info("Returning {} flex booking itineraries", itineraries.size());
    return itineraries;
  }

  /**
   * A flex trip the passenger could ride from an origin area to a destination area, with the
   * resolved board and alight positions in the trip's pattern.
   */
  private record CandidateTrip(
    UnscheduledTrip trip,
    int boardStopPosition,
    int alightStopPosition
  ) {}

  private record DatedCandidate(CandidateTrip candidate, LocalDate serviceDate, CarpoolTrip tour) {}

  private Map<FeedScopedId, CandidateTrip> findCandidateTrips(
    RouteRequest request,
    List<AreaStop> originAreas,
    List<AreaStop> destinationAreas
  ) {
    var flexIndex = transitService.getFlexIndex();
    var matcher = TripMatcherFactory.of(
      FilterMapper.map(request.journey().transit().filters()),
      transitService.getTripCalendars()::listServiceDates
    );

    Map<FeedScopedId, CandidateTrip> candidates = new LinkedHashMap<>();
    for (var originArea : originAreas) {
      for (var flexTrip : flexIndex.getFlexTripsByStopId(originArea.getId())) {
        var tripId = flexTrip.getTrip().getId();
        if (candidates.containsKey(tripId)) {
          continue;
        }
        if (!(flexTrip instanceof UnscheduledTrip unscheduledTrip)) {
          continue;
        }
        if (!matcher.match(flexTrip.getTrip())) {
          continue;
        }
        int boardPos = unscheduledTrip.findBoardIndex(originArea.getId());
        if (boardPos == FlexTrip.STOP_INDEX_NOT_FOUND) {
          continue;
        }
        for (var destinationArea : destinationAreas) {
          int alightPos = unscheduledTrip.findAlightIndex(destinationArea.getId());
          if (alightPos != FlexTrip.STOP_INDEX_NOT_FOUND && boardPos < alightPos) {
            candidates.put(tripId, new CandidateTrip(unscheduledTrip, boardPos, alightPos));
            break;
          }
        }
      }
    }
    return candidates;
  }

  /**
   * Resolves the (trip, service date) pairs that are active within the search window AND have a
   * stored real-time tour. Pairs without a tour are left to the static flex pipeline.
   */
  private List<DatedCandidate> findDatedCandidatesWithTour(
    Map<FeedScopedId, CandidateTrip> candidates,
    LocalDate searchDate,
    AdditionalSearchDays additionalSearchDays
  ) {
    var flexIndex = transitService.getFlexIndex();
    var result = new ArrayList<DatedCandidate>();
    var seen = new HashSet<TripIdAndServiceDate>();
    for (
      int d = -additionalSearchDays.additionalSearchDaysInPast();
      d <= additionalSearchDays.additionalSearchDaysInFuture();
      d++
    ) {
      for (var tripForDate : flexIndex.getFlexTripsForRunningDate(searchDate.plusDays(d))) {
        var candidate = candidates.get(tripForDate.flexTrip().getTrip().getId());
        if (candidate == null) {
          continue;
        }
        var key = new TripIdAndServiceDate(
          tripForDate.flexTrip().getTrip().getId(),
          tripForDate.serviceDate()
        );
        if (!seen.add(key)) {
          continue;
        }
        repository
          .findTour(key)
          .ifPresent(tour -> result.add(new DatedCandidate(candidate, key.serviceDate(), tour)));
      }
    }
    return result;
  }

  private List<AreaStop> areaStopsContaining(WgsCoordinate coordinate) {
    var jtsCoordinate = coordinate.asJtsCoordinate();
    var point = GeometryUtils.getGeometryFactory().createPoint(jtsCoordinate);
    return transitService
      .findAreaStops(new Envelope(jtsCoordinate))
      .stream()
      .filter(areaStop -> areaStop.getGeometry().contains(point))
      .toList();
  }

  /**
   * Appends the synthetic end anchor that makes "append after the last booked stop" expressible:
   * a copy of the last stop whose deviation budget is the remaining service window — the NeTEx
   * window end at the alight position minus the tour's currently expected end (floored at zero).
   */
  private static CarpoolTrip appendEndAnchor(
    CarpoolTrip tour,
    UnscheduledTrip trip,
    int alightStopPosition,
    ZonedDateTime startOfService
  ) {
    var lastStop = tour.stops().getLast();
    var windowEnd = startOfService.plusSeconds(trip.latestArrivalTime(alightStopPosition));
    var expectedEnd = lastStop.getScheduledArrivalTime() != null
      ? lastStop.getScheduledArrivalTime()
      : tour.endTime();
    var extensionBudget = Duration.between(expectedEnd, windowEnd);
    if (extensionBudget.isNegative()) {
      extensionBudget = Duration.ZERO;
    }

    var anchor = CarpoolStop.of(
      new FeedScopedId(tour.getId().getFeedId(), tour.getId().getId() + "_end_anchor")
    )
      .withCoordinate(lastStop.getCoordinate())
      .withOnboardCount(lastStop.getOnboardCount())
      .withDeviationBudget(extensionBudget)
      .build();

    var stops = new ArrayList<>(tour.stops());
    stops.add(anchor);
    return new CarpoolTripBuilder(tour).withStops(stops).build();
  }

  private void validateRequest(RouteRequest request) throws RoutingValidationException {
    Objects.requireNonNull(request.from());
    Objects.requireNonNull(request.to());
    if (request.from().wgsCoordinate() == null) {
      throw new RoutingValidationException(
        List.of(new RoutingError(RoutingErrorCode.LOCATION_NOT_FOUND, InputField.FROM_PLACE))
      );
    }
    if (request.to().wgsCoordinate() == null) {
      throw new RoutingValidationException(
        List.of(new RoutingError(RoutingErrorCode.LOCATION_NOT_FOUND, InputField.TO_PLACE))
      );
    }
  }
}
