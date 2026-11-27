package org.opentripplanner.raptor.rangeraptor.multicriteria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.raptor._data.transit.TestAccessEgress;
import org.opentripplanner.raptor._data.transit.TestTransfer;
import org.opentripplanner.raptor._data.transit.TestTripPattern;
import org.opentripplanner.raptor._data.transit.TestTripSchedule;
import org.opentripplanner.raptor.api.view.PathLegType;
import org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop.McStopArrival;
import org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop.McStopArrivalFactory;
import org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop.StopArrivalFactoryC1;
import org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop.StopArrivalFactoryC2;
import org.opentripplanner.raptor.rangeraptor.multicriteria.heuristic.HeuristicsProvider;
import org.opentripplanner.raptor.rangeraptor.multicriteria.ride.AbstractPatternRide;
import org.opentripplanner.raptor.rangeraptor.multicriteria.ride.PatternRideC1;
import org.opentripplanner.raptor.rangeraptor.multicriteria.ride.PatternRideC2;
import org.opentripplanner.utils.time.TimeUtils;

class StopArrivalCandidatesTest {

  private static final int STOP_A = 7;
  private static final int STOP_B = 4;
  private static final int STOP_C = 12;
  private static final int ANY = 17543;
  private static final int ORIGIN_DEPARTURE_TIME = TimeUtils.time("10:00");
  private static final int TRANSIT_ARRIVAL_TIME = TimeUtils.time("10:11");
  private static final int TRANSIT_C1 = 63000;
  private static final int RIDE_C2 = 5;
  private static final int TRANSFER_DURATION = 30;
  private static final TestAccessEgress ACCESS = TestAccessEgress.walk(STOP_A, 30);
  private static final TestTransfer TRANSFER = TestTransfer.transfer(STOP_C, TRANSFER_DURATION);

  private static final TestTripSchedule TRIP = TestTripSchedule.schedule("10:03 10:10")
    .pattern(TestTripPattern.pattern("Line A", STOP_A, STOP_B))
    .build();

  private static final HeuristicsProvider<TestTripSchedule> HEURISTICS = new HeuristicsProvider<>();

  static List<Arguments> factories() {
    return List.of(
      Arguments.of("c1", new StopArrivalFactoryC1<TestTripSchedule>()),
      Arguments.of("c1 and c2", new StopArrivalFactoryC2<TestTripSchedule>())
    );
  }

  /**
   * The values of a candidate are used to accept or reject the arrival before it is created, so
   * they must be the values of the arrival created.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("factories")
  void theValuesOfACandidateAreThoseOfTheArrivalCreated(
    String name,
    McStopArrivalFactory<TestTripSchedule> factory
  ) {
    var access = factory.createAccessStopArrival(ORIGIN_DEPARTURE_TIME, ACCESS);
    var ride = ride(factory, access);
    var transit = factory.createTransitStopArrival(ride, STOP_B, TRANSIT_ARRIVAL_TIME, TRANSIT_C1);
    int transferArrivalTime = TRANSIT_ARRIVAL_TIME + TRANSFER_DURATION;

    // An arrival by transit, not created yet, after an arrival that exists
    var transitArrivals = StopArrivalCandidates.ofTransitArrivals(factory, HEURISTICS, null);
    transitArrivals.addArrival(access);
    transitArrivals.addTransitArrival(ride, STOP_B, TRANSIT_ARRIVAL_TIME, TRANSIT_C1);

    assertEquals(2, transitArrivals.size());
    assertSame(access, transitArrivals.arrival(0));
    assertNull(transitArrivals.arrival(1));
    var transitCreated = transitArrivals.createArrival(1);
    assertEquals(PathLegType.TRANSIT, transitCreated.arrivedBy());
    assertSame(access, transitCreated.previous());
    assertEquals(TRANSIT_ARRIVAL_TIME, transitCreated.arrivalTime());
    assertEquals(TRANSIT_C1, transitCreated.c1());
    assertEquals(transit.c2(), transitCreated.c2());
    assertValuesAreThoseOf(transitCreated, transitArrivals, 1);

    // An arrival by transfer, not created yet, before an arrival that exists
    var transferArrivals = StopArrivalCandidates.ofTransferArrivals(factory, HEURISTICS, null);
    transferArrivals.addTransferArrival(transit, TRANSFER, transferArrivalTime);
    transferArrivals.addArrival(access);

    assertEquals(2, transferArrivals.size());
    assertNull(transferArrivals.arrival(0));
    var transferCreated = transferArrivals.createArrival(0);
    assertEquals(PathLegType.TRANSFER, transferCreated.arrivedBy());
    assertSame(transit, transferCreated.previous());
    assertSame(TRANSFER, transferCreated.transfer());
    assertEquals(transferArrivalTime, transferCreated.arrivalTime());
    assertValuesAreThoseOf(transferCreated, transferArrivals, 0);
    assertSame(access, transferArrivals.arrival(1));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("factories")
  void growsAndIsEmptyAfterClear(String name, McStopArrivalFactory<TestTripSchedule> factory) {
    var subject = StopArrivalCandidates.ofTransitArrivals(factory, HEURISTICS, null);
    var access = factory.createAccessStopArrival(ORIGIN_DEPARTURE_TIME, ACCESS);
    var ride = ride(factory, access);
    int n = 1000;

    for (int i = 0; i < n; ++i) {
      subject.addTransitArrival(ride, STOP_B, TRANSIT_ARRIVAL_TIME + i, TRANSIT_C1 + i);
    }
    assertEquals(n, subject.size());
    for (int i = 0; i < n; ++i) {
      assertEquals(STOP_B, subject.stop(i));
      assertEquals(TRANSIT_ARRIVAL_TIME + i, subject.arrivalTime(i));
      assertEquals(TRANSIT_C1 + i, subject.c1(i));
      assertValuesAreThoseOf(subject.createArrival(i), subject, i);
    }

    subject.clear();
    assertEquals(0, subject.size());

    subject.addArrival(access);
    assertEquals(1, subject.size());
    assertSame(access, subject.arrival(0));
  }

  private static void assertValuesAreThoseOf(
    McStopArrival<TestTripSchedule> arrival,
    StopArrivalCandidates<TestTripSchedule> subject,
    int index
  ) {
    assertEquals(arrival.stop(), subject.stop(index));
    assertEquals(arrival.arrivalTime(), subject.arrivalTime(index));
    assertEquals(arrival.round(), subject.round(index));
    assertEquals(arrival.c1(), subject.c1(index));
    assertEquals(arrival.c2(), subject.c2(index));
    assertEquals(arrival.arrivedOnBoard(), subject.arrivedOnBoard());
  }

  private static AbstractPatternRide<TestTripSchedule> ride(
    McStopArrivalFactory<TestTripSchedule> factory,
    McStopArrival<TestTripSchedule> prevArrival
  ) {
    if (factory instanceof StopArrivalFactoryC2) {
      return new PatternRideC2<>(
        prevArrival,
        0,
        TRIP.departure(0),
        ANY,
        ANY,
        RIDE_C2,
        TRIP.tripSortIndex(),
        TRIP
      );
    }
    return new PatternRideC1<>(
      prevArrival,
      0,
      TRIP.departure(0),
      ANY,
      ANY,
      TRIP.tripSortIndex(),
      TRIP
    );
  }
}
