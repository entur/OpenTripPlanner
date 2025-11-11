package org.opentripplanner.graph_builder.module.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.graph_builder.module.TransferParameters;
import org.opentripplanner.model.PathTransfer;
import org.opentripplanner.routing.algorithm.GraphRoutingTest;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.routing.api.request.request.StreetRequest;
import org.opentripplanner.street.model.StreetTraversalPermission;
import org.opentripplanner.street.model.vertex.StreetVertex;
import org.opentripplanner.street.model.vertex.TransitStopVertex;
import org.opentripplanner.transit.model._data.TimetableRepositoryForTest;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.network.BikeAccess;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.Station;
import org.opentripplanner.transit.model.timetable.ScheduledTripTimes;
import org.opentripplanner.transit.service.TimetableRepository;

/**
 * This test uses the following graph/network for testing the DirectTransfer generation. The
 * fokus is on the filtering of the transfers, not on testing that the NearBySearch return the
 * correct set of nearby stops.
 * <p>
 * <img src=DirectTransferGeneratorTest.drawio.png />
 */
class DirectTransferGeneratorTest {

  private static final Duration MAX_TRANSFER_DURATION = Duration.ofHours(1);
  private static final RouteRequest REQUEST_WITH_WALK_TRANSFER = RouteRequest.defaultValue();
  private static final RouteRequest REQUEST_WITH_BIKE_TRANSFER = RouteRequest.of()
    .withJourney(jb -> jb.withTransfer(new StreetRequest(StreetMode.BIKE)))
    .buildDefault();
  private static final TransferParameters TX_BIKES_ALLOWED_1H = new TransferParameters(
    null,
    null,
    Duration.parse("PT1H"),
    true
  );

  @Test
  public void testStraightLineTransfersWithNoPatterns() {
    // There is no trip-patterns to transfer between; Hence empty.
    var repository = testData().withTransferRequests(REQUEST_WITH_WALK_TRANSFER).build();
    assertEquals("<Empty>", toString(repository.getAllPathTransfers()));
  }

  @Test
  public void testStraightLineTransfersWithoutPatternsPruning() {
    OTPFeature.ConsiderPatternsForDirectTransfers.testOff(() -> {
      var repository = testData().withTransferRequests(REQUEST_WITH_WALK_TRANSFER).build();
      // S0 <-> S23 is too fare, not found in neardby search
      assertEquals(
        """
         S0 - S11, 1668m
         S0 - S12, 3892m
         S0 - S21, 1829m
         S0 - S22, 3964m
        S11 -  S0, 1668m
        S11 - S12, 2224m
        S11 - S13, 4448m
        S11 - S21, 751m
        S11 - S22, 2347m
        S11 - S23, 4511m
        S12 -  S0, 3892m
        S12 - S11, 2224m
        S12 - S13, 2224m
        S12 - S21, 2347m
        S12 - S22, 751m
        S12 - S23, 2347m
        S13 - S11, 4448m
        S13 - S12, 2224m
        S13 - S21, 4511m
        S13 - S22, 2347m
        S13 - S23, 751m
        S21 -  S0, 1829m
        S21 - S11, 751m
        S21 - S12, 2347m
        S21 - S13, 4511m
        S21 - S22, 2224m
        S21 - S23, 4448m
        S22 -  S0, 3964m
        S22 - S11, 2347m
        S22 - S12, 751m
        S22 - S13, 2347m
        S22 - S21, 2224m
        S22 - S23, 2224m
        S23 - S11, 4511m
        S23 - S12, 2347m
        S23 - S13, 751m
        S23 - S21, 4448m
        S23 - S22, 2224m""",
        toString(repository.getAllPathTransfers())
      );
    });
  }

  @Test
  public void testStraightLineTransfersWithPatternsPruning() {
    var repository = testData()
      .withPatterns()
      .withTransferRequests(REQUEST_WITH_WALK_TRANSFER)
      .build();

    // Exactly one transfer is expected from each stop to the closest place to board all
    // patterns, including the same pattern used by the stop (E.g. S12 - S11).
    assertEquals(
      """
       S0 - S11, 1668m
       S0 - S21, 1829m
      S11 -  S0, 1668m
      S11 - S21, 751m
      S12 -  S0, 3892m
      S12 - S11, 2224m
      S12 - S22, 751m
      S13 - S11, 4448m
      S13 - S22, 2347m
      S21 -  S0, 1829m
      S21 - S11, 751m
      S22 -  S0, 3964m
      S22 - S11, 2347m
      S23 - S11, 4511m
      S23 - S22, 2224m""",
      toString(repository.getAllPathTransfers())
    );
  }

  @Test
  public void testStraightLineTransfersWithBoardingRestrictions() {
    var repository = testData()
      .withPatterns()
      .withNoBoardingForR1AtStop11()
      .withTransferRequests(REQUEST_WITH_WALK_TRANSFER)
      .build();

    assertEquals(
      // * - S11 is not allowed, because of boarding constraints
      """
       S0 - S21, 1829m
      S11 -  S0, 1668m
      S11 - S21, 751m
      S12 -  S0, 3892m
      S12 - S22, 751m
      S13 - S22, 2347m
      S21 -  S0, 1829m
      S22 -  S0, 3964m
      S23 - S22, 2224m""",
      toString(repository.getAllPathTransfers())
    );
  }

  @Test
  public void testStreetTransfersWithNoPatterns() {
    // There is no trip-patterns to transfer between; Hence empty.
    var repository = testData()
      .withStreetGraph()
      .withTransferRequests(REQUEST_WITH_WALK_TRANSFER)
      .build();

    assertEquals("<Empty>", toString(repository.getAllPathTransfers()));
  }

  @Test
  public void testStreetTransfersWithoutPatternsPruning() {
    OTPFeature.ConsiderPatternsForDirectTransfers.testOff(() -> {
      var repository = testData()
        .withStreetGraph()
        .withTransferRequests(REQUEST_WITH_WALK_TRANSFER)
        .build();

      assertEquals(
        """
         S0 - S11, 100m
         S0 - S12, 200m
         S0 - S21, 100m
         S0 - S22, 200m
         S0 - S23, 300m
        S11 - S12, 100m
        S11 - S21, 100m
        S11 - S22, 110m
        S11 - S23, 210m
        S12 - S22, 110m
        S12 - S23, 210m
        S13 - S12, 100m
        S13 - S22, 210m
        S13 - S23, 310m
        S22 - S23, 100m""",
        toString(repository.getAllPathTransfers())
      );
    });
  }

  @Test
  public void testStreetTransfersWithPatterns() {
    var repository = testData()
      .withPatterns()
      .withStreetGraph()
      .withTransferRequests(REQUEST_WITH_WALK_TRANSFER)
      .build();

    assertEquals(
      """
       S0 - S11, 100m
       S0 - S21, 100m
      S11 - S21, 100m
      S12 - S22, 110m
      S13 - S22, 210m""",
      toString(repository.getAllPathTransfers())
    );
  }

  @Test
  public void testStreetTransfersWithPatternsIncludeRealTimeUsedStops() {
    OTPFeature.IncludeStopsUsedRealtimeInTransfers.testOn(() -> {
      var repository = testData()
        .withPatterns()
        .withStreetGraph()
        .withTransferRequests(REQUEST_WITH_WALK_TRANSFER)
        .build();

      assertEquals(
        """
         S0 - S11, 100m
         S0 - S21, 100m
         S0 - S23, 300m
        S11 - S21, 100m
        S11 - S23, 210m
        S12 - S22, 110m
        S12 - S23, 210m
        S13 - S22, 210m
        S13 - S23, 310m
        S22 - S23, 100m""",
        toString(repository.getAllPathTransfers())
      );
    });
  }

  @Test
  public void testStreetTransfersWithMultipleRequestsWithPatterns() {
    var repository = testData()
      .withPatterns()
      .withStreetGraph()
      .withTransferRequests(REQUEST_WITH_WALK_TRANSFER, REQUEST_WITH_BIKE_TRANSFER)
      .build();

    var walkTransfers = repository.findTransfers(StreetMode.WALK);
    var bikeTransfers = repository.findTransfers(StreetMode.BIKE);
    var carTransfers = repository.findTransfers(StreetMode.CAR);

    assertEquals(
      """
       S0 - S11, 100m
       S0 - S21, 100m
      S11 - S21, 100m
      S12 - S22, 110m
      S13 - S22, 210m""",
      toString(walkTransfers)
    );

    // Transfer S11-S21 is dominated by the S11-S22 with lower cost; Hence missing. Some of the
    // edges used are not allowed for bicycles, but you can walk the bike so they are included here
    // with a higher cost.
    assertEquals(
      """
       S0 - S11, 100m
       S0 - S21, 100m
      S11 - S22, 110m
      S12 - S22, 110m
      S13 - S22, 210m""",
      toString(bikeTransfers)
    );
    assertEquals("<Empty>", toString(carTransfers));
  }

  @Test
  public void testStreetTransfersWithStationWithTransfersNotAllowed() {
    var repository = testData()
      .withPatterns()
      .withStreetGraph()
      .withNoTransfersOnStationA()
      .withTransferRequests(REQUEST_WITH_WALK_TRANSFER)
      .build();

    assertEquals(
      """
       S0 - S22, 200m
      S12 - S22, 110m
      S13 - S22, 210m""",
      toString(repository.getAllPathTransfers())
    );
  }

  @Test
  public void testBikeRequestWithBikesAllowedTransfersWithIncludeEmptyRailStopsInTransfersOn() {
    OTPFeature.IncludeStopsUsedRealtimeInTransfers.testOn(() -> {
      var repository = testData()
        .withPatterns()
        .withStreetGraph()
        .withTransferRequests(REQUEST_WITH_BIKE_TRANSFER)
        .addTransferParameters(StreetMode.BIKE, TX_BIKES_ALLOWED_1H)
        .build();

      var bikeTransfers = repository.findTransfers(StreetMode.BIKE);
      assertEquals(
        """
        S13 - S22, 210m
        S13 - S23, 310m
        S22 - S23, 100m""",
        toString(bikeTransfers)
      );
    });
  }

  @Test
  public void testBikeRequestWithBikesAllowedTransfersWithConsiderPatternsForDirectTransfersOff() {
    OTPFeature.ConsiderPatternsForDirectTransfers.testOff(() -> {
      var repository = testData()
        .withPatterns()
        .withStreetGraph()
        .withTransferRequests(REQUEST_WITH_BIKE_TRANSFER)
        .addTransferParameters(StreetMode.BIKE, TX_BIKES_ALLOWED_1H)
        .build();

      var bikeTransfers = repository.findTransfers(StreetMode.BIKE);
      assertEquals(
        """
        S13 - S22, 210m
        S13 - S23, 310m
        S22 - S23, 100m""",
        toString(bikeTransfers)
      );
    });
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

  private TestData testData() {
    return new TestData();
  }

  private static class TestData extends GraphRoutingTest {

    private boolean addPatterns = false;
    private boolean withBoardingConstraint = false;
    private boolean noTransfersOnStationA = false;
    private boolean graphHasStreets = false;
    private final List<RouteRequest> transferRequests = new ArrayList<>();
    private final Map<StreetMode, TransferParameters> transferParametersForMode = new HashMap<>();

    public TestData withPatterns() {
      this.addPatterns = true;
      return this;
    }

    public TestData withNoBoardingForR1AtStop11() {
      this.withBoardingConstraint = true;
      return this;
    }

    public TestData withNoTransfersOnStationA() {
      this.noTransfersOnStationA = true;
      return this;
    }

    public TestData withStreetGraph() {
      this.graphHasStreets = true;
      return this;
    }

    public TestData withTransferRequests(RouteRequest... request) {
      this.transferRequests.addAll(Arrays.asList(request));
      return this;
    }

    public TestData addTransferParameters(StreetMode mode, TransferParameters value) {
      this.transferParametersForMode.put(mode, value);
      return this;
    }

    public TimetableRepository build() {
      var model = modelOf(new Builder());
      model.graph().hasStreets = graphHasStreets;

      new DirectTransferGenerator(
        model.graph(),
        model.timetableRepository(),
        DataImportIssueStore.NOOP,
        MAX_TRANSFER_DURATION,
        transferRequests,
        transferParametersForMode
      ).buildGraph();

      return model.timetableRepository();
    }

    private class Builder extends GraphRoutingTest.Builder {

      @Override
      public void build() {
        var stationA = stationEntity("1", s -> s.withTransfersNotAllowed(noTransfersOnStationA));
        TransitStopVertex S0, S_FAR_AWAY, S11, S12, S13, S21, S22, S23;
        StreetVertex V0, V11, V12, V13, V21, V22, V23;

        S0 = stop("S0", b -> b.withCoordinate(47.485, 19.001).withVehicleType(TransitMode.RAIL));
        S_FAR_AWAY = stop("FarAway", 55.0, 30.0);
        S11 = stop("S11", 47.500, 19.001, stationA);
        S12 = stop("S12", 47.520, 19.001);
        S13 = stop("S13", b -> b.withCoordinate(47.540, 19.001).withSometimesUsedRealtime(true));
        S21 = stop("S21", 47.500, 19.011, stationA);
        S22 = stop("S22", b ->
          b
            .withCoordinate(47.520, 19.011)
            .withVehicleType(TransitMode.BUS)
            .withSometimesUsedRealtime(true)
        );
        S23 = stop("S23", b -> b.withCoordinate(47.540, 19.011).withSometimesUsedRealtime(true));

        V0 = intersection("V0", 47.485, 19.000);
        V11 = intersection("V11", 47.500, 19.000);
        V12 = intersection("V12", 47.520, 19.000);
        V13 = intersection("V13", 47.540, 19.000);
        V21 = intersection("V21", 47.500, 19.010);
        V22 = intersection("V22", 47.520, 19.010);
        V23 = intersection("V23", 47.540, 19.010);

        biLink(V0, S0);
        biLink(V11, S11);
        biLink(V12, S12);
        biLink(V13, S13);
        biLink(V21, S21);
        biLink(V22, S22);
        biLink(V23, S23);

        street(V0, V11, 100, StreetTraversalPermission.ALL);
        street(V0, V21, 100, StreetTraversalPermission.ALL);
        street(V0, V22, 200, StreetTraversalPermission.ALL);

        street(V11, V12, 100, StreetTraversalPermission.PEDESTRIAN);
        street(V11, V21, 100, StreetTraversalPermission.PEDESTRIAN);
        street(V11, V22, 110, StreetTraversalPermission.PEDESTRIAN_AND_BICYCLE);
        street(V12, V22, 110, StreetTraversalPermission.PEDESTRIAN);
        street(V13, V12, 100, StreetTraversalPermission.PEDESTRIAN);
        street(V22, V23, 100, StreetTraversalPermission.PEDESTRIAN);

        if (addPatterns) {
          var agency = TimetableRepositoryForTest.agency("Agency");

          tripPattern(
            TripPattern.of(TimetableRepositoryForTest.id("TP0"))
              .withRoute(route("R0", TransitMode.RAIL, agency))
              .withStopPattern(new StopPattern(List.of(st(S0), st(S_FAR_AWAY))))
              .build()
          );
          tripPattern(
            TripPattern.of(TimetableRepositoryForTest.id("TP1"))
              .withRoute(route("R1", TransitMode.BUS, agency))
              .withStopPattern(
                new StopPattern(List.of(st(S11, !withBoardingConstraint, true), st(S12)))
              )
              .build()
          );
          tripPattern(
            TripPattern.of(TimetableRepositoryForTest.id("TP2"))
              .withRoute(route("R2", TransitMode.BUS, agency))
              .withStopPattern(new StopPattern(List.of(st(S21), st(S22), st(S_FAR_AWAY))))
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
  }
}
