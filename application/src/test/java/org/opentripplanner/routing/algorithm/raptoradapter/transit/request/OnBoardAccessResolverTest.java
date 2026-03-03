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
    var env = ENV_BUILDER
      .addTrip(
        TripInput.of("T1")
          .addStop(STOP_A, "10:00")
          .addStop(STOP_B, "10:05")
          .addStop(STOP_C, "10:10")
      )
      .build();

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
    var env = ENV_BUILDER
      .addTrip(
        TripInput.of("T1")
          .addStop(STOP_A, "10:00")
          .addStop(STOP_B, "10:05")
          .addStop(STOP_C, "10:10")
      )
      .build();

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
    var env = ENV_BUILDER
      .addTrip(
        TripInput.of("T1")
          .addStop(STOP_A, "10:00")
          .addStop(STOP_B, "10:05")
          .addStop(STOP_C, "10:10")
      )
      .build();

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
    var env = ENV_BUILDER
      .addTrip(
        TripInput.of("T1")
          .addStop(STOP_A, "10:00")
          .addStop(STOP_B, "10:05")
          .addStop(STOP_C, "10:10")
      )
      .build();

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
    var env = ENV_BUILDER
      .addTrip(
        TripInput.of("T1")
          .addStop(STOP_A, "10:00")
          .addStop(STOP_B, "10:05")
          .addStop(STOP_C, "10:10")
      )
      .build();

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
    var env = ENV_BUILDER
      .addTrip(
        TripInput.of("T1")
          .addStop(STOP_A, "10:00")
          .addStop(STOP_B, "10:05")
          .addStop(STOP_C, "10:10")
      )
      .build();

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
    var env = ENV_BUILDER
      .addTrip(
        TripInput.of("T1")
          .addStop(STOP_A, "10:00")
          .addStop(STOP_B, "10:05")
          .addStop(STOP_C, "10:10")
      )
      .build();

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
    var env = ENV_BUILDER
      .addTrip(
        TripInput.of("T1")
          .addStop(STOP_A, "10:00")
          .addStop(STOP_B, "10:05")
          .addStop(STOP_C, "10:10")
      )
      .build();

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
    var env = ENV_BUILDER
      .addTrip(
        TripInput.of("T1")
          .addStop(STOP_A, "10:00")
          .addStop(STOP_B, "10:05")
          .addStop(STOP_A, "10:15")
      )
      .build();

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
    var env = ENV_BUILDER
      .addTrip(
        TripInput.of("T1")
          .addStop(STOP_A, "10:00")
          .addStop(STOP_B, "10:05")
          .addStop(STOP_A, "10:15")
      )
      .build();

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
    var env = ENV_BUILDER
      .addTrip(
        TripInput.of("T1")
          .addStop(STOP_A, "10:00")
          .addStop(STOP_B, "10:05")
          .addStop(STOP_C, "10:10")
      )
      .build();

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
    var env = ENV_BUILDER
      .addTrip(
        TripInput.of("T1")
          .addStop(STOP_A, "10:00")
          .addStop(STOP_B, "10:05")
          .addStop(STOP_C, "10:10")
      )
      .build();

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
}
