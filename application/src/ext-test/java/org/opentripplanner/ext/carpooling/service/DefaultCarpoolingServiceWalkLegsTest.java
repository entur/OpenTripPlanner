package org.opentripplanner.ext.carpooling.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.ext.carpooling.CarpoolTripTestData;
import org.opentripplanner.ext.carpooling.CarpoolingRepository;
import org.opentripplanner.ext.carpooling.internal.DefaultCarpoolingRepository;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.model.plan.leg.StreetLeg;
import org.opentripplanner.routing.algorithm.GraphRoutingTest;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.request.StreetRequest;
import org.opentripplanner.routing.linking.VertexLinkerTestFactory;
import org.opentripplanner.routing.linking.internal.VertexCreationService;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.street.linking.VertexLinker;
import org.opentripplanner.street.model.StreetMode;
import org.opentripplanner.street.model.StreetTraversalPermission;
import org.opentripplanner.street.model.vertex.IntersectionVertex;
import org.opentripplanner.street.search.TraverseMode;
import org.opentripplanner.street.service.StreetLimitationParametersService;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TransitService;

/**
 * Integration tests that exercise the walk-to/from-carpool behavior added to
 * {@link DefaultCarpoolingService}. The graph places the passenger's origin and destination on
 * pedestrian-only edges, so the snapper must find a nearby stoppable vertex and the resulting
 * itinerary must contain leading and trailing WALK {@link StreetLeg}s around the carpool leg.
 *
 * <pre>
 *   A ====== B ============= C ====== D          (=  biStreet: car + ped)
 *            .               .
 *            . (ped only)    . (ped only)
 *            P               Q                   (passenger pickup / dropoff)
 * </pre>
 */
class DefaultCarpoolingServiceWalkLegsTest extends GraphRoutingTest {

  private static final WgsCoordinate ORIGIN = new WgsCoordinate(59.9139, 10.7522);
  private static final ZoneId ZONE = ZoneId.of("Europe/Oslo");
  private static final ZonedDateTime SEARCH_TIME = LocalDateTime.of(2025, 6, 15, 12, 0).atZone(
    ZONE
  );

  private DefaultCarpoolingService service;
  private CarpoolingRepository repository;

  private WgsCoordinate tripStart;
  private WgsCoordinate tripEnd;
  private WgsCoordinate passengerPickup;
  private WgsCoordinate passengerDropoff;

  private IntersectionVertex vertexPickup;
  private IntersectionVertex vertexDropoff;
  private IntersectionVertex vertexB;
  private IntersectionVertex vertexC;

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

          var P = intersection("P", ORIGIN.moveEastMeters(500).moveSouthMeters(80));
          var Q = intersection("Q", ORIGIN.moveEastMeters(1500).moveSouthMeters(80));

          tripStart = A.toWgsCoordinate();
          tripEnd = D.toWgsCoordinate();
          passengerPickup = P.toWgsCoordinate();
          passengerDropoff = Q.toWgsCoordinate();
          vertexPickup = P;
          vertexDropoff = Q;
          vertexB = B;
          vertexC = C;

          biStreet(A, B, 500);
          biStreet(B, C, 1000);
          biStreet(C, D, 500);
          // Passenger pickup and dropoff sit on pedestrian-only spurs off the main road.
          street(
            B,
            P,
            80,
            StreetTraversalPermission.PEDESTRIAN,
            StreetTraversalPermission.PEDESTRIAN
          );
          street(
            C,
            Q,
            80,
            StreetTraversalPermission.PEDESTRIAN,
            StreetTraversalPermission.PEDESTRIAN
          );
        }
      }
    );

    Graph graph = model.graph();
    var timetableRepository = model.timetableRepository();
    VertexLinker vertexLinker = VertexLinkerTestFactory.of(graph);
    var vertexCreationService = new VertexCreationService(vertexLinker);
    TransitService transitService = new DefaultTransitService(timetableRepository);
    repository = new DefaultCarpoolingRepository();

    StreetLimitationParametersService streetLimitationParams =
      new StreetLimitationParametersService() {
        @Override
        public float maxCarSpeed() {
          return 40.0f;
        }

        @Override
        public int maxAreaNodes() {
          return 500;
        }
      };

    service = new DefaultCarpoolingService(
      repository,
      streetLimitationParams,
      transitService,
      vertexCreationService
    );
  }

  private RouteRequest buildDirectCarpoolRequest(ZonedDateTime dateTime) {
    return RouteRequest.of()
      .withFrom(
        GenericLocation.fromCoordinate(passengerPickup.latitude(), passengerPickup.longitude())
      )
      .withTo(
        GenericLocation.fromCoordinate(passengerDropoff.latitude(), passengerDropoff.longitude())
      )
      .withDateTime(dateTime.toInstant())
      .withJourney(j -> j.withDirect(new StreetRequest(StreetMode.CARPOOL)))
      .buildRequest();
  }

  @Test
  void passengerOnPedestrianOnlyEdge_emitsWalkLegsAroundCarpoolLeg() {
    var departureTime = SEARCH_TIME.plusMinutes(10);
    var trip = CarpoolTripTestData.createSimpleTripWithTime(tripStart, tripEnd, departureTime);
    repository.upsertCarpoolTrip(trip);

    var request = buildDirectCarpoolRequest(SEARCH_TIME);
    var results = service.routeDirect(request);

    assertFalse(results.isEmpty(), "Expected a direct carpool itinerary when walks are feasible");

    for (var itinerary : results) {
      var legs = itinerary.legs();
      assertEquals(
        3,
        legs.size(),
        () -> "Expected WALK + CARPOOL + WALK, got " + legs.size() + " legs: " + legs
      );

      var firstLeg = legs.getFirst();
      assertTrue(
        firstLeg instanceof StreetLeg sl && sl.getMode() == TraverseMode.WALK,
        "First leg should be a walking StreetLeg"
      );
      assertTrue(firstLeg.duration().toSeconds() > 0, "Walk-to-pickup duration should be positive");

      var lastLeg = legs.getLast();
      assertTrue(
        lastLeg instanceof StreetLeg sl && sl.getMode() == TraverseMode.WALK,
        "Last leg should be a walking StreetLeg"
      );
      assertTrue(
        lastLeg.duration().toSeconds() > 0,
        "Walk-from-dropoff duration should be positive"
      );
    }
  }
}
