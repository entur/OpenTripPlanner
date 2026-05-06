package org.opentripplanner.routing.algorithm.raptoradapter.transit.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opentripplanner.core.model.id.FeedScopedIdForTestFactory.id;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.routing.algorithm.raptoradapter.router.onboardaccess.StartOnBoardAccessResolver;
import org.opentripplanner.routing.algorithm.raptoradapter.router.onboardaccess.TripAndServiceDate;
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

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var result = new StartOnBoardAccessResolver(env.raptorRequestData()).resolve(
      tripAndServiceDate,
      List.of(STOP_B.getIndex()),
      null,
      TIME_ZONE
    );

    var routingPattern = env.tripData("T1").scheduledTripPattern().getRoutingTripPattern();

    assertEquals(routingPattern.patternIndex(), result.tripBoarding().routeIndex());
    assertEquals(0, result.tripBoarding().tripScheduleIndex());
    assertEquals(1, result.tripBoarding().stopPositionInPattern());
    assertEquals(routingPattern.stopIndex(1), result.stop());
    assertEquals(10 * 3600 + 5 * 60, result.boardingTime());
    assertEquals(0, result.c1());
  }

  @Test
  void resolveFirstStop() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var result = new StartOnBoardAccessResolver(env.raptorRequestData()).resolve(
      tripAndServiceDate,
      List.of(STOP_A.getIndex()),
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

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var patternSearch = env.raptorRequestData();
    assertThrows(IllegalArgumentException.class, () ->
      new StartOnBoardAccessResolver(patternSearch).resolve(
        tripAndServiceDate,
        List.of(STOP_C.getIndex()),
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

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var patternSearch = env.raptorRequestData();
    var aimedDeparture = ServiceDateUtils.asStartOfService(SERVICE_DATE, TIME_ZONE)
      .plusSeconds(10 * 3600 + 10 * 60)
      .toInstant();
    assertThrows(IllegalArgumentException.class, () ->
      new StartOnBoardAccessResolver(patternSearch).resolve(
        tripAndServiceDate,
        List.of(STOP_C.getIndex()),
        aimedDeparture,
        TIME_ZONE
      )
    );
  }

  @Test
  void resolveWithAimedDepartureTimeOnUniqueStop() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var aimedDeparture = ServiceDateUtils.asStartOfService(SERVICE_DATE, TIME_ZONE)
      .plusSeconds(10 * 3600 + 5 * 60)
      .toInstant();
    var result = new StartOnBoardAccessResolver(env.raptorRequestData()).resolve(
      tripAndServiceDate,
      List.of(STOP_B.getIndex()),
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
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var patternSearch = env.raptorRequestData();
    var wrongAimedDeparture = ServiceDateUtils.asStartOfService(SERVICE_DATE, TIME_ZONE)
      .plusSeconds(10 * 3600)
      .toInstant();
    assertThrows(IllegalArgumentException.class, () ->
      new StartOnBoardAccessResolver(patternSearch).resolve(
        tripAndServiceDate,
        List.of(STOP_B.getIndex()),
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

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    // Use ignoreRealtimeUpdates=true, mirroring the production flag in TransitRouter
    var result = new StartOnBoardAccessResolver(env.raptorRequestData(true)).resolve(
      tripAndServiceDate,
      List.of(STOP_B.getIndex()),
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
  void throwsOnRingLineWithSingleIndex() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_A, "10:15")
    ).build();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var patternSearch = env.raptorRequestData();
    assertThrows(RoutingValidationException.class, () ->
      new StartOnBoardAccessResolver(patternSearch).resolve(
        tripAndServiceDate,
        List.of(STOP_A.getIndex()),
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

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var patternSearch = env.raptorRequestData();

    // First occurrence of STOP_A at 10:00
    var firstOccurrence = ServiceDateUtils.asStartOfService(SERVICE_DATE, TIME_ZONE)
      .plusSeconds(10 * 3600)
      .toInstant();
    var result1 = new StartOnBoardAccessResolver(patternSearch).resolve(
      tripAndServiceDate,
      List.of(STOP_A.getIndex()),
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
      List.of(STOP_A.getIndex()),
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

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    // findPattern(trip, SERVICE_DATE) returns realtimePattern (not in index),
    // falls back to findPattern(trip) which returns scheduledPattern (in index)
    var result = new StartOnBoardAccessResolver(patternSearch).resolve(
      tripAndServiceDate,
      List.of(STOP_B.getIndex()),
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
   * When multiple stop indices are provided (e.g. all child stops of a station), the resolver
   * picks the one that the trip actually visits.
   */
  @Test
  void resolveWithMultipleStopIndicesPicksVisitedStop() {
    var stopA1 = ENV_BUILDER.stop("A1");
    var stopA2 = ENV_BUILDER.stop("A2");
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(stopA2, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    // Pass both A1 and A2; only A2 is in the trip, so position 0 should be found
    var result = new StartOnBoardAccessResolver(env.raptorRequestData()).resolve(
      tripAndServiceDate,
      List.of(stopA1.getIndex(), stopA2.getIndex()),
      null,
      TIME_ZONE
    );
    assertEquals(0, result.tripBoarding().stopPositionInPattern());
    assertEquals(10 * 3600, result.boardingTime());
  }

  /**
   * When empty stop indices are provided (e.g. station without any child stops), the resolver
   * picks the one that the trip actually visits.
   */
  @Test
  void throwsWhenEmptyStopIndices() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    assertThrows(IllegalArgumentException.class, () ->
      new StartOnBoardAccessResolver(env.raptorRequestData()).resolve(
        tripAndServiceDate,
        List.of(),
        null,
        TIME_ZONE
      )
    );
  }

  /**
   * When none of the provided stop indices appear in the trip, the resolver throws.
   */
  @Test
  void throwsWhenNoneOfStopIndicesMatchTripPattern() {
    var stopA1 = ENV_BUILDER.stop("A1");
    var stopA2 = ENV_BUILDER.stop("A2");
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(stopA2, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    // A1 is not visited by the trip
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var patternSearch = env.raptorRequestData();
    assertThrows(IllegalArgumentException.class, () ->
      new StartOnBoardAccessResolver(patternSearch).resolve(
        tripAndServiceDate,
        List.of(stopA1.getIndex()),
        null,
        TIME_ZONE
      )
    );
  }

  /**
   * When multiple stop indices both appear in the trip (e.g. two child stops of a station on a
   * ring line), passing no departure time is ambiguous and must throw. Passing a departure time
   * disambiguates.
   */
  @Test
  void throwsWithAmbiguousMultipleIndicesWithoutDepartureTime() {
    var stopA1 = ENV_BUILDER.stop("A1");
    var stopA2 = ENV_BUILDER.stop("A2");
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1")
        .addStop(stopA1, "10:00")
        .addStop(STOP_B, "10:05")
        .addStop(stopA2, "10:15")
        .addStop(STOP_C, "10:20")
    ).build();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var patternSearch = env.raptorRequestData();

    // Without departure time — both A1 (pos 0) and A2 (pos 2) match, ambiguous
    assertThrows(RoutingValidationException.class, () ->
      new StartOnBoardAccessResolver(patternSearch).resolve(
        tripAndServiceDate,
        List.of(stopA1.getIndex(), stopA2.getIndex()),
        null,
        TIME_ZONE
      )
    );

    // With departure time for A1 at 10:00 — should find position 0
    var firstOccurrence = ServiceDateUtils.asStartOfService(SERVICE_DATE, TIME_ZONE)
      .plusSeconds(10 * 3600)
      .toInstant();
    var result1 = new StartOnBoardAccessResolver(patternSearch).resolve(
      tripAndServiceDate,
      List.of(stopA1.getIndex(), stopA2.getIndex()),
      firstOccurrence,
      TIME_ZONE
    );
    assertEquals(0, result1.tripBoarding().stopPositionInPattern());
    assertEquals(10 * 3600, result1.boardingTime());

    // With departure time for A2 at 10:15 — should find position 2
    var secondOccurrence = ServiceDateUtils.asStartOfService(SERVICE_DATE, TIME_ZONE)
      .plusSeconds(10 * 3600 + 15 * 60)
      .toInstant();
    var result2 = new StartOnBoardAccessResolver(patternSearch).resolve(
      tripAndServiceDate,
      List.of(stopA1.getIndex(), stopA2.getIndex()),
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

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    // Should succeed by falling back to scheduledTimetable.getPattern() (the original)
    var result = new StartOnBoardAccessResolver(patternSearch).resolve(
      tripAndServiceDate,
      List.of(STOP_B.getIndex()),
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

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    // findPattern(trip, date) → realtimePattern (not in index)
    // findPattern(trip) → copiedPattern (not in index)
    // copiedPattern.scheduledTimetable.getPattern() → originalPattern (in index!)
    var result = new StartOnBoardAccessResolver(patternSearch).resolve(
      tripAndServiceDate,
      List.of(STOP_B.getIndex()),
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

    // Compute the aimed departure instant using start-of-service (noon-minus-12h),
    // which is what a correct client would send
    var aimedDeparture = ServiceDateUtils.asStartOfService(dstDate, dstZone)
      .plusSeconds(10 * 3600 + 5 * 60)
      .toInstant();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), dstDate);
    var result = new StartOnBoardAccessResolver(env.raptorRequestData()).resolve(
      tripAndServiceDate,
      List.of(stopB.getIndex()),
      aimedDeparture,
      dstZone
    );

    assertEquals(1, result.tripBoarding().stopPositionInPattern());
    assertEquals(10 * 3600 + 5 * 60, result.boardingTime());
  }

  /**
   * Passing a stop index where boarding is not allowed should throw.
   */
  @Test
  void throwsWhenBoardingNotPossibleAtStop() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1")
        .addStop(STOP_A, "10:00")
        .addStop(STOP_B, "10:05", "10:05", PickDrop.NONE, PickDrop.SCHEDULED)
        .addStop(STOP_C, "10:10")
    ).build();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var patternSearch = env.raptorRequestData();
    assertThrows(IllegalArgumentException.class, () ->
      new StartOnBoardAccessResolver(patternSearch).resolve(
        tripAndServiceDate,
        List.of(STOP_B.getIndex()),
        null,
        TIME_ZONE
      )
    );
  }

  /**
   * Passing a stop index where boarding is not allowed should throw, even with an aimed departure
   * time.
   */
  @Test
  void throwsWhenBoardingNotPossibleAtStopWithAimedDepartureTime() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1")
        .addStop(STOP_A, "10:00")
        .addStop(STOP_B, "10:05", "10:05", PickDrop.NONE, PickDrop.SCHEDULED)
        .addStop(STOP_C, "10:10")
    ).build();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var patternSearch = env.raptorRequestData();
    var aimedDeparture = ServiceDateUtils.asStartOfService(SERVICE_DATE, TIME_ZONE)
      .plusSeconds(10 * 3600 + 5 * 60)
      .toInstant();
    assertThrows(IllegalArgumentException.class, () ->
      new StartOnBoardAccessResolver(patternSearch).resolve(
        tripAndServiceDate,
        List.of(STOP_B.getIndex()),
        aimedDeparture,
        TIME_ZONE
      )
    );
  }
}
