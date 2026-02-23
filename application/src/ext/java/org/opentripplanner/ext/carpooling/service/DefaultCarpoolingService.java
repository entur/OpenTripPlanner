package org.opentripplanner.ext.carpooling.service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.opentripplanner.astar.model.GraphPath;
import org.opentripplanner.ext.carpooling.CarpoolingRepository;
import org.opentripplanner.ext.carpooling.CarpoolingService;
import org.opentripplanner.ext.carpooling.constraints.PassengerDelayConstraints;
import org.opentripplanner.ext.carpooling.filter.AccessEgressFilterChain;
import org.opentripplanner.ext.carpooling.filter.FilterChain;
import org.opentripplanner.ext.carpooling.internal.CarpoolItineraryMapper;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.opentripplanner.ext.carpooling.routing.CarpoolAccessEgress;
import org.opentripplanner.ext.carpooling.routing.CarpoolStreetRouter;
import org.opentripplanner.ext.carpooling.routing.CarpoolTreeStreetRouter;
import org.opentripplanner.ext.carpooling.routing.InsertionCandidate;
import org.opentripplanner.ext.carpooling.routing.InsertionEvaluator;
import org.opentripplanner.ext.carpooling.routing.InsertionPosition;
import org.opentripplanner.ext.carpooling.routing.InsertionPositionFinder;
import org.opentripplanner.ext.carpooling.util.BeelineEstimator;
import org.opentripplanner.ext.carpooling.util.StreetVertexUtils;
import org.opentripplanner.framework.model.TimeAndCost;
import org.opentripplanner.graph_builder.module.nearbystops.StopResolver;
import org.opentripplanner.graph_builder.module.nearbystops.StreetNearbyStopFinder;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.routing.algorithm.raptoradapter.router.street.AccessEgressType;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.request.StreetRequest;
import org.opentripplanner.routing.api.response.InputField;
import org.opentripplanner.routing.api.response.RoutingError;
import org.opentripplanner.routing.api.response.RoutingErrorCode;
import org.opentripplanner.routing.error.RoutingValidationException;
import org.opentripplanner.routing.graphfinder.NearbyStop;
import org.opentripplanner.routing.linking.LinkingContext;
import org.opentripplanner.routing.linking.TemporaryVerticesContainer;
import org.opentripplanner.routing.linking.VertexLinker;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.street.model.StreetMode;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.street.service.StreetLimitationParametersService;
import org.opentripplanner.transit.model.site.AreaStop;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.utils.time.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link CarpoolingService} that orchestrates the two-phase
 * carpooling routing algorithm: position finding and insertion evaluation.
 * <p>
 * This service is the main entry point for carpool routing functionality. It coordinates multiple
 * components to efficiently find viable carpool matches while minimizing expensive routing
 * calculations through strategic filtering and early rejection.
 *
 * <h2>Algorithm Phases</h2>
 * <p>
 * The service executes routing requests in three distinct phases:
 * <ol>
 *   <li><strong>Pre-filtering ({@link FilterChain}):</strong> Quickly eliminates incompatible
 *       trips based on capacity, time windows, direction, and distance.</li>
 *   <li><strong>Position Finding ({@link InsertionPositionFinder}):</strong> For trips that
 *       pass filtering, identifies viable pickup/dropoff position pairs using fast heuristics
 *       (capacity, direction, beeline delay estimates). No routing is performed in this phase.</li>
 *   <li><strong>Insertion Evaluation ({@link InsertionEvaluator}):</strong> For viable positions,
 *       computes actual routes using A* street routing. Evaluates all feasible insertion positions
 *       and selects the one minimizing additional travel time while satisfying delay constraints.</li>
 * </ol>
 *
 * <h2>Component Dependencies</h2>
 * <ul>
 *   <li><strong>{@link CarpoolingRepository}:</strong> Source of available driver trips</li>
 *   <li><strong>{@link VertexLinker}:</strong> Links coordinates to graph vertices</li>
 *   <li><strong>{@link StreetLimitationParametersService}:</strong> Street routing configuration</li>
 *   <li><strong>{@link FilterChain}:</strong> Pre-screening filters</li>
 *   <li><strong>{@link InsertionPositionFinder}:</strong> Heuristic position filtering</li>
 *   <li><strong>{@link InsertionEvaluator}:</strong> Routing evaluation and selection</li>
 *   <li><strong>{@link CarpoolItineraryMapper}:</strong> Maps insertions to OTP itineraries</li>
 * </ul>
 *
 * @see CarpoolingService for interface documentation and usage examples
 * @see FilterChain for filtering strategy details
 * @see InsertionPositionFinder for position finding strategy details
 * @see InsertionEvaluator for insertion evaluation algorithm details
 */
public class DefaultCarpoolingService implements CarpoolingService {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultCarpoolingService.class);
  private static final int DEFAULT_MAX_CARPOOL_RESULTS = 3;
  private static final Duration DEFAULT_SEARCH_WINDOW = Duration.ofMinutes(30);
  // How far away in time a carpooling trip can be from the requested departure time to be considered
  private static final Duration ACCESS_EGRESS_SEARCH_WINDOW = Duration.ofHours(12);
  /*
    The time it takes to pick up or drop off a passenger and start driving again. This might be baked into Siri,
    and is a temporary solution. But it seems smart to for now have an estimate of 1 minute so this issue
    is not forgotten.
   */
  private static final Duration CARPOOL_STOP_DURATION = Duration.ofMinutes(1);
  /*
    This is needed for managing computational complexity unless we find a smarter way of searching
    for nearby stops.
   */
  public static final Duration MAX_SEARCH_DURATION_FOR_NEARBY_STOPS_FOR_ACCESS_EGRESS =
    Duration.ofMinutes(60);
  private final CarpoolingRepository repository;
  private final StreetLimitationParametersService streetLimitationParametersService;
  private final FilterChain preFilters;
  private final AccessEgressFilterChain accessEgressPreFilters;
  private final CarpoolItineraryMapper itineraryMapper;
  private final PassengerDelayConstraints delayConstraints;
  private final InsertionPositionFinder positionFinder;
  private final VertexLinker vertexLinker;

  /**
   * Creates a new carpooling service with the specified dependencies.
   * <p>
   * The service is initialized with a standard filter chain. The filter chain
   * is currently hardcoded but could be made configurable in future versions.
   *
   * @param repository provides access to active driver trips, must not be null
   * @param streetLimitationParametersService provides street routing configuration including
   *        speed limits, must not be null
   * @param transitService provides timezone from GTFS agency data for time conversions, must not be null
   * @param vertexLinker links coordinates to graph vertices, must not be null
   * @throws NullPointerException if any parameter is null
   */
  public DefaultCarpoolingService(
    CarpoolingRepository repository,
    StreetLimitationParametersService streetLimitationParametersService,
    TransitService transitService,
    VertexLinker vertexLinker
  ) {
    this.repository = repository;
    this.streetLimitationParametersService = streetLimitationParametersService;
    this.preFilters = FilterChain.standard();
    this.accessEgressPreFilters = AccessEgressFilterChain.standard();
    this.itineraryMapper = new CarpoolItineraryMapper(transitService.getTimeZone());
    this.delayConstraints = new PassengerDelayConstraints();
    this.positionFinder = new InsertionPositionFinder(delayConstraints, new BeelineEstimator());
    this.vertexLinker = vertexLinker;
  }

  @Override
  public List<Itinerary> route(RouteRequest request, LinkingContext linkingContext)
    throws RoutingValidationException {
    if (!StreetMode.CARPOOL.equals(request.journey().direct().mode())) {
      return Collections.emptyList();
    }

    validateRequest(request);

    WgsCoordinate passengerPickup = new WgsCoordinate(request.from().getCoordinate());
    WgsCoordinate passengerDropoff = new WgsCoordinate(request.to().getCoordinate());
    var passengerDepartureTime = request.dateTime();
    var searchWindow = request.searchWindow() == null
      ? DEFAULT_SEARCH_WINDOW
      : request.searchWindow();

    LOG.debug(
      "Finding carpool itineraries from {} to {} at {}",
      passengerPickup,
      passengerDropoff,
      passengerDepartureTime
    );

    var allTrips = repository.getCarpoolTrips();
    LOG.debug("Repository contains {} carpool trips", allTrips.size());

    var candidateTrips = allTrips
      .stream()
      .filter(trip ->
        preFilters.accepts(
          trip,
          passengerPickup,
          passengerDropoff,
          passengerDepartureTime,
          searchWindow
        )
      )
      .toList();

    LOG.debug(
      "{} trips passed pre-filters ({} rejected)",
      candidateTrips.size(),
      allTrips.size() - candidateTrips.size()
    );

    if (candidateTrips.isEmpty()) {
      return List.of();
    }

    var itineraries = List.<Itinerary>of();
    try (var temporaryVerticesContainer = new TemporaryVerticesContainer()) {
      var router = new CarpoolStreetRouter(streetLimitationParametersService, request);

      var streetVertexUtils = new StreetVertexUtils(this.vertexLinker, temporaryVerticesContainer);

      var insertionEvaluator = new InsertionEvaluator(
        delayConstraints,
        linkingContext,
        streetVertexUtils,
        router
      );

      // Find optimal insertions for remaining trips
      var insertionCandidates = candidateTrips
        .stream()
        .map(trip -> {
          List<InsertionPosition> viablePositions = positionFinder.findViablePositions(
            trip,
            passengerPickup,
            passengerDropoff
          );

          if (viablePositions.isEmpty()) {
            LOG.debug("No viable positions found for trip {} (avoided all routing!)", trip.getId());
            return null;
          }

          LOG.debug(
            "{} viable positions found for trip {}, evaluating with routing",
            viablePositions.size(),
            trip.getId()
          );

          var tripVertices = getVerticesForTrip(trip, streetVertexUtils, linkingContext);
          trip.setVertices(tripVertices);

          // Evaluate only viable positions with expensive routing
          return insertionEvaluator.findBestInsertion(
            trip,
            viablePositions,
            passengerPickup,
            passengerDropoff
          );
        })
        .filter(Objects::nonNull)
        .sorted(Comparator.comparing(InsertionCandidate::additionalDuration))
        .limit(DEFAULT_MAX_CARPOOL_RESULTS)
        .toList();

      LOG.debug("Found {} viable insertion candidates", insertionCandidates.size());

      itineraries = insertionCandidates
        .stream()
        .map(candidate -> itineraryMapper.toItinerary(request, candidate))
        .filter(Objects::nonNull)
        .toList();
    }

    LOG.info("Returning {} carpool itineraries", itineraries.size());
    return itineraries;
  }

  @Override
  public List<CarpoolAccessEgress> routeAccessEgress(
    RouteRequest request,
    StreetRequest streetRequest,
    AccessEgressType accessOrEgress,
    StopResolver stopResolver,
    LinkingContext linkingContext,
    ZonedDateTime transitSearchTimeZero
  ) throws RoutingValidationException {
    try {
      if (
        !StreetMode.CARPOOL.equals(request.journey().access().mode()) && accessOrEgress.isAccess()
      ) {
        return Collections.emptyList();
      }

      if (
        !StreetMode.CARPOOL.equals(request.journey().egress().mode()) && accessOrEgress.isEgress()
      ) {
        return Collections.emptyList();
      }

      var allTrips = repository.getCarpoolTrips();
      LOG.debug("Repository contains {} carpool trips", allTrips.size());

      GenericLocation passengerAccessEgressLocation = accessOrEgress.isAccess()
        ? request.from()
        : request.to();
      WgsCoordinate passengerAccessEgressCoordinates = new WgsCoordinate(
        passengerAccessEgressLocation.lat,
        passengerAccessEgressLocation.lng
      );

      var passengerDepartureTime = request.dateTime();

      var candidateTrips = allTrips
        .stream()
        .filter(trip ->
          accessEgressPreFilters.accepts(
            trip,
            passengerAccessEgressCoordinates,
            passengerDepartureTime,
            ACCESS_EGRESS_SEARCH_WINDOW
          )
        )
        .toList();

      if (candidateTrips.isEmpty()) {
        return List.of();
      }

      var temporaryVerticesContainer = new TemporaryVerticesContainer();
      var streetVertexUtils = new StreetVertexUtils(this.vertexLinker, temporaryVerticesContainer);

      var carPoolTreeVertexRouter = new CarpoolTreeStreetRouter();
      Vertex passengerAccessEgressVertex = streetVertexUtils.getOrCreateVertex(
        passengerAccessEgressCoordinates,
        linkingContext
      );

      var streetNearbyStopFinder = StreetNearbyStopFinder.of(
        stopResolver,
        MAX_SEARCH_DURATION_FOR_NEARBY_STOPS_FOR_ACCESS_EGRESS,
        0
      );

      var nearByStops = streetNearbyStopFinder
        .build()
        .findNearbyStops(
          Set.of(passengerAccessEgressVertex),
          request,
          streetRequest,
          accessOrEgress.isEgress()
        )
        .stream()
        .filter(stop -> !(stop.stop instanceof AreaStop))
        .toList();

      var nearByStopsWithVertices = nearByStops
        .stream()
        .collect(
          Collectors.toMap(
            stop -> stop,
            stop -> streetVertexUtils.getOrCreateVertex(stop.stop.getCoordinate(), linkingContext)
          )
        );

      candidateTrips.forEach(carpoolTrip -> {
        var vertices = getVerticesForTrip(carpoolTrip, streetVertexUtils, linkingContext);
        carpoolTrip.setVertices(vertices);
      });

      // vertices have to be added to the carPoolTreeVertexRouter AFTER all vertices have been created
      carPoolTreeVertexRouter.addVertex(
        passengerAccessEgressVertex,
        CarpoolTreeStreetRouter.Direction.BOTH,
        MAX_SEARCH_DURATION_FOR_NEARBY_STOPS_FOR_ACCESS_EGRESS
      );
      candidateTrips.forEach(carpoolTrip -> {
        var vertices = carpoolTrip.getVertices();
        carPoolTreeVertexRouter.addVertex(
          vertices.getFirst(),
          CarpoolTreeStreetRouter.Direction.FROM,
          MAX_SEARCH_DURATION_FOR_NEARBY_STOPS_FOR_ACCESS_EGRESS
        );
        carPoolTreeVertexRouter.addVertex(
          vertices.getLast(),
          CarpoolTreeStreetRouter.Direction.TO,
          MAX_SEARCH_DURATION_FOR_NEARBY_STOPS_FOR_ACCESS_EGRESS
        );

        var middleVertices = vertices.subList(1, vertices.size() - 1);
        middleVertices.forEach(vertex -> {
          carPoolTreeVertexRouter.addVertex(
            vertex,
            CarpoolTreeStreetRouter.Direction.BOTH,
            MAX_SEARCH_DURATION_FOR_NEARBY_STOPS_FOR_ACCESS_EGRESS
          );
        });
      });

      var insertionEvaluator = new InsertionEvaluator(
        delayConstraints,
        linkingContext,
        streetVertexUtils,
        carPoolTreeVertexRouter
      );

      var candidateTripsWithViableStopsAndPositions = candidateTrips
        .stream()
        .map(candidateTrip -> {
          var viableSegmentInsertions = nearByStops
            .stream()
            .map(nearbyStop -> {
              var pickUpCoord = accessOrEgress.isAccess()
                ? passengerAccessEgressCoordinates
                : nearbyStop.stop.getCoordinate();
              var dropOffCoord = accessOrEgress.isAccess()
                ? nearbyStop.stop.getCoordinate()
                : passengerAccessEgressCoordinates;

              var viablePositions = positionFinder.findViablePositions(
                candidateTrip,
                pickUpCoord,
                dropOffCoord
              );
              return new ViableAccessEgress(
                nearbyStop,
                nearByStopsWithVertices.get(nearbyStop),
                passengerAccessEgressVertex,
                accessOrEgress,
                viablePositions
              );
            })
            .filter(it -> !it.insertionPositions.isEmpty())
            .toList();
          return new TripWithViableAccessEgress(candidateTrip, viableSegmentInsertions);
        })
        .toList();

      var insertionCandidates = candidateTripsWithViableStopsAndPositions
        .stream()
        .flatMap(it -> insertionEvaluator.findBestInsertions(it).stream())
        .toList();

      var accessEgresses = insertionCandidates
        .stream()
        .map(it -> createCarpoolAccessEgress(it, transitSearchTimeZero))
        .toList();
      return accessEgresses;
    } catch (Exception e) {
      LOG.error(e.getMessage());
      LOG.error(Arrays.toString(e.getStackTrace()));
      throw e;
    }
  }

  private List<Vertex> getVerticesForTrip(
    CarpoolTrip trip,
    StreetVertexUtils streetVertexUtils,
    LinkingContext linkingContext
  ) {
    var stops = trip.routePoints();
    return stops
      .stream()
      .map(coordinate -> streetVertexUtils.getOrCreateVertex(coordinate, linkingContext))
      .toList();
  }

  public record ViableAccessEgress(
    NearbyStop transitStop,
    Vertex transitVertex,
    Vertex passengerVertex,
    AccessEgressType accessEgress,
    List<InsertionPosition> insertionPositions
  ) {}

  public record TripWithViableAccessEgress(
    CarpoolTrip trip,
    List<ViableAccessEgress> viableAccessEgress
  ) {}

  private void validateRequest(RouteRequest request) throws RoutingValidationException {
    Objects.requireNonNull(request.from());
    Objects.requireNonNull(request.to());
    if (request.from().lat == null || request.from().lng == null) {
      throw new RoutingValidationException(
        List.of(new RoutingError(RoutingErrorCode.LOCATION_NOT_FOUND, InputField.FROM_PLACE))
      );
    }
    if (request.to().lat == null || request.to().lng == null) {
      throw new RoutingValidationException(
        List.of(new RoutingError(RoutingErrorCode.LOCATION_NOT_FOUND, InputField.TO_PLACE))
      );
    }
  }

  private Duration getTotalDurationOfSegments(
    List<GraphPath<State, Edge, Vertex>> segments,
    Duration extraTimeForStop
  ) {
    return segments
      .stream()
      .map(it -> Duration.between(it.states.getFirst().getTime(), it.states.getLast().getTime()))
      .reduce(Duration.ZERO, Duration::plus)
      .plus(extraTimeForStop.multipliedBy(segments.size() - 1));
  }

  public CarpoolAccessEgress createCarpoolAccessEgress(
    InsertionCandidate insertionCandidate,
    ZonedDateTime transitSearchTimeZero
  ) {
    var pickUpIndex = insertionCandidate.pickupPosition();
    var dropOffIndex = insertionCandidate.dropoffPosition() - 1;

    var segmentsBeforeInsertion = insertionCandidate.routeSegments().subList(0, pickUpIndex);
    var segmentsWithPassenger = insertionCandidate
      .routeSegments()
      .subList(pickUpIndex, dropOffIndex + 1);
    var durationBeforeInsertion = getTotalDurationOfSegments(
      segmentsBeforeInsertion,
      CARPOOL_STOP_DURATION
    );
    var durationWithPassenger = getTotalDurationOfSegments(
      segmentsWithPassenger,
      CARPOOL_STOP_DURATION
    );

    var startTimeOfSegment = insertionCandidate.trip().startTime().plus(durationBeforeInsertion);
    var endTimeOfSegment = startTimeOfSegment.plus(durationWithPassenger);

    var relativeStartTime = TimeUtils.toTransitTimeSeconds(
      transitSearchTimeZero,
      startTimeOfSegment.toInstant()
    );
    var relativeEndTime = TimeUtils.toTransitTimeSeconds(
      transitSearchTimeZero,
      endTimeOfSegment.toInstant()
    );

    var accessEgress = new CarpoolAccessEgress(
      insertionCandidate.transitStop().stop.getIndex(),
      durationWithPassenger,
      CARPOOL_STOP_DURATION,
      relativeStartTime,
      relativeEndTime,
      segmentsWithPassenger,
      TimeAndCost.ZERO
    );

    return accessEgress;
  }
}
