package org.opentripplanner.ext.flexbooking.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.core.model.id.FeedScopedIdForTestFactory.id;
import static org.opentripplanner.model.FlexStopTimesFactory.area;
import static org.opentripplanner.transit.model._data.TimetableRepositoryForTest.trip;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.carpooling.model.CarpoolStop;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.opentripplanner.ext.carpooling.model.CarpoolTripBuilder;
import org.opentripplanner.ext.carpooling.routing.CarpoolTreeStreetRouter;
import org.opentripplanner.ext.flex.FlexParameters;
import org.opentripplanner.ext.flex.FlexibleTransitLeg;
import org.opentripplanner.ext.flex.trip.UnscheduledTrip;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.model.calendar.CalendarServiceData;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.routing.algorithm.GraphRoutingTest;
import org.opentripplanner.routing.algorithm.raptoradapter.router.AdditionalSearchDays;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.request.StreetRequest;
import org.opentripplanner.routing.linking.VertexLinkerTestFactory;
import org.opentripplanner.routing.linking.internal.VertexCreationService;
import org.opentripplanner.street.geometry.GeometryUtils;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.street.model.StreetMode;
import org.opentripplanner.street.model.vertex.IntersectionVertex;
import org.opentripplanner.street.service.StreetLimitationParametersService;
import org.opentripplanner.transit.model.site.AreaStop;
import org.opentripplanner.transit.model.timetable.TripIdAndServiceDate;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TimetableRepository;

/**
 * Integration tests for {@link DefaultFlexBookingService#routeDirect} on a real street graph,
 * with an unscheduled flex trip whose area covers the whole corridor.
 * <p>
 * Graph layout (main road going east, P and Q sit south of the road):
 * <pre>
 *           500m         1000m         500m
 *      A ---------- B ----------- C ---------- D
 *      |\          /               \          /
 *      | \        / 255         255 \        / 255
 *      |  \      /                   \      /
 *      |   P ---------- 1400 ---------- Q
 *      |                              /
 *      +-------------- 1500 ----------+
 *
 *   A = vehicle tour start (anchor), D = last booked stop
 *   P = passenger pickup, Q = passenger dropoff
 * </pre>
 */
class DefaultFlexBookingServiceTest extends GraphRoutingTest {

  private static final WgsCoordinate ORIGIN = new WgsCoordinate(59.9139, 10.7522);
  private static final ZoneId ZONE = ZoneId.of("Europe/Oslo");
  private static final LocalDate SERVICE_DATE = LocalDate.of(2025, 6, 15);
  private static final ZonedDateTime SEARCH_TIME = LocalDateTime.of(2025, 6, 15, 12, 0).atZone(
    ZONE
  );
  private static final FeedScopedId TRIP_ID = id("flex-1");
  private static final FlexParameters FLEX_PARAMETERS = FlexParameters.defaultValues();

  private static final StreetLimitationParametersService STREET_LIMITATION_PARAMETERS =
    new StreetLimitationParametersService() {
      @Override
      public float maxCarSpeed() {
        return 40.0f;
      }

      @Override
      public int maxAreaNodes() {
        return 500;
      }

      @Override
      public float getBestWalkSafety() {
        return 1;
      }

      @Override
      public float getBestBikeSafety() {
        return 1;
      }
    };

  private DefaultFlexBookingService service;
  private DefaultFlexBookingRepository repository;

  private WgsCoordinate tourStart;
  private WgsCoordinate tourEnd;
  private WgsCoordinate passengerPickup;
  private WgsCoordinate passengerDropoff;

  private IntersectionVertex vertexTourStart;
  private IntersectionVertex vertexTourEnd;
  private IntersectionVertex vertexPickup;
  private IntersectionVertex vertexDropoff;

  @BeforeEach
  void setUp() {
    var model = modelOf(
      new GraphRoutingTest.Builder() {
        @Override
        public void build() {
          var A = intersection("A", ORIGIN);
          var B = intersection("B", ORIGIN.moveEastMeters(500));
          var C = intersection("C", ORIGIN.moveEastMeters(1500));
          var D = intersection("D", ORIGIN.moveEastMeters(2000));

          var P = intersection("P", ORIGIN.moveEastMeters(250).moveSouthMeters(200));
          var Q = intersection("Q", ORIGIN.moveEastMeters(1750).moveSouthMeters(200));

          tourStart = A.toWgsCoordinate();
          tourEnd = D.toWgsCoordinate();
          passengerPickup = P.toWgsCoordinate();
          passengerDropoff = Q.toWgsCoordinate();
          vertexTourStart = A;
          vertexTourEnd = D;
          vertexPickup = P;
          vertexDropoff = Q;

          biStreet(A, B, 500);
          biStreet(B, C, 1000);
          biStreet(C, D, 500);
          biStreet(A, P, 255);
          biStreet(B, P, 255);
          biStreet(C, Q, 255);
          biStreet(D, Q, 255);
          biStreet(P, Q, 1400);
          biStreet(A, Q, 1500);
        }
      }
    );

    var timetableRepository = model.timetableRepository();
    repository = new DefaultFlexBookingRepository();
    var vertexCreationService = new VertexCreationService(
      VertexLinkerTestFactory.of(model.graph())
    );

    addFlexTrip(timetableRepository, "08:00", "18:00");
    OTPFeature.FlexRouting.testOn(timetableRepository::index);

    service = new DefaultFlexBookingService(
      repository,
      STREET_LIMITATION_PARAMETERS,
      new DefaultTransitService(timetableRepository),
      vertexCreationService
    );
  }

  /**
   * Registers the unscheduled flex trip [area, area] with the given time window; the area's
   * polygon covers the whole test corridor, so origin and destination containment always match.
   */
  private void addFlexTrip(
    TimetableRepository timetableRepository,
    String windowStart,
    String windowEnd
  ) {
    var siteRepositoryBuilder = timetableRepository.getSiteRepository().withContext();
    AreaStop areaStop = siteRepositoryBuilder
      .areaStop(id("AREA"))
      .withName(new NonLocalizedString("AREA"))
      .withGeometry(corridorPolygon())
      .build();
    siteRepositoryBuilder.withAreaStop(areaStop);

    var serviceId = id("S1");
    var flexTrip = UnscheduledTrip.of(TRIP_ID)
      .withTrip(trip(TRIP_ID.getId()).withServiceId(serviceId).build())
      .withStopTimes(
        List.of(area(areaStop, windowEnd, windowStart), area(areaStop, windowEnd, windowStart))
      )
      .build();
    timetableRepository.addFlexTrip(flexTrip.getId(), flexTrip);
    timetableRepository.mergeSiteRepositories(siteRepositoryBuilder.build());

    var calendarData = new CalendarServiceData();
    calendarData.putServiceDatesForServiceId(serviceId, List.of(SERVICE_DATE));
    timetableRepository.updateCalendarServiceData(calendarData);
  }

  private Polygon corridorPolygon() {
    var nw = ORIGIN.moveWestMeters(600).moveNorthMeters(600);
    var ne = ORIGIN.moveEastMeters(2600).moveNorthMeters(600);
    var se = ORIGIN.moveEastMeters(2600).moveSouthMeters(800);
    var sw = ORIGIN.moveWestMeters(600).moveSouthMeters(800);
    return GeometryUtils.getGeometryFactory().createPolygon(
      new Coordinate[] {
        nw.asJtsCoordinate(),
        ne.asJtsCoordinate(),
        se.asJtsCoordinate(),
        sw.asJtsCoordinate(),
        nw.asJtsCoordinate(),
      }
    );
  }

  /**
   * A tour with the vehicle anchor at A and one booked dropoff at D with the given budget.
   */
  private CarpoolTrip tour(ZonedDateTime departure, Duration bookedStopBudget) {
    return new CarpoolTripBuilder(new FeedScopedId("F", "tour-1"))
      .withStartTime(departure)
      .withEndTime(departure.plusMinutes(30))
      .withTotalCapacity(8)
      .withStops(
        List.of(
          CarpoolStop.of(new FeedScopedId("F", "tour-1-anchor"))
            .withCoordinate(tourStart)
            .withAimedDepartureTime(departure)
            .withOnboardCount(2)
            .withDeviationBudget(Duration.ZERO)
            .build(),
          CarpoolStop.of(new FeedScopedId("F", "tour-1-dropoff"))
            .withCoordinate(tourEnd)
            .withAimedArrivalTime(departure.plusMinutes(30))
            .withOnboardCount(1)
            .withDeviationBudget(bookedStopBudget)
            .build()
        )
      )
      .build();
  }

  private void storeTour(CarpoolTrip tour) {
    repository.upsertTour(new TripIdAndServiceDate(TRIP_ID, SERVICE_DATE), tour);
  }

  private RouteRequest buildRequest(WgsCoordinate from, WgsCoordinate to, ZonedDateTime dateTime) {
    return RouteRequest.of()
      .withFrom(GenericLocation.fromCoordinate(from.latitude(), from.longitude()))
      .withTo(GenericLocation.fromCoordinate(to.latitude(), to.longitude()))
      .withDateTime(dateTime.toInstant())
      .withJourney(j -> j.withDirect(new StreetRequest(StreetMode.FLEXIBLE)))
      .buildRequest();
  }

  private List<Itinerary> route(RouteRequest request) {
    return service.routeDirect(
      request,
      FLEX_PARAMETERS,
      AdditionalSearchDays.defaults(SEARCH_TIME)
    );
  }

  @Test
  void returnsEmptyWhenDirectModeIsNotFlexible() {
    storeTour(tour(SEARCH_TIME.plusMinutes(10), Duration.ofMinutes(30)));
    var request = RouteRequest.of()
      .withFrom(
        GenericLocation.fromCoordinate(passengerPickup.latitude(), passengerPickup.longitude())
      )
      .withTo(
        GenericLocation.fromCoordinate(passengerDropoff.latitude(), passengerDropoff.longitude())
      )
      .withDateTime(SEARCH_TIME.toInstant())
      .withJourney(j -> j.withDirect(new StreetRequest(StreetMode.WALK)))
      .buildRequest();

    assertTrue(route(request).isEmpty());
  }

  @Test
  void returnsEmptyWithoutStoredTour() {
    var request = buildRequest(passengerPickup, passengerDropoff, SEARCH_TIME);

    assertTrue(route(request).isEmpty());
  }

  @Test
  void insertsPassengerBetweenAnchorAndBookedStop() {
    var departure = SEARCH_TIME.plusMinutes(10);
    storeTour(tour(departure, Duration.ofMinutes(30)));

    var request = buildRequest(passengerPickup, passengerDropoff, SEARCH_TIME);
    var results = route(request);

    assertFalse(results.isEmpty(), "Should find an insertion-based flex itinerary");
    assertEquals(1, results.size());

    var itinerary = results.getFirst();
    assertEquals(1, itinerary.legs().size(), "Flex itinerary should have exactly one leg");
    var leg = itinerary.legs().getFirst();
    assertTrue(leg instanceof FlexibleTransitLeg, "The ride leg should be a flexible transit leg");
    assertEquals(TRIP_ID, ((FlexibleTransitLeg) leg).trip().getId());
    assertEquals(SERVICE_DATE, ((FlexibleTransitLeg) leg).serviceDate());
    assertTrue(
      service.containsRealTimeManagedLeg(itinerary),
      "The suppression predicate must recognize the managed flex leg"
    );

    // The vehicle drives anchor -> pickup, dwells, then pickup -> dropoff.
    var router = new CarpoolTreeStreetRouter();
    router.addVertex(vertexTourStart, CarpoolTreeStreetRouter.Direction.FROM, Duration.ofHours(2));
    router.addVertex(vertexPickup, CarpoolTreeStreetRouter.Direction.FROM, Duration.ofHours(2));

    var pathToPickup = router.route(vertexTourStart, vertexPickup);
    assertNotNull(pathToPickup);
    var drivingToPickup = Duration.between(
      pathToPickup.states.getFirst().getTime(),
      pathToPickup.states.getLast().getTime()
    );
    var pathPickupToDropoff = router.route(vertexPickup, vertexDropoff);
    assertNotNull(pathPickupToDropoff);
    var drivingPickupToDropoff = Duration.between(
      pathPickupToDropoff.states.getFirst().getTime(),
      pathPickupToDropoff.states.getLast().getTime()
    );
    var stopDuration = request.preferences().car().pickupTime();

    var expectedStart = departure.plus(drivingToPickup);
    var expectedEnd = expectedStart.plus(stopDuration).plus(drivingPickupToDropoff);
    assertEquals(expectedStart.toInstant(), itinerary.startTime().toInstant());
    assertEquals(expectedEnd.toInstant(), itinerary.endTime().toInstant());
  }

  @Test
  void appendsAfterLastBookedStopWhenBudgetsAreZero() {
    // The booked stop cannot be delayed at all, but the window leaves hours of slack after the
    // tour's end, so the passenger is appended after the last booked dropoff. Times derive from
    // the tour start plus ROUTED durations (not the feed's claimed schedule), so "after the
    // tour" means after the routed drive from the anchor to the last booked stop.
    var departure = SEARCH_TIME.plusMinutes(10);
    storeTour(tour(departure, Duration.ZERO));

    var request = buildRequest(passengerPickup, passengerDropoff, SEARCH_TIME);
    var results = route(request);

    assertFalse(results.isEmpty(), "Appending after the tour should be feasible");

    var router = new CarpoolTreeStreetRouter();
    router.addVertex(vertexTourStart, CarpoolTreeStreetRouter.Direction.FROM, Duration.ofHours(2));
    var pathToTourEnd = router.route(vertexTourStart, vertexTourEnd);
    assertNotNull(pathToTourEnd);
    var drivingToTourEnd = Duration.between(
      pathToTourEnd.states.getFirst().getTime(),
      pathToTourEnd.states.getLast().getTime()
    );

    var itinerary = results.getFirst();
    assertTrue(
      itinerary.startTime().toInstant().isAfter(departure.plus(drivingToTourEnd).toInstant()),
      "Pickup must happen after the vehicle has passed its last booked stop"
    );
  }

  @Test
  void returnsEmptyWhenVehicleWouldReachPickupBeforeRequestedDeparture() {
    // The whole tour (and any feasible insertion) lies before the requested departure.
    storeTour(tour(SEARCH_TIME.minusHours(2), Duration.ofMinutes(30)));

    var request = buildRequest(passengerPickup, passengerDropoff, SEARCH_TIME);

    assertTrue(route(request).isEmpty());
  }

  @Test
  void isRealTimeManagedOnlyWhenATourIsStored() {
    assertFalse(service.isRealTimeManaged(TRIP_ID, SERVICE_DATE));

    storeTour(tour(SEARCH_TIME.plusMinutes(10), Duration.ofMinutes(30)));

    assertTrue(service.isRealTimeManaged(TRIP_ID, SERVICE_DATE));
    assertFalse(service.isRealTimeManaged(TRIP_ID, SERVICE_DATE.plusDays(1)));
    assertFalse(service.isRealTimeManaged(id("unknown"), SERVICE_DATE));
  }
}
