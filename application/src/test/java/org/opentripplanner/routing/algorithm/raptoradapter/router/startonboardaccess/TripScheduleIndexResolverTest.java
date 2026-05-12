package org.opentripplanner.routing.algorithm.raptoradapter.router.startonboardaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.utils.time.ServiceDateUtils;

class TripScheduleIndexResolverTest {

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

  @Test
  void resolvesSimpleOnBoardAccess() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var result = new TripScheduleIndexResolver(env.raptorRoutingRequestTransitData()).resolve(
      tripAndServiceDate,
      List.of(STOP_B.getIndex())
    );

    var routingPattern = env.tripData("T1").scheduledTripPattern().getRoutingTripPattern();

    assertEquals(routingPattern.patternIndex(), result.routeIndex());
    assertEquals(0, result.tripScheduleIndex());
  }

  @Test
  void resolvesFirstStop() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    var result = new TripScheduleIndexResolver(env.raptorRoutingRequestTransitData()).resolve(
      tripAndServiceDate,
      List.of(STOP_A.getIndex())
    );

    var routingPattern = env.tripData("T1").scheduledTripPattern().getRoutingTripPattern();

    assertEquals(routingPattern.patternIndex(), result.routeIndex());
    assertEquals(0, result.tripScheduleIndex());
  }

  /**
   * When multiple stop indices are provided (e.g. all child stops of a station), the resolver
   * picks the one that the trip actually visits.
   */
  @Test
  void resolvesWithMultipleStopIndicesPicksVisitedStop() {
    var stopA1 = ENV_BUILDER.stop("A1");
    var stopA2 = ENV_BUILDER.stop("A2");
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(stopA2, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    // Pass both A1 and A2. Only A2 is in the trip, but the pattern should be found nonetheless.
    var result = new TripScheduleIndexResolver(env.raptorRoutingRequestTransitData()).resolve(
      tripAndServiceDate,
      List.of(stopA1.getIndex(), stopA2.getIndex())
    );

    var routingPattern = env.tripData("T1").scheduledTripPattern().getRoutingTripPattern();
    assertEquals(routingPattern.patternIndex(), result.routeIndex());
    assertEquals(0, result.tripScheduleIndex());
  }

  /**
   * When empty stop indices are provided (e.g. station without any child stops), the resolver
   * throws.
   */
  @Test
  void throwsWhenEmptyStopIndices() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(STOP_A, "10:00").addStop(STOP_B, "10:05").addStop(STOP_C, "10:10")
    ).build();

    var tripAndServiceDate = new TripAndServiceDate(env.tripData("T1").trip(), SERVICE_DATE);
    assertThrows(IllegalArgumentException.class, () ->
      new TripScheduleIndexResolver(env.raptorRoutingRequestTransitData()).resolve(
        tripAndServiceDate,
        List.of()
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
    var patternSearch = env.raptorRoutingRequestTransitData();
    assertThrows(IllegalArgumentException.class, () ->
      new TripScheduleIndexResolver(patternSearch).resolve(
        tripAndServiceDate,
        List.of(stopA1.getIndex())
      )
    );
  }
}
