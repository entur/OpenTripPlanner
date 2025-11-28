package org.opentripplanner.updater.trip.gtfs;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.transit.model._data.TimetableRepositoryForTest;
import org.opentripplanner.transit.model.framework.DeduplicatorService;
import org.opentripplanner.transit.model.timetable.ScheduledTripTimes;
import org.opentripplanner.transit.model.timetable.StopRealTimeState;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimesFactory;
import org.opentripplanner.utils.collection.ListUtils;

public class BackwardsDelayInterpolatorTest {

  static final Trip TRIP = TimetableRepositoryForTest.trip("TRIP_ID").build();
  static final int STOP_COUNT = 5;
  static final ScheduledTripTimes SCHEDULED_TRIP_TIMES = TripTimesFactory.tripTimes(
    TRIP,
    TimetableRepositoryForTest.of().stopTimesEvery5Minutes(STOP_COUNT, TRIP, "00:00"),
    DeduplicatorService.NOOP
  );
  public static final int SIX_MINUTES_EARLY = -6 * 60;

  private static List<BackwardsDelayInterpolator> requiredPropagators() {
    return List.of(
      new BackwardsDelayRequiredInterpolator(true),
      new BackwardsDelayRequiredInterpolator(false)
    );
  }

  @ParameterizedTest
  @MethodSource("requiredPropagators")
  void precedingNoDataWithEarlyArrival(BackwardsDelayInterpolator interpolator) {
    var builder = SCHEDULED_TRIP_TIMES.createRealTimeFromScheduledTimes()
      .withNoData(0)
      .withNoData(1)
      .withNoData(2)
      .withArrivalDelay(3, SIX_MINUTES_EARLY)
      .withDepartureDelay(3, SIX_MINUTES_EARLY);

    assertEquals(OptionalInt.of(3), interpolator.propagateBackwards(builder));

    assertEquals(-60, builder.getArrivalDelay(2));
    assertEquals(-60, builder.getDepartureDelay(2));
    assertEquals(StopRealTimeState.NO_DATA, builder.getStopRealTimeState(2));
    List.of(0, 1).forEach(i -> {
      assertEquals(0, builder.getArrivalDelay(i));
      assertEquals(0, builder.getDepartureDelay(i));
      assertEquals(StopRealTimeState.NO_DATA, builder.getStopRealTimeState(i));
    });
  }

  private static List<BackwardsDelayInterpolator> allPropagators() {
    return ListUtils.combine(
      requiredPropagators(),
      List.of(new BackwardsDelayAlwaysInterpolator())
    );
  }

  @ParameterizedTest
  @MethodSource("allPropagators")
  void noDataOnly(BackwardsDelayInterpolator interpolator) {
    var builder = SCHEDULED_TRIP_TIMES.createRealTimeFromScheduledTimes()
      .withNoData(0)
      .withNoData(1)
      .withNoData(2)
      .withNoData(3)
      .withNoData(4);

    assertThat(interpolator.propagateBackwards(builder)).isEmpty();

    List.of(0, builder.numberOfStops() - 1).forEach(i -> {
      assertEquals(0, builder.getArrivalDelay(i));
      assertEquals(0, builder.getDepartureDelay(i));
      assertEquals(StopRealTimeState.NO_DATA, builder.getStopRealTimeState(i));
    });
  }
}
