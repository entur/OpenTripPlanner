package org.opentripplanner.routing.algorithm.raptoradapter.transit.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opentripplanner.core.model.id.FeedScopedIdForTestFactory.id;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.routing.algorithm.raptoradapter.router.onboardaccess.StartOnBoardAccessResolver;
import org.opentripplanner.routing.algorithm.raptoradapter.router.onboardaccess.TripAndServiceDate;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.mappers.TestLookupStopIndexCallback;
import org.opentripplanner.routing.error.RoutingValidationException;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.utils.time.ServiceDateUtils;

class StartOnBoardAccessResolverTest {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 11, 1);
  private static final ZoneId TIME_ZONE = ZoneId.of("GMT");

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of(
    SERVICE_DATE,
    TIME_ZONE
  );

  private final RegularStop STOP_A = ENV_BUILDER.stop("A");
  private final RegularStop STOP_B = ENV_BUILDER.stop("B");
  private final RegularStop STOP_C = ENV_BUILDER.stop("C");

  @Test
  void resolveSimpleOnBoardAccess() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var lookupStopIndex = new TestLookupStopIndexCallback(
      Map.of(
        STOP_A.getId(),
        new int[] { STOP_A.getIndex() },
        STOP_B.getId(),
        new int[] { STOP_B.getIndex() },
        STOP_C.getId(),
        new int[] { STOP_C.getIndex() }
      )
    );
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var result = new StartOnBoardAccessResolver(env.raptorRequestData()).resolve(
      tripAndServiceDate,
      STOP_B.getId(),
      lookupStopIndex,
      null,
      TIME_ZONE
    );

    var routingPattern = env.tripData("T1").scheduledTripPattern().getRoutingTripPattern();

    assertEquals(routingPattern.patternIndex(), result.tripBoarding().routeIndex());
    assertEquals(0, result.tripBoarding().tripScheduleIndex());
    assertEquals(1, result.tripBoarding().stopPositionInPattern());
    assertEquals(routingPattern.stopIndex(1), result.stop());
    assertEquals(10 * 3600 + 5 * 60, result.boardingTime());
  }

  @Test
  void resolveFirstStop() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var lookupStopIndex = new TestLookupStopIndexCallback(
      Map.of(
        STOP_A.getId(),
        new int[] { STOP_A.getIndex() },
        STOP_B.getId(),
        new int[] { STOP_B.getIndex() },
        STOP_C.getId(),
        new int[] { STOP_C.getIndex() }
      )
    );
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var result = new StartOnBoardAccessResolver(env.raptorRequestData()).resolve(
      tripAndServiceDate,
      STOP_A.getId(),
      lookupStopIndex,
      null,
      TIME_ZONE
    );

    var routingPattern = env.tripData("T1").scheduledTripPattern().getRoutingTripPattern();

    assertEquals(0, result.tripBoarding().stopPositionInPattern());
    assertEquals(routingPattern.stopIndex(0), result.stop());
    assertEquals(10 * 3600, result.boardingTime());
  }

  @Test
  void throwsOnLastStop() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var lookupStopIndex = new TestLookupStopIndexCallback(
      Map.of(
        STOP_A.getId(),
        new int[] { STOP_A.getIndex() },
        STOP_B.getId(),
        new int[] { STOP_B.getIndex() },
        STOP_C.getId(),
        new int[] { STOP_C.getIndex() }
      )
    );
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var patternSearch = env.raptorRequestData();
    assertThrows(IllegalArgumentException.class, () ->
      new StartOnBoardAccessResolver(patternSearch).resolve(
        tripAndServiceDate,
        STOP_C.getId(),
        lookupStopIndex,
        null,
        TIME_ZONE
      )
    );
  }

  @Test
  void throwsOnLastStopWithAimedDepartureTime() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var lookupStopIndex = new TestLookupStopIndexCallback(
      Map.of(
        STOP_A.getId(),
        new int[] { STOP_A.getIndex() },
        STOP_B.getId(),
        new int[] { STOP_B.getIndex() },
        STOP_C.getId(),
        new int[] { STOP_C.getIndex() }
      )
    );
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var patternSearch = env.raptorRequestData();
    var aimedDeparture = ServiceDateUtils.asStartOfService(SERVICE_DATE, TIME_ZONE)
      .plusSeconds(10 * 3600 + 10 * 60)
      .toInstant();
    assertThrows(IllegalArgumentException.class, () ->
      new StartOnBoardAccessResolver(patternSearch).resolve(
        tripAndServiceDate,
        STOP_C.getId(),
        lookupStopIndex,
        aimedDeparture,
        TIME_ZONE
      )
    );
  }

  @Test
  void resolveOnBoardAccessWithZeroCost() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var lookupStopIndex = new TestLookupStopIndexCallback(
      Map.of(
        STOP_A.getId(),
        new int[] { STOP_A.getIndex() },
        STOP_B.getId(),
        new int[] { STOP_B.getIndex() },
        STOP_C.getId(),
        new int[] { STOP_C.getIndex() }
      )
    );
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var result = new StartOnBoardAccessResolver(env.raptorRequestData()).resolve(
      tripAndServiceDate,
      STOP_B.getId(),
      lookupStopIndex,
      null,
      TIME_ZONE
    );
    assertEquals(0, result.c1());
  }

  @Test
  void resolveWithAimedDepartureTimeOnUniqueStop() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var lookupStopIndex = new TestLookupStopIndexCallback(
      Map.of(
        STOP_A.getId(),
        new int[] { STOP_A.getIndex() },
        STOP_B.getId(),
        new int[] { STOP_B.getIndex() },
        STOP_C.getId(),
        new int[] { STOP_C.getIndex() }
      )
    );
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var aimedDeparture = ServiceDateUtils.asStartOfService(SERVICE_DATE, TIME_ZONE)
      .plusSeconds(10 * 3600 + 5 * 60)
      .toInstant();
    var result = new StartOnBoardAccessResolver(env.raptorRequestData()).resolve(
      tripAndServiceDate,
      STOP_B.getId(),
      lookupStopIndex,
      aimedDeparture,
      TIME_ZONE
    );

    var routingPattern = env.tripData("T1").scheduledTripPattern().getRoutingTripPattern();

    assertEquals(routingPattern.patternIndex(), result.tripBoarding().routeIndex());
    assertEquals(0, result.tripBoarding().tripScheduleIndex());
    assertEquals(1, result.tripBoarding().stopPositionInPattern());
    assertEquals(routingPattern.stopIndex(1), result.stop());
    assertEquals(10 * 3600 + 5 * 60, result.boardingTime());
  }

  @Test
  void throwsOnWrongAimedDepartureTimeOnUniqueStop() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    // STOP_B departs at 10:05, but we provide 10:00 — should fail
    var lookupStopIndex = new TestLookupStopIndexCallback(
      Map.of(
        STOP_A.getId(),
        new int[] { STOP_A.getIndex() },
        STOP_B.getId(),
        new int[] { STOP_B.getIndex() },
        STOP_C.getId(),
        new int[] { STOP_C.getIndex() }
      )
    );
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var patternSearch = env.raptorRequestData();
    var wrongAimedDeparture = ServiceDateUtils.asStartOfService(SERVICE_DATE, TIME_ZONE)
      .plusSeconds(10 * 3600)
      .toInstant();
    assertThrows(IllegalArgumentException.class, () ->
      new StartOnBoardAccessResolver(patternSearch).resolve(
        tripAndServiceDate,
        STOP_B.getId(),
        lookupStopIndex,
        wrongAimedDeparture,
        TIME_ZONE
      )
    );
  }

  @Test
  void resolveWithScheduledRaptorData() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var lookupStopIndex = new TestLookupStopIndexCallback(
      Map.of(
        STOP_A.getId(),
        new int[] { STOP_A.getIndex() },
        STOP_B.getId(),
        new int[] { STOP_B.getIndex() },
        STOP_C.getId(),
        new int[] { STOP_C.getIndex() }
      )
    );
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    // Use ignoreRealtimeUpdates=true, mirroring the production flag in TransitRouter
    var result = new StartOnBoardAccessResolver(env.raptorRequestData(true)).resolve(
      tripAndServiceDate,
      STOP_B.getId(),
      lookupStopIndex,
      null,
      TIME_ZONE
    );

    var routingPattern = env.tripData("T1").scheduledTripPattern().getRoutingTripPattern();

    assertEquals(routingPattern.patternIndex(), result.tripBoarding().routeIndex());
    assertEquals(1, result.tripBoarding().stopPositionInPattern());
    assertEquals(routingPattern.stopIndex(1), result.stop());
    assertEquals(10 * 3600 + 5 * 60, result.boardingTime());
  }

  @Test
  void throwsOnRingLineWithStopId() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_A, "10:15")
    ).build();

    var lookupStopIndex = new TestLookupStopIndexCallback(
      Map.of(
        STOP_A.getId(),
        new int[] { STOP_A.getIndex() },
        STOP_B.getId(),
        new int[] { STOP_B.getIndex() }
      )
    );
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var patternSearch = env.raptorRequestData();
    assertThrows(RoutingValidationException.class, () ->
      new StartOnBoardAccessResolver(patternSearch).resolve(
        tripAndServiceDate,
        STOP_A.getId(),
        lookupStopIndex,
        null,
        TIME_ZONE
      )
    );
  }

  @Test
  void resolveRingLineWithScheduledDepartureTime() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1")
        .addStop(STOP_A, "10:00")
        .addStop(STOP_B, "10:05")
        .addStop(STOP_A, "10:15")
        .addStop(STOP_C, "10:20")
    ).build();

    var lookupStopIndex = new TestLookupStopIndexCallback(
      Map.of(
        STOP_A.getId(),
        new int[] { STOP_A.getIndex() },
        STOP_B.getId(),
        new int[] { STOP_B.getIndex() },
        STOP_C.getId(),
        new int[] { STOP_C.getIndex() }
      )
    );
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var patternSearch = env.raptorRequestData();

    // First occurrence of STOP_A at 10:00
    var firstOccurrence = ServiceDateUtils.asStartOfService(SERVICE_DATE, TIME_ZONE)
      .plusSeconds(10 * 3600)
      .toInstant();
    var result1 = new StartOnBoardAccessResolver(patternSearch).resolve(
      tripAndServiceDate,
      STOP_A.getId(),
      lookupStopIndex,
      firstOccurrence,
      TIME_ZONE
    );
    assertEquals(0, result1.tripBoarding().stopPositionInPattern());
    assertEquals(10 * 3600, result1.boardingTime());

    // Second occurrence of STOP_A at 10:15
    var secondOccurrence = ServiceDateUtils.asStartOfService(SERVICE_DATE, TIME_ZONE)
      .plusSeconds(10 * 3600 + 15 * 60)
      .toInstant();
    var result2 = new StartOnBoardAccessResolver(patternSearch).resolve(
      tripAndServiceDate,
      STOP_A.getId(),
      lookupStopIndex,
      secondOccurrence,
      TIME_ZONE
    );
    assertEquals(2, result2.tripBoarding().stopPositionInPattern());
    assertEquals(10 * 3600 + 15 * 60, result2.boardingTime());
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
    var patternSearch = env.raptorRequestData();

    // Realtime-modified pattern (different route index, not in Raptor data)
    var realtimePattern = TripPattern.of(id("P1-rt"))
      .withRoute(scheduledPattern.getRoute())
      .withStopPattern(scheduledPattern.getStopPattern())
      .withRealTimeStopPatternModified()
      .build();

    // Apply realtime update that maps the trip to the new pattern
    env
      .timetableSnapshotManager()
      .updateBuffer(RealTimeTripUpdate.of(realtimePattern, tripTimes, SERVICE_DATE).build());
    env.timetableSnapshotManager().purgeAndCommit();

    var lookupStopIndex = new TestLookupStopIndexCallback(
      Map.of(
        STOP_A.getId(),
        new int[] { STOP_A.getIndex() },
        STOP_B.getId(),
        new int[] { STOP_B.getIndex() },
        STOP_C.getId(),
        new int[] { STOP_C.getIndex() }
      )
    );
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    // findPattern(trip, SERVICE_DATE) returns realtimePattern (not in index),
    // falls back to findPattern(trip) which returns scheduledPattern (in index)
    var result = new StartOnBoardAccessResolver(patternSearch).resolve(
      tripAndServiceDate,
      STOP_B.getId(),
      lookupStopIndex,
      null,
      TIME_ZONE
    );

    assertEquals(
      scheduledPattern.getRoutingTripPattern().patternIndex(),
      result.tripBoarding().routeIndex()
    );
    assertEquals(1, result.tripBoarding().stopPositionInPattern());
    assertEquals(10 * 3600 + 5 * 60, result.boardingTime());
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

    var lookupStopIndex = new TestLookupStopIndexCallback(
      Map.of(
        id("StationA"),
        new int[] { stopA.getIndex() },
        id("StationB"),
        new int[] { stopB.getIndex() },
        id("StationC"),
        new int[] { stopC.getIndex() },
        stopA.getId(),
        new int[] { stopA.getIndex() },
        stopB.getId(),
        new int[] { stopB.getIndex() },
        stopC.getId(),
        new int[] { stopC.getIndex() }
      )
    );
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var result = new StartOnBoardAccessResolver(env.raptorRequestData()).resolve(
      tripAndServiceDate,
      id("StationB"),
      lookupStopIndex,
      null,
      TIME_ZONE
    );
    assertEquals(1, result.tripBoarding().stopPositionInPattern());
    assertEquals(10 * 3600 + 5 * 60, result.boardingTime());
  }

  /**
   * Station with multiple child stops where the trip visits only one. Passing the station ID
   * should find the visited stop regardless of child stop iteration order.
   */
  @Test
  void resolveByStationIdWithMultipleChildStops() {
    var stopA1 = ENV_BUILDER.stopAtStation("SA1", "StationA");
    var stopA2 = ENV_BUILDER.stopAtStation("SA2", "StationA");
    var stopB = ENV_BUILDER.stopAtStation("SB1", "StationB");
    var stopC = ENV_BUILDER.stopAtStation("SC1", "StationC");
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(stopA2, "10:00").addStop(stopB, "10:05").addStop(stopC, "10:10")
    ).build();

    var lookupStopIndex = new TestLookupStopIndexCallback(
      Map.of(
        id("StationA"),
        new int[] { stopA1.getIndex(), stopA2.getIndex() },
        id("StationB"),
        new int[] { stopB.getIndex() },
        id("StationC"),
        new int[] { stopC.getIndex() },
        stopA1.getId(),
        new int[] { stopA1.getIndex() },
        stopA2.getId(),
        new int[] { stopA2.getIndex() },
        stopB.getId(),
        new int[] { stopB.getIndex() },
        stopC.getId(),
        new int[] { stopC.getIndex() }
      )
    );
    // Pass station ID — should find SA2 at position 0 (even though SA1 also belongs to StationA)
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var result = new StartOnBoardAccessResolver(env.raptorRequestData()).resolve(
      tripAndServiceDate,
      id("StationA"),
      lookupStopIndex,
      null,
      TIME_ZONE
    );
    assertEquals(0, result.tripBoarding().stopPositionInPattern());
    assertEquals(10 * 3600, result.boardingTime());
  }

  /**
   * Station with multiple child stops where the trip visits only one. Passing the stop ID for the
   * wrong stop means we throw.
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

    var lookupStopIndex = new TestLookupStopIndexCallback(
      Map.of(
        id("StationA"),
        new int[] { stopA1.getIndex(), stopA2.getIndex() },
        id("StationB"),
        new int[] { stopB.getIndex() },
        id("StationC"),
        new int[] { stopC.getIndex() },
        stopA1.getId(),
        new int[] { stopA1.getIndex() },
        stopA2.getId(),
        new int[] { stopA2.getIndex() },
        stopB.getId(),
        new int[] { stopB.getIndex() },
        stopC.getId(),
        new int[] { stopC.getIndex() }
      )
    );
    // A2 is the stop visited, but here we pass A1
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var patternSearch = env.raptorRequestData();
    assertThrows(IllegalArgumentException.class, () ->
      new StartOnBoardAccessResolver(patternSearch).resolve(
        tripAndServiceDate,
        stopA1.getId(),
        lookupStopIndex,
        null,
        TIME_ZONE
      )
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
    var stopC = ENV_BUILDER.stopAtStation("SC1", "StationC");
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1")
        .addStop(stopA1, "10:00")
        .addStop(stopB, "10:05")
        .addStop(stopA2, "10:15")
        .addStop(stopC, "10:20")
    ).build();

    var lookupStopIndex = new TestLookupStopIndexCallback(
      Map.of(
        id("StationA"),
        new int[] { stopA1.getIndex(), stopA2.getIndex() },
        id("StationB"),
        new int[] { stopB.getIndex() },
        id("StationC"),
        new int[] { stopC.getIndex() },
        stopA1.getId(),
        new int[] { stopA1.getIndex() },
        stopA2.getId(),
        new int[] { stopA2.getIndex() },
        stopB.getId(),
        new int[] { stopB.getIndex() },
        stopC.getId(),
        new int[] { stopC.getIndex() }
      )
    );
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var patternSearch = env.raptorRequestData();

    // Without departure time — ambiguous, should throw
    assertThrows(RoutingValidationException.class, () ->
      new StartOnBoardAccessResolver(patternSearch).resolve(
        tripAndServiceDate,
        id("StationA"),
        lookupStopIndex,
        null,
        TIME_ZONE
      )
    );

    // With departure time for SA1 at 10:00 — should find position 0
    var firstOccurrence = ServiceDateUtils.asStartOfService(SERVICE_DATE, TIME_ZONE)
      .plusSeconds(10 * 3600)
      .toInstant();
    var result1 = new StartOnBoardAccessResolver(patternSearch).resolve(
      tripAndServiceDate,
      id("StationA"),
      lookupStopIndex,
      firstOccurrence,
      TIME_ZONE
    );
    assertEquals(0, result1.tripBoarding().stopPositionInPattern());
    assertEquals(10 * 3600, result1.boardingTime());

    // With departure time for SA2 at 10:15 — should find position 2
    var secondOccurrence = ServiceDateUtils.asStartOfService(SERVICE_DATE, TIME_ZONE)
      .plusSeconds(10 * 3600 + 15 * 60)
      .toInstant();
    var result2 = new StartOnBoardAccessResolver(patternSearch).resolve(
      tripAndServiceDate,
      id("StationA"),
      lookupStopIndex,
      secondOccurrence,
      TIME_ZONE
    );
    assertEquals(2, result2.tripBoarding().stopPositionInPattern());
    assertEquals(10 * 3600 + 15 * 60, result2.boardingTime());
  }

  /**
   * When a TripPattern is copied (as happens during graph build in TransitDataImportBuilder),
   * the copy gets a new RoutingTripPattern with a different patternIndex. However, the
   * scheduledTimetable may still reference the original pattern. The Raptor data is built from
   * scheduledTimetable.getPattern().getRoutingTripPattern() (the original), while
   * TransitService.findPattern(trip) returns the copy.
   *
   * This test verifies that findPatternInRaptorData falls back to the scheduledTimetable's
   * pattern when the copy's RoutingTripPattern is not in the Raptor index.
   */
  @Test
  void resolveFallsBackToScheduledTimetablePatternWhenCopiedPatternNotInIndex() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var tripData = env.tripData("T1");
    var originalPattern = tripData.scheduledTripPattern();

    // Build Raptor data BEFORE replacing the pattern — so it indexes the original's
    // RoutingTripPattern
    var patternSearch = env.raptorRequestData();

    // Simulate what TransitDataImportBuilder does: copy the pattern while reusing the
    // existing scheduledTimetable. The copy gets a new RoutingTripPattern (different
    // patternIndex), but scheduledTimetable.getPattern() still points to the original.
    // We must change an unrelated field (name) to prevent AbstractBuilder.build() from
    // returning the original due to sameAs() equality.
    var copiedPattern = originalPattern
      .copy()
      .withName("copied")
      .withScheduledTimeTable(originalPattern.getScheduledTimetable())
      .build();

    // Replace the pattern in the repository — findPattern(trip) will now return the copy
    env.timetableRepository().addTripPattern(copiedPattern.getId(), copiedPattern);
    env.timetableRepository().index();

    // Verify our setup: the copy has a different RoutingTripPattern index
    assertNotEquals(
      originalPattern.getRoutingTripPattern().patternIndex(),
      copiedPattern.getRoutingTripPattern().patternIndex()
    );
    // And the scheduledTimetable still references the original
    assertSame(originalPattern, copiedPattern.getScheduledTimetable().getPattern());

    var lookupStopIndex = new TestLookupStopIndexCallback(
      Map.of(
        STOP_A.getId(),
        new int[] { STOP_A.getIndex() },
        STOP_B.getId(),
        new int[] { STOP_B.getIndex() },
        STOP_C.getId(),
        new int[] { STOP_C.getIndex() }
      )
    );
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    // Should succeed by falling back to scheduledTimetable.getPattern() (the original)
    var result = new StartOnBoardAccessResolver(patternSearch).resolve(
      tripAndServiceDate,
      STOP_B.getId(),
      lookupStopIndex,
      null,
      TIME_ZONE
    );

    assertEquals(
      originalPattern.getRoutingTripPattern().patternIndex(),
      result.tripBoarding().routeIndex()
    );
    assertEquals(1, result.tripBoarding().stopPositionInPattern());
    assertEquals(10 * 3600 + 5 * 60, result.boardingTime());
  }

  /**
   * Combines both failure modes: a realtime update creates a new pattern not in the index,
   * AND the base pattern was copied (so its RoutingTripPattern also differs from what's in
   * the index). The resolver must:
   * 1. Try findPattern(trip, date) → realtime pattern (not in index, scheduledTimetable
   *    points to itself → still not in index)
   * 2. Fall back to findPattern(trip) → copied base pattern (not in index)
   * 3. Fall back to copiedBase.scheduledTimetable.getPattern() → original (in index)
   */
  @Test
  void resolveFallsBackThroughRealtimeAndCopiedPattern() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var tripData = env.tripData("T1");
    var originalPattern = tripData.scheduledTripPattern();
    var tripTimes = tripData.scheduledTripTimes();

    // Build Raptor data with the original pattern
    var patternSearch = env.raptorRequestData();

    // Step 1: Copy the pattern (simulating graph build), reusing scheduledTimetable
    var copiedPattern = originalPattern
      .copy()
      .withName("copied")
      .withScheduledTimeTable(originalPattern.getScheduledTimetable())
      .build();
    env.timetableRepository().addTripPattern(copiedPattern.getId(), copiedPattern);
    env.timetableRepository().index();

    // Step 2: Apply a realtime update that moves the trip to a new pattern
    var realtimePattern = TripPattern.of(id("P1-rt"))
      .withRoute(originalPattern.getRoute())
      .withStopPattern(originalPattern.getStopPattern())
      .withRealTimeStopPatternModified()
      .build();
    env
      .timetableSnapshotManager()
      .updateBuffer(RealTimeTripUpdate.of(realtimePattern, tripTimes, SERVICE_DATE).build());
    env.timetableSnapshotManager().purgeAndCommit();

    var lookupStopIndex = new TestLookupStopIndexCallback(
      Map.of(
        STOP_A.getId(),
        new int[] { STOP_A.getIndex() },
        STOP_B.getId(),
        new int[] { STOP_B.getIndex() },
        STOP_C.getId(),
        new int[] { STOP_C.getIndex() }
      )
    );
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    // findPattern(trip, date) → realtimePattern (not in index)
    // findPattern(trip) → copiedPattern (not in index)
    // copiedPattern.scheduledTimetable.getPattern() → originalPattern (in index!)
    var result = new StartOnBoardAccessResolver(patternSearch).resolve(
      tripAndServiceDate,
      STOP_B.getId(),
      lookupStopIndex,
      null,
      TIME_ZONE
    );

    assertEquals(
      originalPattern.getRoutingTripPattern().patternIndex(),
      result.tripBoarding().routeIndex()
    );
    assertEquals(1, result.tripBoarding().stopPositionInPattern());
    assertEquals(10 * 3600 + 5 * 60, result.boardingTime());
  }

  /**
   * When the clocks move forward in spring due to DST, midnight and noon-minus-12h
   * (start-of-service) differ by one hour. The aimed departure time conversion uses
   * start-of-service (noon-minus-12h) as the reference, not midnight, because TripTimes are
   * relative to start-of-service.
   */
  @Test
  void resolveWithAimedDepartureTimeOnDstSpringForwardDay() {
    // Europe/Oslo moves clocks forward on 2024-03-31: clocks skip from 02:00 to 03:00
    var dstDate = LocalDate.of(2024, 3, 31);
    var dstZone = ZoneId.of("Europe/Oslo");
    var dstEnvBuilder = TransitTestEnvironment.of(dstDate, dstZone);
    var stopA = dstEnvBuilder.stop("A");
    var stopB = dstEnvBuilder.stop("B");
    var stopC = dstEnvBuilder.stop("C");

    var env = dstEnvBuilder
      .addTrip(
        TripInput.of("T1").addStop(stopA, "10:00").addStop(stopB, "10:05").addStop(stopC, "10:10")
      )
      .build();

    var lookupStopIndex = new TestLookupStopIndexCallback(
      Map.of(
        stopA.getId(),
        new int[] { stopA.getIndex() },
        stopB.getId(),
        new int[] { stopB.getIndex() },
        stopC.getId(),
        new int[] { stopC.getIndex() }
      )
    );
    // Compute the aimed departure instant using start-of-service (noon-minus-12h),
    // which is what a correct client would send
    var aimedDeparture = ServiceDateUtils.asStartOfService(dstDate, dstZone)
      .plusSeconds(10 * 3600 + 5 * 60)
      .toInstant();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), dstDate);
    var result = new StartOnBoardAccessResolver(env.raptorRequestData()).resolve(
      tripAndServiceDate,
      stopB.getId(),
      lookupStopIndex,
      aimedDeparture,
      dstZone
    );

    assertEquals(1, result.tripBoarding().stopPositionInPattern());
    assertEquals(10 * 3600 + 5 * 60, result.boardingTime());
  }

  /**
   * Passing a stop ID which doesn't allow boarding should throw.
   */
  @Test
  void throwsWhenBoardingNotPossibleAtStop() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1")
        .addStop(STOP_A, "10:00")
        .addStop(STOP_B, "10:05", "10:05", PickDrop.NONE, PickDrop.SCHEDULED)
        .addStop(STOP_C, "10:10")
    ).build();

    var lookupStopIndex = new TestLookupStopIndexCallback(
      Map.of(
        STOP_A.getId(),
        new int[] { STOP_A.getIndex() },
        STOP_B.getId(),
        new int[] { STOP_B.getIndex() },
        STOP_C.getId(),
        new int[] { STOP_C.getIndex() }
      )
    );
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var patternSearch = env.raptorRequestData();
    assertThrows(IllegalArgumentException.class, () ->
      new StartOnBoardAccessResolver(patternSearch).resolve(
        tripAndServiceDate,
        STOP_B.getId(),
        lookupStopIndex,
        null,
        TIME_ZONE
      )
    );
  }

  /**
   * Passing a stop ID which doesn't allow boarding should throw.
   */
  @Test
  void throwsWhenBoardingNotPossibleAtStopWithAimedDepartureTime() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1")
        .addStop(STOP_A, "10:00")
        .addStop(STOP_B, "10:05", "10:05", PickDrop.NONE, PickDrop.SCHEDULED)
        .addStop(STOP_C, "10:10")
    ).build();

    var lookupStopIndex = new TestLookupStopIndexCallback(
      Map.of(
        STOP_A.getId(),
        new int[] { STOP_A.getIndex() },
        STOP_B.getId(),
        new int[] { STOP_B.getIndex() },
        STOP_C.getId(),
        new int[] { STOP_C.getIndex() }
      )
    );
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var patternSearch = env.raptorRequestData();
    var aimedDeparture = ServiceDateUtils.asStartOfService(SERVICE_DATE, TIME_ZONE)
      .plusSeconds(10 * 3600 + 5 * 60)
      .toInstant();
    assertThrows(IllegalArgumentException.class, () ->
      new StartOnBoardAccessResolver(patternSearch).resolve(
        tripAndServiceDate,
        STOP_B.getId(),
        lookupStopIndex,
        aimedDeparture,
        TIME_ZONE
      )
    );
  }

  /**
   * Passing a station ID whose child stops are not visited by the trip should throw.
   */
  @Test
  void throwsOnWrongStationId() {
    var stopA = ENV_BUILDER.stopAtStation("SA1", "StationA");
    var stopB = ENV_BUILDER.stopAtStation("SB1", "StationB");
    var stopC = ENV_BUILDER.stopAtStation("SC1", "StationC");
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(stopA, "10:00").addStop(stopB, "10:05")
    ).build();

    var lookupStopIndex = new TestLookupStopIndexCallback(
      Map.of(
        id("StationA"),
        new int[] { stopA.getIndex() },
        id("StationB"),
        new int[] { stopB.getIndex() },
        id("StationC"),
        new int[] { stopC.getIndex() },
        stopA.getId(),
        new int[] { stopA.getIndex() },
        stopB.getId(),
        new int[] { stopB.getIndex() },
        stopC.getId(),
        new int[] { stopC.getIndex() }
      )
    );
    // StationC has child stop SC1 which is not in the trip's pattern
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var patternSearch = env.raptorRequestData();
    assertThrows(IllegalArgumentException.class, () ->
      new StartOnBoardAccessResolver(patternSearch).resolve(
        tripAndServiceDate,
        id("StationC"),
        lookupStopIndex,
        null,
        TIME_ZONE
      )
    );
  }
}
