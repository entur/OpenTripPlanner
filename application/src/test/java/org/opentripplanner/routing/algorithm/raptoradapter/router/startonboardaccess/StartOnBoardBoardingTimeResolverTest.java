package org.opentripplanner.routing.algorithm.raptoradapter.router.startonboardaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opentripplanner.core.model.id.FeedScopedIdForTestFactory.id;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opentripplanner.routing.error.RoutingValidationException;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;

class StartOnBoardBoardingTimeResolverTest {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 11, 1);

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of(
    SERVICE_DATE,
    ZoneId.of("GMT")
  );
  private final RegularStop STOP_A = ENV_BUILDER.stop("A");
  private final RegularStop STOP_B = ENV_BUILDER.stop("B");
  private final RegularStop STOP_C = ENV_BUILDER.stop("C");

  @Test
  void resolvesSimpleBoardingTime() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var result = new StartOnBoardBoardingTimeResolver(env.transitService()).resolve(
      tripAndServiceDate,
      STOP_B.getId(),
      null
    );

    assertEquals(10 * 3600 + 5 * 60, result);
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
      10 * 3600 + 5 * 60
    );

    assertEquals(10 * 3600 + 5 * 60, result);
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
        10 * 3600
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
        null
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
        10 * 3600 + 10 * 60
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
      null
    );

    assertEquals(10 * 3600 + 5 * 60, result);
  }

  /**
   * On a DST day where clocks spring forward, start-of-service (noon-minus-12h) differs from
   * midnight. The resolver receives seconds already relative to start-of-service, so it resolves
   * correctly regardless of DST — the conversion responsibility lies with the caller.
   */
  @Test
  void resolvesWithAimedDepartureTimeOnDstDay() {
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

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), dstDate);
    var result = new StartOnBoardBoardingTimeResolver(env.transitService()).resolve(
      tripAndServiceDate,
      stopB.getId(),
      10 * 3600 + 5 * 60
    );

    assertEquals(10 * 3600 + 5 * 60, result);
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
        10 * 3600
      );
      assertEquals(10 * 3600, result1);

      // Second occurrence of STOP_A at 10:15
      var result2 = new StartOnBoardBoardingTimeResolver(env.transitService()).resolve(
        tripAndServiceDate,
        STOP_A.getId(),
        10 * 3600 + 15 * 60
      );
      assertEquals(10 * 3600 + 15 * 60, result2);
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
          null
        )
      );
    }
  }
}
