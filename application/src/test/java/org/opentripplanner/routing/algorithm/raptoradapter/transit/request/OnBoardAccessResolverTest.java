package org.opentripplanner.routing.algorithm.raptoradapter.transit.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opentripplanner.transit.model._data.FeedScopedIdForTestFactory.id;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.opentripplanner.routing.algorithm.raptoradapter.router.OnBoardAccessResolver;
import org.opentripplanner.routing.api.request.TripLocation;
import org.opentripplanner.routing.api.request.TripOnDateReference;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;

class OnBoardAccessResolverTest {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 11, 1);
  private static final ZoneId TIME_ZONE = ZoneId.of("GMT");

  private static final String STOP_A_ID = "A";
  private static final String STOP_B_ID = "B";
  private static final String STOP_C_ID = "C";

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of(
    SERVICE_DATE,
    TIME_ZONE
  );
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stop(STOP_B_ID);
  private final RegularStop STOP_C = ENV_BUILDER.stop(STOP_C_ID);

  private static long toEpochMillis(int secondsSinceMidnight) {
    long midnightEpochSecond = SERVICE_DATE.atStartOfDay(TIME_ZONE).toEpochSecond();
    return (midnightEpochSecond + secondsSinceMidnight) * 1000L;
  }

  @Test
  void resolveSimpleOnBoardAccess() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var resolver = new OnBoardAccessResolver(env.transitService());
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(id("T1"), SERVICE_DATE),
      STOP_B.getId()
    );

    var patternSearch = env.raptorRequestData().onBoardTripPatternSearch();
    var resolved = resolver.resolve(tripLocation, patternSearch);
    var result = resolved.access();

    var tripData = env.tripData("T1");
    var routingPattern = tripData.scheduledTripPattern().getRoutingTripPattern();

    assertEquals(SERVICE_DATE, resolved.serviceDate());
    assertEquals(routingPattern.patternIndex(), result.routeIndex());
    assertEquals(0, result.tripScheduleIndex());
    assertEquals(1, result.stopPositionInPattern());
    assertEquals(routingPattern.stopIndex(1), result.stop());
    assertEquals(10 * 3600 + 5 * 60, result.boardingTime());
  }

  @Test
  void resolveFirstStop() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var resolver = new OnBoardAccessResolver(env.transitService());
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(id("T1"), SERVICE_DATE),
      STOP_A.getId()
    );

    var resolved = resolver.resolve(
      tripLocation,
      env.raptorRequestData().onBoardTripPatternSearch()
    );
    var result = resolved.access();

    var routingPattern = env.tripData("T1").scheduledTripPattern().getRoutingTripPattern();

    assertEquals(SERVICE_DATE, resolved.serviceDate());
    assertEquals(0, result.stopPositionInPattern());
    assertEquals(routingPattern.stopIndex(0), result.stop());
    assertEquals(10 * 3600, result.boardingTime());
  }

  @Test
  void resolveLastStop() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var resolver = new OnBoardAccessResolver(env.transitService());
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(id("T1"), SERVICE_DATE),
      STOP_C.getId()
    );

    var resolved = resolver.resolve(
      tripLocation,
      env.raptorRequestData().onBoardTripPatternSearch()
    );
    var result = resolved.access();

    var routingPattern = env.tripData("T1").scheduledTripPattern().getRoutingTripPattern();

    assertEquals(SERVICE_DATE, resolved.serviceDate());
    assertEquals(2, result.stopPositionInPattern());
    assertEquals(routingPattern.stopIndex(2), result.stop());
    assertEquals(10 * 3600 + 10 * 60, result.boardingTime());
  }

  @Test
  void throwsOnUnknownTrip() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var resolver = new OnBoardAccessResolver(env.transitService());
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(id("unknown"), SERVICE_DATE),
      STOP_A.getId()
    );

    var patternSearch = env.raptorRequestData().onBoardTripPatternSearch();
    assertThrows(IllegalArgumentException.class, () ->
      resolver.resolve(tripLocation, patternSearch)
    );
  }

  @Test
  void throwsOnUnknownStop() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var resolver = new OnBoardAccessResolver(env.transitService());
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(id("T1"), SERVICE_DATE),
      id("unknown-stop")
    );

    var patternSearch = env.raptorRequestData().onBoardTripPatternSearch();
    assertThrows(IllegalArgumentException.class, () ->
      resolver.resolve(tripLocation, patternSearch)
    );
  }

  @Test
  void resolveOnBoardAccessWithZeroCost() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var resolver = new OnBoardAccessResolver(env.transitService());
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(id("T1"), SERVICE_DATE),
      STOP_B.getId()
    );

    var resolved = resolver.resolve(
      tripLocation,
      env.raptorRequestData().onBoardTripPatternSearch()
    );
    assertEquals(0, resolved.access().c1());
  }

  @Test
  void resolveWithScheduledDepartureTimeOnUniqueStop() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var resolver = new OnBoardAccessResolver(env.transitService());
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(id("T1"), SERVICE_DATE),
      STOP_B.getId(),
      toEpochMillis(10 * 3600 + 5 * 60)
    );

    var patternSearch = env.raptorRequestData().onBoardTripPatternSearch();
    var resolved = resolver.resolve(tripLocation, patternSearch);
    var result = resolved.access();

    var routingPattern = env.tripData("T1").scheduledTripPattern().getRoutingTripPattern();

    assertEquals(routingPattern.patternIndex(), result.routeIndex());
    assertEquals(0, result.tripScheduleIndex());
    assertEquals(1, result.stopPositionInPattern());
    assertEquals(routingPattern.stopIndex(1), result.stop());
    assertEquals(10 * 3600 + 5 * 60, result.boardingTime());
  }

  @Test
  void resolveWithScheduledRaptorData() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var resolver = new OnBoardAccessResolver(env.transitService());
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(id("T1"), SERVICE_DATE),
      STOP_B.getId()
    );

    // Use ignoreRealtimeUpdates=true, mirroring the production flag in TransitRouter
    var patternSearch = env.raptorRequestData(true).onBoardTripPatternSearch();
    var resolved = resolver.resolve(tripLocation, patternSearch);
    var result = resolved.access();

    var routingPattern = env.tripData("T1").scheduledTripPattern().getRoutingTripPattern();

    assertEquals(routingPattern.patternIndex(), result.routeIndex());
    assertEquals(1, result.stopPositionInPattern());
    assertEquals(routingPattern.stopIndex(1), result.stop());
    assertEquals(10 * 3600 + 5 * 60, result.boardingTime());
  }

  @Test
  void throwsOnRingLineWithStopId() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_A, "10:15")
    ).build();

    var resolver = new OnBoardAccessResolver(env.transitService());
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(id("T1"), SERVICE_DATE),
      STOP_A.getId()
    );

    var patternSearch = env.raptorRequestData().onBoardTripPatternSearch();
    assertThrows(IllegalArgumentException.class, () ->
      resolver.resolve(tripLocation, patternSearch)
    );
  }

  @Test
  void resolveRingLineWithScheduledDepartureTime() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_A, "10:15")
    ).build();

    var resolver = new OnBoardAccessResolver(env.transitService());
    var tripRef = TripOnDateReference.ofTripIdAndServiceDate(id("T1"), SERVICE_DATE);

    // First occurrence of STOP_A at 10:00
    var firstOccurrence = TripLocation.of(tripRef, STOP_A.getId(), toEpochMillis(10 * 3600));
    var patternSearch = env.raptorRequestData().onBoardTripPatternSearch();
    var resolved1 = resolver.resolve(firstOccurrence, patternSearch);
    assertEquals(0, resolved1.access().stopPositionInPattern());
    assertEquals(10 * 3600, resolved1.access().boardingTime());

    // Second occurrence of STOP_A at 10:15
    var secondOccurrence = TripLocation.of(
      tripRef,
      STOP_A.getId(),
      toEpochMillis(10 * 3600 + 15 * 60)
    );
    var resolved2 = resolver.resolve(secondOccurrence, patternSearch);
    assertEquals(2, resolved2.access().stopPositionInPattern());
    assertEquals(10 * 3600 + 15 * 60, resolved2.access().boardingTime());
  }

  /**
   * When a realtime-modified pattern is not in the Raptor pattern index,
   * findPatternInRaptorData falls back to the base/static pattern.
   */
  @Test
  void resolveFallsBackToBasePatternWhenRealtimePatternNotInIndex() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var tripData = env.tripData("T1");
    var scheduledPattern = tripData.scheduledTripPattern();
    var tripTimes = tripData.scheduledTripTimes();

    // Build pattern search BEFORE applying the realtime update — so it only has scheduled patterns
    var patternSearch = env.raptorRequestData().onBoardTripPatternSearch();

    // Realtime-modified pattern (different route index, not in Raptor data)
    var realtimePattern = TripPattern.of(id("P1-rt"))
      .withRoute(scheduledPattern.getRoute())
      .withStopPattern(scheduledPattern.getStopPattern())
      .withRealTimeStopPatternModified()
      .build();

    // Apply realtime update that maps the trip to the new pattern
    env
      .timetableSnapshotManager()
      .updateBuffer(new RealTimeTripUpdate(realtimePattern, tripTimes, SERVICE_DATE));
    env.timetableSnapshotManager().purgeAndCommit();

    // Transit service sees the realtime pattern, but patternSearch has only scheduled
    var resolver = new OnBoardAccessResolver(env.transitService());
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(id("T1"), SERVICE_DATE),
      STOP_B.getId()
    );

    // findPattern(trip, SERVICE_DATE) returns realtimePattern (not in index),
    // falls back to findPattern(trip) which returns scheduledPattern (in index)
    var resolved = resolver.resolve(tripLocation, patternSearch);

    assertEquals(
      scheduledPattern.getRoutingTripPattern().patternIndex(),
      resolved.access().routeIndex()
    );
    assertEquals(1, resolved.access().stopPositionInPattern());
    assertEquals(10 * 3600 + 5 * 60, resolved.access().boardingTime());
  }

  /**
   * Verify that resolveBoardingDateTime works when a realtime updater has modified the trip's
   * stop pattern, moving it to a new TripPattern whose scheduled timetable is empty.
   */
  @Test
  void resolveBoardingDateTimeWithRealtimeModifiedPattern() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var tripData = env.tripData("T1");
    var scheduledPattern = tripData.scheduledTripPattern();
    var tripTimes = tripData.scheduledTripTimes();

    // Realtime-modified pattern (empty scheduled timetable)
    var realtimePattern = TripPattern.of(id("P1-rt"))
      .withRoute(scheduledPattern.getRoute())
      .withStopPattern(scheduledPattern.getStopPattern())
      .withRealTimeStopPatternModified()
      .build();

    // Apply realtime update
    env
      .timetableSnapshotManager()
      .updateBuffer(new RealTimeTripUpdate(realtimePattern, tripTimes, SERVICE_DATE));
    env.timetableSnapshotManager().purgeAndCommit();

    var resolver = new OnBoardAccessResolver(env.transitService());
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(id("T1"), SERVICE_DATE),
      STOP_B.getId()
    );

    var result = resolver.resolveBoardingDateTime(tripLocation, TIME_ZONE);

    long expectedEpochSecond =
      SERVICE_DATE.atStartOfDay(TIME_ZONE).toEpochSecond() + 10 * 3600 + 5 * 60;
    assertEquals(expectedEpochSecond, result.getEpochSecond());
  }

  /**
   * When a station ID is passed instead of a stop ID, the resolver should
   * find the child stop that the trip visits.
   */
  @Test
  void resolveByStationId() {
    var stopA = ENV_BUILDER.stopAtStation("SA1", "StationA");
    var stopB = ENV_BUILDER.stopAtStation("SB1", "StationB");
    var stopC = ENV_BUILDER.stopAtStation("SC1", "StationC");
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(stopA, "10:00").addStop(stopB, "10:05").addStop(stopC, "10:10")
    ).build();

    var resolver = new OnBoardAccessResolver(env.transitService());
    var patternSearch = env.raptorRequestData().onBoardTripPatternSearch();

    // Pass the station ID — should resolve to the child stop's position
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(id("T1"), SERVICE_DATE),
      id("StationB")
    );

    var resolved = resolver.resolve(tripLocation, patternSearch);
    assertEquals(1, resolved.access().stopPositionInPattern());
    assertEquals(10 * 3600 + 5 * 60, resolved.access().boardingTime());
  }

  /**
   * Station with multiple child stops where the trip visits only one. Passing the station ID
   * should find the visited stop regardless of child stop iteration order.
   */
  @Test
  void resolveByStationIdWithMultipleChildStops() {
    ENV_BUILDER.stopAtStation("SA1", "StationA");
    var stopA2 = ENV_BUILDER.stopAtStation("SA2", "StationA");
    var stopB = ENV_BUILDER.stopAtStation("SB1", "StationB");
    var stopC = ENV_BUILDER.stopAtStation("SC1", "StationC");
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(stopA2, "10:00").addStop(stopB, "10:05").addStop(stopC, "10:10")
    ).build();

    var resolver = new OnBoardAccessResolver(env.transitService());
    var patternSearch = env.raptorRequestData().onBoardTripPatternSearch();

    // Pass station ID — should find SA2 at position 1 (even though SA1 also belongs to StationA)
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(id("T1"), SERVICE_DATE),
      id("StationA")
    );

    var resolved = resolver.resolve(tripLocation, patternSearch);
    assertEquals(0, resolved.access().stopPositionInPattern());
    assertEquals(10 * 3600, resolved.access().boardingTime());
  }

  /**
   * Station with multiple child stops where the trip visits only one. Passing the stop ID for the
   * wrong stop means we throw
   */
  @Test
  void resolveByStationIdWithMultipleChildStopsThrowsWhenWrongStopPassed() {
    var stopA1 = ENV_BUILDER.stopAtStation("SA1", "StationA");
    var stopA2 = ENV_BUILDER.stopAtStation("SA2", "StationA");
    var stopB = ENV_BUILDER.stopAtStation("SB1", "StationB");
    var stopC = ENV_BUILDER.stopAtStation("SC1", "StationC");
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(stopA2, "10:00").addStop(stopB, "10:05").addStop(stopC, "10:10")
    ).build();

    var resolver = new OnBoardAccessResolver(env.transitService());
    var patternSearch = env.raptorRequestData().onBoardTripPatternSearch();

    // A2 is the stop visited, but here we pass A1
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(id("T1"), SERVICE_DATE),
      stopA1.getId()
    );

    // Should throw since A1 is not visited
    assertThrows(IllegalArgumentException.class, () ->
      resolver.resolve(tripLocation, patternSearch)
    );
  }

  /**
   * Station with multiple child stops on a ring line — pattern visits SA1 then SA2 (both children
   * of the same station). Passing the station ID without a departure time should throw because
   * it is ambiguous. Passing with a departure time should disambiguate.
   */
  @Test
  void resolveStationOnRingLineThrowsWithoutDepartureTime() {
    var stopA1 = ENV_BUILDER.stopAtStation("SA1", "StationA");
    var stopA2 = ENV_BUILDER.stopAtStation("SA2", "StationA");
    var stopB = ENV_BUILDER.stopAtStation("SB1", "StationB");
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(stopA1, "10:00").addStop(stopB, "10:05").addStop(stopA2, "10:15")
    ).build();

    var resolver = new OnBoardAccessResolver(env.transitService());
    var patternSearch = env.raptorRequestData().onBoardTripPatternSearch();
    var tripRef = TripOnDateReference.ofTripIdAndServiceDate(id("T1"), SERVICE_DATE);

    // Without departure time — ambiguous, should throw
    var ambiguous = TripLocation.of(tripRef, id("StationA"));
    assertThrows(IllegalArgumentException.class, () -> resolver.resolve(ambiguous, patternSearch));

    // With departure time for SA2 at 10:15 — should disambiguate
    var withTime = TripLocation.of(tripRef, id("StationA"), toEpochMillis(10 * 3600 + 15 * 60));
    var resolved = resolver.resolve(withTime, patternSearch);
    assertEquals(2, resolved.access().stopPositionInPattern());
    assertEquals(10 * 3600 + 15 * 60, resolved.access().boardingTime());

    // With departure time for SA1 at 10:00 — should find position 0
    var withTimeFirst = TripLocation.of(tripRef, id("StationA"), toEpochMillis(10 * 3600));
    var resolvedFirst = resolver.resolve(withTimeFirst, patternSearch);
    assertEquals(0, resolvedFirst.access().stopPositionInPattern());
    assertEquals(10 * 3600, resolvedFirst.access().boardingTime());
  }

  /**
   * Passing a station ID whose child stops are not visited by the trip should throw.
   */
  @Test
  void throwsOnWrongStationId() {
    var stopA = ENV_BUILDER.stopAtStation("SA1", "StationA");
    var stopB = ENV_BUILDER.stopAtStation("SB1", "StationB");
    ENV_BUILDER.stopAtStation("SC1", "StationC");
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(stopA, "10:00").addStop(stopB, "10:05")
    ).build();

    var resolver = new OnBoardAccessResolver(env.transitService());
    var patternSearch = env.raptorRequestData().onBoardTripPatternSearch();

    // StationC has child stop SC1 which is not in the trip's pattern
    var tripLocation = TripLocation.of(
      TripOnDateReference.ofTripIdAndServiceDate(id("T1"), SERVICE_DATE),
      id("StationC")
    );
    assertThrows(IllegalArgumentException.class, () ->
      resolver.resolve(tripLocation, patternSearch)
    );
  }
}
