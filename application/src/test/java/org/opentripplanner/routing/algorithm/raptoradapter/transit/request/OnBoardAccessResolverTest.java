package org.opentripplanner.routing.algorithm.raptoradapter.transit.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opentripplanner.transit.model._data.TimetableRepositoryForTest.id;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.model.StopTime;
import org.opentripplanner.routing.algorithm.raptoradapter.router.OnBoardAccessResolver;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripPatternForDate;
import org.opentripplanner.routing.api.request.TripLocation;
import org.opentripplanner.routing.api.request.TripOnDateReference;
import org.opentripplanner.transit.model._data.TimetableRepositoryForTest;
import org.opentripplanner.transit.model.framework.Deduplicator;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.RoutingTripPattern;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimesFactory;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.transit.service.TransitService;

class OnBoardAccessResolverTest {

  private static final TimetableRepositoryForTest TEST_MODEL = TimetableRepositoryForTest.of();
  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 11, 1);
  private static final Route ROUTE = TimetableRepositoryForTest.route("R1").build();

  private static final RegularStop STOP_A = TEST_MODEL.stop("A", 60.0, 10.0).build();
  private static final RegularStop STOP_B = TEST_MODEL.stop("B", 60.1, 10.1).build();
  private static final RegularStop STOP_C = TEST_MODEL.stop("C", 60.2, 10.2).build();

  private static final int DEP_TIME_A = 10 * 3600;
  private static final int DEP_TIME_B = 10 * 3600 + 5 * 60;
  private static final int DEP_TIME_C = 10 * 3600 + 10 * 60;

  private static Trip TRIP;
  private static RoutingTripPattern ROUTING_PATTERN;
  private static TransitService transitService;
  private static TripPatternForDates tripPatternForDates;

  @BeforeAll
  static void setup() {
    TRIP = TimetableRepositoryForTest.trip("T1").withRoute(ROUTE).build();

    var stopTimeA = new StopTime();
    stopTimeA.setStop(STOP_A);
    stopTimeA.setArrivalTime(DEP_TIME_A);
    stopTimeA.setDepartureTime(DEP_TIME_A);
    stopTimeA.setStopSequence(0);

    var stopTimeB = new StopTime();
    stopTimeB.setStop(STOP_B);
    stopTimeB.setArrivalTime(DEP_TIME_B);
    stopTimeB.setDepartureTime(DEP_TIME_B);
    stopTimeB.setStopSequence(1);

    var stopTimeC = new StopTime();
    stopTimeC.setStop(STOP_C);
    stopTimeC.setArrivalTime(DEP_TIME_C);
    stopTimeC.setDepartureTime(DEP_TIME_C);
    stopTimeC.setStopSequence(2);

    StopPattern stopPattern = new StopPattern(List.of(stopTimeA, stopTimeB, stopTimeC));

    var tripTimes = TripTimesFactory.tripTimes(
      TRIP,
      List.of(stopTimeA, stopTimeB, stopTimeC),
      new Deduplicator()
    );

    TripPattern tripPattern = TripPattern.of(id("P1"))
      .withRoute(ROUTE)
      .withStopPattern(stopPattern)
      .withScheduledTimeTableBuilder(builder -> builder.addTripTimes(tripTimes))
      .build();

    ROUTING_PATTERN = tripPattern.getRoutingTripPattern();

    var siteRepository = TEST_MODEL.siteRepositoryBuilder()
      .withRegularStop(STOP_A)
      .withRegularStop(STOP_B)
      .withRegularStop(STOP_C)
      .build();

    var timetableRepository = new TimetableRepository(siteRepository);
    timetableRepository.addTripPattern(tripPattern.getId(), tripPattern);
    timetableRepository.index();

    transitService = new DefaultTransitService(timetableRepository);

    var boardingAndAlighting = new BitSet(3);
    boardingAndAlighting.set(0);
    boardingAndAlighting.set(1);
    boardingAndAlighting.set(2);

    var tpfd = new TripPatternForDate(ROUTING_PATTERN, List.of(tripTimes), List.of(), SERVICE_DATE);

    tripPatternForDates = new TripPatternForDates(
      ROUTING_PATTERN,
      new TripPatternForDate[] { tpfd },
      new int[] { 0 },
      boardingAndAlighting,
      boardingAndAlighting,
      0
    );
  }

  private static long toEpochMillis(int secondsSinceMidnight) {
    long midnightEpochSecond = SERVICE_DATE.atStartOfDay(ZoneId.of("GMT")).toEpochSecond();
    return (midnightEpochSecond + secondsSinceMidnight) * 1000L;
  }

  private static List<TripPatternForDates> buildPatternIndex() {
    int routeIndex = ROUTING_PATTERN.patternIndex();
    TripPatternForDates[] array = new TripPatternForDates[routeIndex + 1];
    array[routeIndex] = tripPatternForDates;
    return Arrays.asList(array);
  }

  @Test
  void resolveSimpleOnBoardAccess() {
    var resolver = new OnBoardAccessResolver(transitService);
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(TRIP.getId(), SERVICE_DATE),
      STOP_B.getId()
    );

    var patternIndex = buildPatternIndex();
    var result = resolver.resolve(tripLocation, patternIndex);

    assertEquals(ROUTING_PATTERN.patternIndex(), result.routeIndex());
    assertEquals(0, result.tripScheduleIndex());
    assertEquals(1, result.stopPositionInPattern());
    assertEquals(ROUTING_PATTERN.stopIndex(1), result.stop());
  }

  @Test
  void resolveFirstStop() {
    var resolver = new OnBoardAccessResolver(transitService);
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(TRIP.getId(), SERVICE_DATE),
      STOP_A.getId()
    );

    var patternIndex = buildPatternIndex();
    var result = resolver.resolve(tripLocation, patternIndex);

    assertEquals(0, result.stopPositionInPattern());
    assertEquals(ROUTING_PATTERN.stopIndex(0), result.stop());
  }

  @Test
  void resolveLastStop() {
    var resolver = new OnBoardAccessResolver(transitService);
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(TRIP.getId(), SERVICE_DATE),
      STOP_C.getId()
    );

    var patternIndex = buildPatternIndex();
    var result = resolver.resolve(tripLocation, patternIndex);

    assertEquals(2, result.stopPositionInPattern());
    assertEquals(ROUTING_PATTERN.stopIndex(2), result.stop());
  }

  @Test
  void throwsOnUnknownTrip() {
    var resolver = new OnBoardAccessResolver(transitService);
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(id("unknown"), SERVICE_DATE),
      STOP_A.getId()
    );

    var patternIndex = buildPatternIndex();
    assertThrows(IllegalArgumentException.class, () ->
      resolver.resolve(tripLocation, patternIndex)
    );
  }

  @Test
  void throwsOnUnknownStop() {
    var resolver = new OnBoardAccessResolver(transitService);
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(TRIP.getId(), SERVICE_DATE),
      id("unknown-stop")
    );

    var patternIndex = buildPatternIndex();
    assertThrows(IllegalArgumentException.class, () ->
      resolver.resolve(tripLocation, patternIndex)
    );
  }

  @Test
  void resolveOnBoardAccessWithZeroCost() {
    var resolver = new OnBoardAccessResolver(transitService);
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(TRIP.getId(), SERVICE_DATE),
      STOP_B.getId()
    );

    var patternIndex = buildPatternIndex();
    var result = resolver.resolve(tripLocation, patternIndex);

    assertEquals(0, result.c1());
  }

  @Test
  void resolveWithScheduledDepartureTimeOnUniqueStop() {
    var resolver = new OnBoardAccessResolver(transitService);
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(TRIP.getId(), SERVICE_DATE),
      STOP_B.getId(),
      toEpochMillis(DEP_TIME_B)
    );

    var patternIndex = buildPatternIndex();
    var result = resolver.resolve(tripLocation, patternIndex);

    assertEquals(ROUTING_PATTERN.patternIndex(), result.routeIndex());
    assertEquals(0, result.tripScheduleIndex());
    assertEquals(1, result.stopPositionInPattern());
    assertEquals(ROUTING_PATTERN.stopIndex(1), result.stop());
  }

  @Test
  void throwsOnRingLineWithStopId() {
    var ringRoute = TimetableRepositoryForTest.route("R2").build();
    var ringTrip = TimetableRepositoryForTest.trip("T2").withRoute(ringRoute).build();

    var st1 = new StopTime();
    st1.setStop(STOP_A);
    st1.setArrivalTime(10 * 3600);
    st1.setDepartureTime(10 * 3600);
    st1.setStopSequence(0);

    var st2 = new StopTime();
    st2.setStop(STOP_B);
    st2.setArrivalTime(10 * 3600 + 5 * 60);
    st2.setDepartureTime(10 * 3600 + 5 * 60);
    st2.setStopSequence(1);

    var st3 = new StopTime();
    st3.setStop(STOP_A);
    st3.setArrivalTime(10 * 3600 + 15 * 60);
    st3.setDepartureTime(10 * 3600 + 15 * 60);
    st3.setStopSequence(2);

    StopPattern ringStopPattern = new StopPattern(List.of(st1, st2, st3));

    var ringTripTimes = TripTimesFactory.tripTimes(
      ringTrip,
      List.of(st1, st2, st3),
      new Deduplicator()
    );

    TripPattern ringTripPattern = TripPattern.of(id("P2"))
      .withRoute(ringRoute)
      .withStopPattern(ringStopPattern)
      .withScheduledTimeTableBuilder(builder -> builder.addTripTimes(ringTripTimes))
      .build();

    var ringRoutingPattern = ringTripPattern.getRoutingTripPattern();

    var ringTimetableRepo = new TimetableRepository(
      TEST_MODEL.siteRepositoryBuilder().withRegularStop(STOP_A).withRegularStop(STOP_B).build()
    );
    ringTimetableRepo.addTripPattern(ringTripPattern.getId(), ringTripPattern);
    ringTimetableRepo.index();

    var ringTransitService = new DefaultTransitService(ringTimetableRepo);

    var boardingAndAlighting = new BitSet(3);
    boardingAndAlighting.set(0);
    boardingAndAlighting.set(1);
    boardingAndAlighting.set(2);

    var tpfd = new TripPatternForDate(
      ringRoutingPattern,
      List.of(ringTripTimes),
      List.of(),
      SERVICE_DATE
    );

    var ringTpfds = new TripPatternForDates(
      ringRoutingPattern,
      new TripPatternForDate[] { tpfd },
      new int[] { 0 },
      boardingAndAlighting,
      boardingAndAlighting,
      0
    );

    int routeIndex = ringRoutingPattern.patternIndex();
    TripPatternForDates[] array = new TripPatternForDates[routeIndex + 1];
    array[routeIndex] = ringTpfds;
    var patternIndex = Arrays.asList(array);

    var resolver = new OnBoardAccessResolver(ringTransitService);

    // Using stopId alone for a stop that appears twice should throw
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(ringTrip.getId(), SERVICE_DATE),
      STOP_A.getId()
    );

    assertThrows(IllegalArgumentException.class, () ->
      resolver.resolve(tripLocation, patternIndex)
    );
  }

  @Test
  void resolveRingLineWithScheduledDepartureTime() {
    var ringRoute = TimetableRepositoryForTest.route("R3").build();
    var ringTrip = TimetableRepositoryForTest.trip("T3").withRoute(ringRoute).build();

    var st1 = new StopTime();
    st1.setStop(STOP_A);
    st1.setArrivalTime(10 * 3600);
    st1.setDepartureTime(10 * 3600);
    st1.setStopSequence(0);

    var st2 = new StopTime();
    st2.setStop(STOP_B);
    st2.setArrivalTime(10 * 3600 + 5 * 60);
    st2.setDepartureTime(10 * 3600 + 5 * 60);
    st2.setStopSequence(1);

    var st3 = new StopTime();
    st3.setStop(STOP_A);
    st3.setArrivalTime(10 * 3600 + 15 * 60);
    st3.setDepartureTime(10 * 3600 + 15 * 60);
    st3.setStopSequence(2);

    StopPattern ringStopPattern = new StopPattern(List.of(st1, st2, st3));

    var ringTripTimes = TripTimesFactory.tripTimes(
      ringTrip,
      List.of(st1, st2, st3),
      new Deduplicator()
    );

    TripPattern ringTripPattern = TripPattern.of(id("P3"))
      .withRoute(ringRoute)
      .withStopPattern(ringStopPattern)
      .withScheduledTimeTableBuilder(builder -> builder.addTripTimes(ringTripTimes))
      .build();

    var ringRoutingPattern = ringTripPattern.getRoutingTripPattern();

    var ringTimetableRepo = new TimetableRepository(
      TEST_MODEL.siteRepositoryBuilder().withRegularStop(STOP_A).withRegularStop(STOP_B).build()
    );
    ringTimetableRepo.addTripPattern(ringTripPattern.getId(), ringTripPattern);
    ringTimetableRepo.index();

    var ringTransitService = new DefaultTransitService(ringTimetableRepo);

    var boardingAndAlighting = new BitSet(3);
    boardingAndAlighting.set(0);
    boardingAndAlighting.set(1);
    boardingAndAlighting.set(2);

    var tpfd = new TripPatternForDate(
      ringRoutingPattern,
      List.of(ringTripTimes),
      List.of(),
      SERVICE_DATE
    );

    var ringTpfds = new TripPatternForDates(
      ringRoutingPattern,
      new TripPatternForDate[] { tpfd },
      new int[] { 0 },
      boardingAndAlighting,
      boardingAndAlighting,
      0
    );

    int routeIndex = ringRoutingPattern.patternIndex();
    TripPatternForDates[] array = new TripPatternForDates[routeIndex + 1];
    array[routeIndex] = ringTpfds;
    var patternIndex = Arrays.asList(array);

    var resolver = new OnBoardAccessResolver(ringTransitService);
    var tripRef = TripOnDateReference.ofTripIdAndServiceDate(ringTrip.getId(), SERVICE_DATE);

    // First occurrence of STOP_A at 10:00
    var firstOccurrence = TripLocation.of(tripRef, STOP_A.getId(), toEpochMillis(10 * 3600));
    var result1 = resolver.resolve(firstOccurrence, patternIndex);
    assertEquals(0, result1.stopPositionInPattern());

    // Second occurrence of STOP_A at 10:15
    var secondOccurrence = TripLocation.of(
      tripRef,
      STOP_A.getId(),
      toEpochMillis(10 * 3600 + 15 * 60)
    );
    var result2 = resolver.resolve(secondOccurrence, patternIndex);
    assertEquals(2, result2.stopPositionInPattern());
  }
}
