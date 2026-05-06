package org.opentripplanner.routing.algorithm.raptoradapter.router.startonboardaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opentripplanner.core.model.id.FeedScopedIdForTestFactory.id;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opentripplanner.routing.error.RoutingValidationException;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
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

  private static Instant toInstant(
    int secondsSinceStartOfService,
    LocalDate serviceDate,
    ZoneId timeZone
  ) {
    return ServiceDateUtils.asStartOfService(serviceDate, timeZone)
      .plusSeconds(secondsSinceStartOfService)
      .toInstant();
  }

  private static Instant toInstant(int secondsSinceStartOfService) {
    return toInstant(secondsSinceStartOfService, SERVICE_DATE, TIME_ZONE);
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

    // Aimed departure time is given with respect to the date and time zone of the client
    var aimedDeparture = toInstant(10 * 3600 + 5 * 60, dstDate, dstZone);

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

  @Nested
  class RingLine {

    @Test
    void resolvesOnRingLineWithDepartureTime() {
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
    void throwsOnRingLineWithoutDepartureTime() {
      var env = ENV_BUILDER.addTrip(
        TripInput.of("T1")
          .addStop(STOP_A, "10:00")
          .addStop(STOP_B, "10:05")
          .addStop(STOP_A, "10:15")
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
  }
}
