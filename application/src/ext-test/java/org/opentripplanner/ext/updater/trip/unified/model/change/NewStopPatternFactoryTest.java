package org.opentripplanner.ext.updater.trip.unified.model.change;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.core.model.id.FeedScopedIdForTestFactory;
import org.opentripplanner.ext.updater.trip.unified.model.ServiceTime;
import org.opentripplanner.ext.updater.trip.unified.model.command.ParsedStopTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.StopReference;
import org.opentripplanner.ext.updater.trip.unified.model.command.TimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.policy.FormatPolicy;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;

class NewStopPatternFactoryTest {

  private static final ZoneId ZONE_ID = ZoneId.of("Europe/Oslo");
  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 5, 7);
  private static final String FEED_ID = FeedScopedIdForTestFactory.FEED_ID;

  private TransitTestEnvironment env;
  private RegularStop stopA;
  private RegularStop stopB;
  private Trip trip;

  @BeforeEach
  void setUp() {
    var builder = TransitTestEnvironment.of().addStops("A", "B");
    stopA = builder.stop("A");
    stopB = builder.stop("B");

    env = builder
      .addTrip(TripInput.of("test-trip").addStop(stopA, "10:00").addStop(stopB, "10:30"))
      .build();

    trip = env.transitService().getTrip(new FeedScopedId(FEED_ID, "test-trip"));
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
      ResolvedStopTimeUpdate.of(
        ParsedStopTimeUpdate.builder(StopReference.ofStopId(stopA.getId()))
          .withDepartureUpdate(
            TimeUpdate.ofAbsolute(
              ServiceTime.ofSecondsPastMidnight(dep1Seconds + 60),
              ServiceTime.ofSecondsPastMidnight(dep1Seconds)
            )
          )
          .build(),
        SERVICE_DATE,
        ZONE_ID,
        ResolvedStopReference.ofReferencedStop(stopA)
      ),
      ResolvedStopTimeUpdate.of(
        ParsedStopTimeUpdate.builder(StopReference.ofStopId(stopB.getId()))
          .withDepartureUpdate(
            TimeUpdate.ofAbsolute(
              ServiceTime.ofSecondsPastMidnight(dep2Seconds + 60),
              ServiceTime.ofSecondsPastMidnight(dep2Seconds)
            )
          )
          .build(),
        SERVICE_DATE,
        ZONE_ID,
        ResolvedStopReference.ofReferencedStop(stopB)
      )
    );

    var stopTimesAndPattern = NewStopPatternFactory.buildNewStopPattern(
      trip,
      stopUpdates,
      FormatPolicy.siri()
    );

    var stopTimes = stopTimesAndPattern.stopTimes();
    assertEquals(2, stopTimes.size());

    // First stop: arrival = departure (fallback from departure when no arrival)
    var firstStop = stopTimes.get(0);
    assertEquals(
      dep1Seconds,
      firstStop.getArrivalTime(),
      "First stop arrival should equal departure"
    );
    assertEquals(dep1Seconds, firstStop.getDepartureTime(), "First stop departure should be set");

    // Second stop (last): arrival = departure (fallback from departure when no arrival)
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
      ResolvedStopTimeUpdate.of(
        ParsedStopTimeUpdate.builder(StopReference.ofStopId(stopA.getId()))
          .withArrivalUpdate(
            TimeUpdate.ofAbsolute(
              ServiceTime.ofSecondsPastMidnight(arr1Seconds + 60),
              ServiceTime.ofSecondsPastMidnight(arr1Seconds)
            )
          )
          .build(),
        SERVICE_DATE,
        ZONE_ID,
        ResolvedStopReference.ofReferencedStop(stopA)
      ),
      ResolvedStopTimeUpdate.of(
        ParsedStopTimeUpdate.builder(StopReference.ofStopId(stopB.getId()))
          .withArrivalUpdate(
            TimeUpdate.ofAbsolute(
              ServiceTime.ofSecondsPastMidnight(arr2Seconds + 60),
              ServiceTime.ofSecondsPastMidnight(arr2Seconds)
            )
          )
          .build(),
        SERVICE_DATE,
        ZONE_ID,
        ResolvedStopReference.ofReferencedStop(stopB)
      )
    );

    var stopTimesAndPattern = NewStopPatternFactory.buildNewStopPattern(
      trip,
      stopUpdates,
      FormatPolicy.siri()
    );

    var stopTimes = stopTimesAndPattern.stopTimes();
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

  /** GTFS-RT marks the calls of a new pattern as timepoints, SIRI-ET leaves them unmarked. */
  @Test
  void timepointFollowsTheFormat() {
    var stopUpdates = List.of(callAt(stopA, 12 * 3600), callAt(stopB, 12 * 3600 + 10 * 60));

    var gtfsRt = NewStopPatternFactory.buildNewStopPattern(
      trip,
      stopUpdates,
      FormatPolicy.gtfsRt(ForwardsDelayPropagationType.NONE, BackwardsDelayPropagationType.NONE)
    );
    assertTrue(
      gtfsRt
        .stopTimes()
        .stream()
        .allMatch(stopTime -> stopTime.getTimepoint() == 1)
    );

    var siri = NewStopPatternFactory.buildNewStopPattern(trip, stopUpdates, FormatPolicy.siri());
    assertTrue(
      siri
        .stopTimes()
        .stream()
        .noneMatch(stopTime -> stopTime.getTimepoint() == 1)
    );
  }

  private ResolvedStopTimeUpdate callAt(RegularStop stop, int departureSeconds) {
    return ResolvedStopTimeUpdate.of(
      ParsedStopTimeUpdate.builder(StopReference.ofStopId(stop.getId()))
        .withDepartureUpdate(
          TimeUpdate.ofAbsolute(
            ServiceTime.ofSecondsPastMidnight(departureSeconds),
            ServiceTime.ofSecondsPastMidnight(departureSeconds)
          )
        )
        .build(),
      SERVICE_DATE,
      ZONE_ID,
      ResolvedStopReference.ofReferencedStop(stop)
    );
  }
}
