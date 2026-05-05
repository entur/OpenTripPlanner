package org.opentripplanner.routing.algorithm.raptoradapter.router.onboardaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opentripplanner.core.model.id.FeedScopedIdForTestFactory.id;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.opentripplanner.routing.error.RoutingValidationException;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.utils.time.ServiceDateUtils;

class StartOnBoardBoardingTimeResolverTest {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 11, 1);
  private static final ZoneId TIME_ZONE = ZoneId.of("GMT");

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of(
    SERVICE_DATE,
    TIME_ZONE
  );
  private final RegularStop STOP_A = ENV_BUILDER.stop("A");
  private final RegularStop STOP_B = ENV_BUILDER.stop("B");
  private final RegularStop STOP_C = ENV_BUILDER.stop("C");

  private static Instant toInstant(int secondsSinceStartOfService) {
    return ServiceDateUtils.asStartOfService(SERVICE_DATE, TIME_ZONE)
      .plusSeconds(secondsSinceStartOfService)
      .toInstant();
  }

  private static long expectedEpochSecond(int secondsSinceStartOfService) {
    return (
      ServiceDateUtils.asStartOfService(SERVICE_DATE, TIME_ZONE).toEpochSecond() +
      secondsSinceStartOfService
    );
  }

  @Test
  void resolvesSimpleBoardingTime() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var result = new StartOnBoardBoardingTimeResolver(env.transitService()).resolve(
      tripAndServiceDate,
      STOP_B.getId(),
      null,
      TIME_ZONE
    );

    assertEquals(expectedEpochSecond(10 * 3600 + 5 * 60), result.getEpochSecond());
  }

  @Test
  void resolvesWithAimedDepartureTimeOnUniqueStop() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var result = new StartOnBoardBoardingTimeResolver(env.transitService()).resolve(
      tripAndServiceDate,
      STOP_B.getId(),
      toInstant(10 * 3600 + 5 * 60),
      TIME_ZONE
    );

    assertEquals(expectedEpochSecond(10 * 3600 + 5 * 60), result.getEpochSecond());
  }

  @Test
  void throwsOnWrongAimedDepartureTime() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    // STOP_B departs at 10:05, but we provide 10:00 — should fail
    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    assertThrows(IllegalArgumentException.class, () ->
      new StartOnBoardBoardingTimeResolver(env.transitService()).resolve(
        tripAndServiceDate,
        STOP_B.getId(),
        toInstant(10 * 3600),
        TIME_ZONE
      )
    );
  }

  @Test
  void throwsOnLastStop() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    assertThrows(IllegalArgumentException.class, () ->
      new StartOnBoardBoardingTimeResolver(env.transitService()).resolve(
        tripAndServiceDate,
        STOP_C.getId(),
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
    assertThrows(IllegalArgumentException.class, () ->
      new StartOnBoardBoardingTimeResolver(env.transitService()).resolve(
        tripAndServiceDate,
        STOP_C.getId(),
        toInstant(10 * 3600 + 10 * 60),
        TIME_ZONE
      )
    );
  }

  @Test
  void throwsOnRingLineWithStopIdOnly() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_A, "10:15")
    ).build();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    assertThrows(RoutingValidationException.class, () ->
      new StartOnBoardBoardingTimeResolver(env.transitService()).resolve(
        tripAndServiceDate,
        STOP_A.getId(),
        null,
        TIME_ZONE
      )
    );
  }

  @Test
  void resolvesRingLineWithAimedDepartureTime() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1")
        .addStop(STOP_A, "10:00")
        .addStop(STOP_B, "10:05")
        .addStop(STOP_A, "10:15")
        .addStop(STOP_C, "10:20")
    ).build();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);

    // First occurrence of STOP_A at 10:00
    var result1 = new StartOnBoardBoardingTimeResolver(env.transitService()).resolve(
      tripAndServiceDate,
      STOP_A.getId(),
      toInstant(10 * 3600),
      TIME_ZONE
    );
    assertEquals(expectedEpochSecond(10 * 3600), result1.getEpochSecond());

    // Second occurrence of STOP_A at 10:15
    var result2 = new StartOnBoardBoardingTimeResolver(env.transitService()).resolve(
      tripAndServiceDate,
      STOP_A.getId(),
      toInstant(10 * 3600 + 15 * 60),
      TIME_ZONE
    );
    assertEquals(expectedEpochSecond(10 * 3600 + 15 * 60), result2.getEpochSecond());
  }

  @Test
  void resolvesByStationId() {
    var stopA = ENV_BUILDER.stopAtStation("SA1", "StationA");
    var stopB = ENV_BUILDER.stopAtStation("SB1", "StationB");
    var stopC = ENV_BUILDER.stopAtStation("SC1", "StationC");
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(stopA, "10:00").addStop(stopB, "10:05").addStop(stopC, "10:10")
    ).build();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var result = new StartOnBoardBoardingTimeResolver(env.transitService()).resolve(
      tripAndServiceDate,
      id("StationB"),
      null,
      TIME_ZONE
    );

    assertEquals(expectedEpochSecond(10 * 3600 + 5 * 60), result.getEpochSecond());
  }

  /**
   * Verify that the resolver works when a realtime updater has modified the trip's stop pattern,
   * moving it to a new TripPattern whose scheduled timetable is empty. The resolver must fall back
   * to the realtime timetable to find the trip's departure time.
   */
  @Test
  void resolvesWithRealtimeModifiedPattern() {
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
      .updateBuffer(RealTimeTripUpdate.of(realtimePattern, tripTimes, SERVICE_DATE).build());
    env.timetableSnapshotManager().purgeAndCommit();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var result = new StartOnBoardBoardingTimeResolver(env.transitService()).resolve(
      tripAndServiceDate,
      STOP_B.getId(),
      null,
      TIME_ZONE
    );

    assertEquals(expectedEpochSecond(10 * 3600 + 5 * 60), result.getEpochSecond());
  }

  /**
   * When a TripPattern is copied (as happens during graph build), the copy gets a new
   * RoutingTripPattern. The resolver must find the trip's times in the realtime timetable
   * (which maps the trip to the copied pattern's timetable) rather than in the empty
   * scheduled timetable of the copy.
   */
  @Test
  void resolvesWithCopiedPattern() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var tripData = env.tripData("T1");
    var originalPattern = tripData.scheduledTripPattern();

    // Copy pattern with reused scheduledTimetable (same as TransitDataImportBuilder)
    var copiedPattern = originalPattern
      .copy()
      .withName("copied")
      .withScheduledTimeTable(originalPattern.getScheduledTimetable())
      .build();
    env.timetableRepository().addTripPattern(copiedPattern.getId(), copiedPattern);
    env.timetableRepository().index();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var result = new StartOnBoardBoardingTimeResolver(env.transitService()).resolve(
      tripAndServiceDate,
      STOP_B.getId(),
      null,
      TIME_ZONE
    );

    assertEquals(expectedEpochSecond(10 * 3600 + 5 * 60), result.getEpochSecond());
  }

  /**
   * When the clocks move forward in spring due to DST, midnight and noon-minus-12h
   * (start-of-service) differ by one hour. The aimed departure time conversion uses
   * start-of-service (noon-minus-12h) as the reference, not midnight, because TripTimes are
   * relative to start-of-service.
   */
  @Test
  void resolvesWithAimedDepartureTimeOnDstDay() {
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
    var result = new StartOnBoardBoardingTimeResolver(env.transitService()).resolve(
      tripAndServiceDate,
      stopB.getId(),
      aimedDeparture,
      dstZone
    );

    long expectedEpochSecond =
      ServiceDateUtils.asStartOfService(dstDate, dstZone).toEpochSecond() + 10 * 3600 + 5 * 60;
    assertEquals(expectedEpochSecond, result.getEpochSecond());
  }
}
