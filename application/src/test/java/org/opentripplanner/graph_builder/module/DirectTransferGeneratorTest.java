package org.opentripplanner.graph_builder.module;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.opentripplanner.TestOtpModel;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.model.PathTransfer;
import org.opentripplanner.routing.algorithm.GraphRoutingTest;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.routing.api.request.request.StreetRequest;
import org.opentripplanner.routing.graphfinder.StopResolver;
import org.opentripplanner.street.model.StreetTraversalPermission;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.StreetVertex;
import org.opentripplanner.street.model.vertex.TransitStopVertex;
import org.opentripplanner.transit.model._data.TimetableRepositoryForTest;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.network.BikeAccess;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.site.Station;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.ScheduledTripTimes;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 * This creates a graph with trip patterns
 <pre>
  S0 -  V0 ------------
        |     \       |
 S11 - V11 --------> V21 - S21
        |      \      |
 S12 - V12 --------> V22 - V22
        |             |
 S13 - V13 --------> V23 - V23
 </pre>
 */
class DirectTransferGeneratorTest extends GraphRoutingTest {

  private static final Duration MAX_TRANSFER_DURATION = Duration.ofHours(1);
  private static final RouteRequest REQUEST_WITH_WALK_TRANSFER = RouteRequest.defaultValue();
  private static final RouteRequest REQUEST_WITH_BIKE_TRANSFER = RouteRequest.of()
    .withJourney(jb -> jb.withTransfer(new StreetRequest(StreetMode.BIKE)))
    .buildDefault();

  private TransitStopVertex S0, S11, S12, S13, S21, S22, S23;
  private StreetVertex V0, V11, V12, V13, V21, V22, V23;

  @Test
  public void testDirectTransfersWithoutPatterns() {
    var otpModel = model(false, false, false);
    var graph = otpModel.graph();
    var timetableRepository = otpModel.timetableRepository();
    var transferRequests = List.of(REQUEST_WITH_WALK_TRANSFER);
    graph.hasStreets = false;

    new DirectTransferGenerator(
      graph,
      timetableRepository,
      DataImportIssueStore.NOOP,
      MAX_TRANSFER_DURATION,
      transferRequests
    ).buildGraph();

    assertEquals("<Empty>", toString(timetableRepository.getAllPathTransfers()));
  }

  @Test
  public void testDirectTransfersWithPatterns() {
    var otpModel = model(true, false, false);
    var graph = otpModel.graph();
    graph.hasStreets = false;
    var timetableRepository = otpModel.timetableRepository();
    var transferRequests = List.of(REQUEST_WITH_WALK_TRANSFER);

    new DirectTransferGenerator(
      graph,
      timetableRepository,
      DataImportIssueStore.NOOP,
      MAX_TRANSFER_DURATION,
      transferRequests
    ).buildGraph();

    assertEquals(
      """
       S0 - S11, 556m
       S0 - S21, 935m
      S11 - S21, 751m
      S12 - S22, 751m
      S13 - S12, 2224m
      S13 - S22, 2347m
      S21 - S11, 751m
      S22 - S12, 751m
      S23 - S12, 2347m
      S23 - S22, 2224m""",
      toString(timetableRepository.getAllPathTransfers())
    );
  }

  @Test
  public void testDirectTransfersWithRestrictedPatterns() {
    var otpModel = model(true, true, false);
    var graph = otpModel.graph();
    graph.hasStreets = false;
    var timetableRepository = otpModel.timetableRepository();
    var transferRequests = List.of(REQUEST_WITH_WALK_TRANSFER);

    new DirectTransferGenerator(
      graph,
      timetableRepository,
      DataImportIssueStore.NOOP,
      MAX_TRANSFER_DURATION,
      transferRequests
    ).buildGraph();

    assertEquals(
      """
       S0 - S12, 2780m
       S0 - S21, 935m
      S11 - S12, 2224m
      S11 - S21, 751m
      S12 - S22, 751m
      S13 - S12, 2224m
      S13 - S22, 2347m
      S21 - S12, 2347m
      S22 - S12, 751m
      S23 - S12, 2347m
      S23 - S22, 2224m""",
      toString(timetableRepository.getAllPathTransfers())
    );
  }

  @Test
  public void testSingleRequestWithoutPatterns() {
    var transferRequests = List.of(REQUEST_WITH_WALK_TRANSFER);

    var otpModel = model(false, false, false);
    var graph = otpModel.graph();
    graph.hasStreets = true;
    var timetableRepository = otpModel.timetableRepository();

    new DirectTransferGenerator(
      graph,
      timetableRepository,
      DataImportIssueStore.NOOP,
      MAX_TRANSFER_DURATION,
      transferRequests
    ).buildGraph();

    assertEquals("<Empty>", toString(timetableRepository.getAllPathTransfers()));
  }

  @Test
  public void testSingleRequestWithPatterns() {
    var transferRequests = List.of(REQUEST_WITH_WALK_TRANSFER);

    var otpModel = model(true, false, false);
    var graph = otpModel.graph();
    graph.hasStreets = true;
    var timetableRepository = otpModel.timetableRepository();

    new DirectTransferGenerator(
      graph,
      timetableRepository,
      DataImportIssueStore.NOOP,
      MAX_TRANSFER_DURATION,
      transferRequests
    ).buildGraph();

    assertEquals(
      """
       S0 - S11, 100m
       S0 - S21, 100m
      S11 - S21, 100m""",
      toString(timetableRepository.getAllPathTransfers())
    );
  }

  @Test
  public void testMultipleRequestsWithoutPatterns() {
    var transferRequests = List.of(REQUEST_WITH_WALK_TRANSFER, REQUEST_WITH_BIKE_TRANSFER);

    var otpModel = model(false, false, false);
    var graph = otpModel.graph();
    graph.hasStreets = true;
    var timetableRepository = otpModel.timetableRepository();

    new DirectTransferGenerator(
      graph,
      timetableRepository,
      DataImportIssueStore.NOOP,
      MAX_TRANSFER_DURATION,
      transferRequests
    ).buildGraph();

    assertEquals("<Empty>", toString(timetableRepository.getAllPathTransfers()));
  }

  @Test
  public void testMultipleRequestsWithPatterns() {
    var transferRequests = List.of(REQUEST_WITH_WALK_TRANSFER, REQUEST_WITH_BIKE_TRANSFER);

    TestOtpModel model = model(true, false, false);
    var graph = model.graph();
    graph.hasStreets = true;
    var timetableRepository = model.timetableRepository();

    new DirectTransferGenerator(
      graph,
      timetableRepository,
      DataImportIssueStore.NOOP,
      MAX_TRANSFER_DURATION,
      transferRequests
    ).buildGraph();

    var walkTransfers = timetableRepository.findTransfers(StreetMode.WALK);
    var bikeTransfers = timetableRepository.findTransfers(StreetMode.BIKE);
    var carTransfers = timetableRepository.findTransfers(StreetMode.CAR);

    assertEquals(
      """
       S0 - S11, 100m
       S0 - S21, 100m
      S11 - S21, 100m""",
      toString(walkTransfers)
    );
    assertEquals(
      """
       S0 - S11, 100m
       S0 - S21, 100m
      S11 - S22, 110m""",
      toString(bikeTransfers)
    );
    assertEquals("<Empty>", toString(carTransfers));
  }

  @Test
  public void testTransferOnIsolatedStations() {
    var otpModel = model(true, false, true);
    var graph = otpModel.graph();
    graph.hasStreets = false;

    var timetableRepository = otpModel.timetableRepository();
    var transferRequests = List.of(REQUEST_WITH_WALK_TRANSFER);

    new DirectTransferGenerator(
      graph,
      timetableRepository,
      DataImportIssueStore.NOOP,
      MAX_TRANSFER_DURATION,
      transferRequests
    ).buildGraph();

    assertEquals("<Empty>", toString(timetableRepository.getAllPathTransfers()));
  }

  @Test
  public void testBikeRequestWithBikesAllowedTransfersWithIncludeEmptyRailStopsInTransfersOn() {
    OTPFeature.IncludeStopsUsedRealtimeInTransfers.testOn(() -> {
      var transferRequests = List.of(REQUEST_WITH_BIKE_TRANSFER);

      TestOtpModel model = model(true, false, false);
      var graph = model.graph();
      graph.hasStreets = true;

      var timetableRepository = model.timetableRepository();
      new DirectTransferGenerator(
        graph,
        timetableRepository,
        DataImportIssueStore.NOOP,
        MAX_TRANSFER_DURATION,
        transferRequests,
        Map.of(StreetMode.BIKE, new TransferParameters(null, null, Duration.parse("PT1H"), true))
      ).buildGraph();

      var bikeTransfers = timetableRepository.findTransfers(StreetMode.BIKE);
      assertEquals(" S0 - S21, 100m", toString(bikeTransfers));
    });
  }

  @Test
  public void testBikeRequestWithBikesAllowedTransfersWithConsiderPatternsForDirectTransfersOff() {
    OTPFeature.ConsiderPatternsForDirectTransfers.testOff(() -> {
      var transferRequests = List.of(REQUEST_WITH_BIKE_TRANSFER);

      TestOtpModel model = model(true, false, false);
      var graph = model.graph();
      graph.hasStreets = true;

      var timetableRepository = model.timetableRepository();
      new DirectTransferGenerator(
        graph,
        timetableRepository,
        DataImportIssueStore.NOOP,
        MAX_TRANSFER_DURATION,
        transferRequests,
        Map.of(StreetMode.BIKE, new TransferParameters(null, null, Duration.parse("PT1H"), true))
      ).buildGraph();

      var bikeTransfers = timetableRepository.findTransfers(StreetMode.BIKE);
      // no transfers involving S11, S12 and S13
      assertEquals(
        """
         S0 - S21, 100m
         S0 - S22, 200m
         S0 - S23, 300m
        S21 - S22, 100m
        S21 - S23, 200m
        S22 - S23, 100m""",
        toString(bikeTransfers)
      );
    });
  }

  private TestOtpModel model(
    boolean addPatterns,
    boolean withBoardingConstraint,
    boolean withNoTransfersOnStations
  ) {
    return modelOf(
      new Builder() {
        @Override
        public void build() {
          var station = stationEntity("1", s ->
            s.withTransfersNotAllowed(withNoTransfersOnStations)
          );

          S0 = stop("S0", b ->
            b
              .withCoordinate(47.495, 19.001)
              .withParentStation(station)
              .withVehicleType(TransitMode.RAIL)
          );
          S11 = stop("S11", 47.500, 19.001, station);
          S12 = stop("S12", 47.520, 19.001, station);
          S13 = stop("S13", 47.540, 19.001, station);
          S21 = stop("S21", 47.500, 19.011, station);
          S22 = stop("S22", b ->
            b
              .withCoordinate(47.520, 19.011)
              .withParentStation(station)
              .withVehicleType(TransitMode.BUS)
              .withSometimesUsedRealtime(true)
          );
          S23 = stop("S23", 47.540, 19.011, station);

          V0 = intersection("V0", 47.495, 19.000);
          V11 = intersection("V11", 47.500, 19.000);
          V12 = intersection("V12", 47.510, 19.000);
          V13 = intersection("V13", 47.520, 19.000);
          V21 = intersection("V21", 47.500, 19.010);
          V22 = intersection("V22", 47.510, 19.010);
          V23 = intersection("V23", 47.520, 19.010);

          biLink(V0, S0);
          biLink(V11, S11);
          biLink(V12, S12);
          biLink(V13, S13);
          biLink(V21, S21);
          biLink(V22, S22);
          biLink(V23, S23);

          street(V0, V11, 100, StreetTraversalPermission.ALL);
          street(V0, V12, 200, StreetTraversalPermission.ALL);
          street(V0, V21, 100, StreetTraversalPermission.ALL);
          street(V0, V22, 200, StreetTraversalPermission.ALL);

          street(V11, V12, 100, StreetTraversalPermission.PEDESTRIAN);
          street(V12, V13, 100, StreetTraversalPermission.PEDESTRIAN);
          street(V21, V22, 100, StreetTraversalPermission.PEDESTRIAN);
          street(V22, V23, 100, StreetTraversalPermission.PEDESTRIAN);
          street(V11, V21, 100, StreetTraversalPermission.PEDESTRIAN);
          street(V11, V22, 110, StreetTraversalPermission.PEDESTRIAN_AND_BICYCLE);

          if (addPatterns) {
            var agency = TimetableRepositoryForTest.agency("Agency");

            tripPattern(
              TripPattern.of(TimetableRepositoryForTest.id("TP1"))
                .withRoute(route("R1", TransitMode.BUS, agency))
                .withStopPattern(
                  new StopPattern(List.of(st(S11, !withBoardingConstraint, true), st(S12), st(S13)))
                )
                .build()
            );

            tripPattern(
              TripPattern.of(TimetableRepositoryForTest.id("TP2"))
                .withRoute(route("R2", TransitMode.BUS, agency))
                .withStopPattern(new StopPattern(List.of(st(S21), st(S22), st(S23))))
                .withScheduledTimeTableBuilder(builder ->
                  builder.addTripTimes(
                    ScheduledTripTimes.of()
                      .withTrip(
                        TimetableRepositoryForTest.trip("bikesAllowedTrip")
                          .withBikesAllowed(BikeAccess.ALLOWED)
                          .build()
                      )
                      .withDepartureTimes("00:00 01:00 02:00")
                      .build()
                  )
                )
                .build()
            );
          }
        }

        private TransitStopVertex stop(String id, double lat, double lon, Station parentStation) {
          return stop(id, b -> b.withCoordinate(lat, lon).withParentStation(parentStation));
        }
      }
    );
  }

  private String toString(Collection<PathTransfer> transfers) {
    if (transfers.isEmpty()) {
      return "<Empty>";
    }
    return transfers
      .stream()
      .map(tx ->
        "%3s - %3s, %dm".formatted(
            tx.from.getName(),
            tx.to.getName(),
            Math.round(tx.getDistanceMeters())
          )
      )
      .sorted()
      .collect(Collectors.joining("\n"));
  }

  private void assertTransfers(
    Collection<PathTransfer> allPathTransfers,
    TransferDescriptor... transfers
  ) {
    var matchedTransfers = new HashSet<PathTransfer>();
    var assertions = Stream.concat(
      Arrays.stream(transfers).map(td -> td.matcher(allPathTransfers, matchedTransfers)),
      Stream.of(allTransfersMatched(allPathTransfers, matchedTransfers))
    );

    assertAll(assertions);
  }

  private Executable allTransfersMatched(
    Collection<PathTransfer> transfersByStop,
    Set<PathTransfer> matchedTransfers
  ) {
    return () -> {
      var missingTransfers = new HashSet<>(transfersByStop);
      missingTransfers.removeAll(matchedTransfers);

      assertEquals(Set.of(), missingTransfers, "All transfers matched");
    };
  }

  private TransferDescriptor tr(
    StopResolver resolver,
    TransitStopVertex from,
    double distance,
    TransitStopVertex to
  ) {
    return new TransferDescriptor(
      resolver.getStop(from.getId()),
      distance,
      resolver.getStop(to.getId())
    );
  }

  private TransferDescriptor tr(
    StopResolver resolver,
    TransitStopVertex from,
    double distance,
    List<StreetVertex> vertices,
    TransitStopVertex to
  ) {
    return new TransferDescriptor(resolver, from, distance, vertices, to);
  }

  private static class TransferDescriptor {

    private final StopLocation from;
    private final StopLocation to;
    private final Double distanceMeters;
    private final List<StreetVertex> vertices;

    public TransferDescriptor(RegularStop from, Double distanceMeters, RegularStop to) {
      this.from = from;
      this.distanceMeters = distanceMeters;
      this.vertices = null;
      this.to = to;
    }

    public TransferDescriptor(
      StopResolver resolver,
      TransitStopVertex from,
      Double distanceMeters,
      List<StreetVertex> vertices,
      TransitStopVertex to
    ) {
      this.from = resolver.getStop(from.getId());
      this.distanceMeters = distanceMeters;
      this.vertices = vertices;
      this.to = resolver.getStop(to.getId());
    }

    @Override
    public String toString() {
      return ToStringBuilder.of(getClass())
        .addObj("from", from)
        .addObj("to", to)
        .addNum("distanceMeters", distanceMeters)
        .addCol("vertices", vertices)
        .toString();
    }

    boolean matches(PathTransfer transfer) {
      if (!Objects.equals(from, transfer.from) || !Objects.equals(to, transfer.to)) {
        return false;
      }

      if (vertices == null) {
        return distanceMeters == transfer.getDistanceMeters() && transfer.getEdges() == null;
      } else {
        var transferVertices = transfer
          .getEdges()
          .stream()
          .map(Edge::getToVertex)
          .filter(StreetVertex.class::isInstance)
          .toList();

        return (
          distanceMeters == transfer.getDistanceMeters() &&
          Objects.equals(vertices, transferVertices)
        );
      }
    }

    private Executable matcher(
      Collection<PathTransfer> transfersByStop,
      Set<PathTransfer> matchedTransfers
    ) {
      return () -> {
        var matched = transfersByStop.stream().filter(this::matches).findFirst();

        if (matched.isPresent()) {
          assertTrue(true, "Found transfer for " + this);
          matchedTransfers.add(matched.get());
        } else {
          fail("Missing transfer for " + this);
        }
      };
    }
  }
}
