package org.opentripplanner.updater.trip.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model._data.FeedScopedIdForTestFactory;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.updater.trip.StopResolver;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.StopReference;
import org.opentripplanner.updater.trip.model.TimeUpdate;

class HandlerUtilsTest {

  private static final ZoneId ZONE_ID = ZoneId.of("Europe/Oslo");
  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 5, 7);
  private static final String FEED_ID = FeedScopedIdForTestFactory.FEED_ID;

  private TransitTestEnvironment env;
  private RegularStop stopA;
  private RegularStop stopB;
  private Trip trip;
  private StopResolver stopResolver;

  @BeforeEach
  void setUp() {
    var builder = TransitTestEnvironment.of().addStops("A", "B");
    stopA = builder.stop("A");
    stopB = builder.stop("B");

    env = builder
      .addTrip(TripInput.of("test-trip").addStop(stopA, "10:00").addStop(stopB, "10:30"))
      .build();

    trip = env.transitService().getTrip(new FeedScopedId(FEED_ID, "test-trip"));
    stopResolver = new StopResolver(env.transitService());
  }

  /**
   * Tests that when only departure times are provided (no arrivals),
   * the arrival time is set to the departure time for each stop.
   * This matches the old StopTimesMapper behavior: aimedArrivalTime ?? aimedDepartureTime
   */
  @Test
  void buildNewStopPatternWithDepartureOnlyTimes() {
    int dep1Seconds = 12 * 3600;
    int dep2Seconds = 12 * 3600 + 10 * 60;

    var stopUpdates = List.of(
      ParsedStopTimeUpdate.builder(StopReference.ofStopId(stopA.getId()))
        .withDepartureUpdate(TimeUpdate.ofAbsolute(dep1Seconds + 60, dep1Seconds))
        .build(),
      ParsedStopTimeUpdate.builder(StopReference.ofStopId(stopB.getId()))
        .withDepartureUpdate(TimeUpdate.ofAbsolute(dep2Seconds + 60, dep2Seconds))
        .build()
    );

    var result = HandlerUtils.buildNewStopPattern(
      trip,
      stopUpdates,
      stopResolver,
      SERVICE_DATE,
      ZONE_ID
    );

    assertTrue(result.isSuccess(), "Expected success but got: " + result);

    var stopTimes = result.successValue().stopTimes();
    assertEquals(2, stopTimes.size());

    // First stop: arrival = departure (fallback from departure when no arrival)
    var firstStop = stopTimes.get(0);
    assertEquals(
      dep1Seconds,
      firstStop.getArrivalTime(),
      "First stop arrival should equal departure"
    );
    assertEquals(dep1Seconds, firstStop.getDepartureTime(), "First stop departure should be set");

    // Second stop (last): arrival = departure (fallback), and departure = arrival (last stop rule)
    var secondStop = stopTimes.get(1);
    assertEquals(
      dep2Seconds,
      secondStop.getArrivalTime(),
      "Second stop arrival should equal its departure"
    );
    assertEquals(
      dep2Seconds,
      secondStop.getDepartureTime(),
      "Second stop departure should equal arrival (last stop)"
    );
  }

  /**
   * Tests that when only arrival times are provided (no departures),
   * the last stop's departure time is set to the arrival time.
   */
  @Test
  void buildNewStopPatternWithArrivalOnlyTimes() {
    int arr1Seconds = 12 * 3600;
    int arr2Seconds = 12 * 3600 + 10 * 60;

    var stopUpdates = List.of(
      ParsedStopTimeUpdate.builder(StopReference.ofStopId(stopA.getId()))
        .withArrivalUpdate(TimeUpdate.ofAbsolute(arr1Seconds + 60, arr1Seconds))
        .build(),
      ParsedStopTimeUpdate.builder(StopReference.ofStopId(stopB.getId()))
        .withArrivalUpdate(TimeUpdate.ofAbsolute(arr2Seconds + 60, arr2Seconds))
        .build()
    );

    var result = HandlerUtils.buildNewStopPattern(
      trip,
      stopUpdates,
      stopResolver,
      SERVICE_DATE,
      ZONE_ID
    );

    assertTrue(result.isSuccess(), "Expected success but got: " + result);

    var stopTimes = result.successValue().stopTimes();
    assertEquals(2, stopTimes.size());

    // First stop: arrival set, departure should equal arrival
    var firstStop = stopTimes.get(0);
    assertEquals(arr1Seconds, firstStop.getArrivalTime());
    assertEquals(
      arr1Seconds,
      firstStop.getDepartureTime(),
      "First stop departure should equal arrival"
    );

    // Last stop: arrival set, departure should equal arrival
    var lastStop = stopTimes.get(1);
    assertEquals(arr2Seconds, lastStop.getArrivalTime());
    assertEquals(
      arr2Seconds,
      lastStop.getDepartureTime(),
      "Last stop departure should equal arrival"
    );
  }
}
