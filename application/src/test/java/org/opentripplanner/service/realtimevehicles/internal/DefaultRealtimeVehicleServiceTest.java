package org.opentripplanner.service.realtimevehicles.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.street.geometry.WgsCoordinate.GREENWICH;
import static org.opentripplanner.transit.model._data.TimetableRepositoryForTest.route;
import static org.opentripplanner.transit.model._data.TimetableRepositoryForTest.tripPattern;

import com.google.common.collect.ImmutableListMultimap;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.service.realtimevehicles.model.RealtimeVehicle;
import org.opentripplanner.transit.model._data.TimetableRepositoryForTest;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TimetableRepository;

class DefaultRealtimeVehicleServiceTest {

  private static final Route ROUTE = route("r1").build();
  private static final TimetableRepositoryForTest MODEL = TimetableRepositoryForTest.of();
  private static final StopPattern STOP_PATTERN = TimetableRepositoryForTest.stopPattern(
    MODEL.stop("1").build(),
    MODEL.stop("2").build()
  );
  private static final TripPattern PATTERN1 = tripPattern("p1", ROUTE)
    .withStopPattern(STOP_PATTERN)
    .build();
  private static final RealtimeVehicle VEHICLE = RealtimeVehicle.builder()
    .withTime(Instant.ofEpochSecond(1000))
    .withCoordinates(GREENWICH)
    .build();

  private static final String FEED_ID = PATTERN1.getFeedId();

  @Test
  void realtimeAddedPatternLookup() {
    var repository = new DefaultRealtimeVehicleRepository();
    repository.setRealtimeVehiclesForFeed(FEED_ID, ImmutableListMultimap.of(PATTERN1, VEHICLE));

    var realtimePattern = tripPattern("realtime-added", ROUTE)
      .withStopPattern(STOP_PATTERN)
      .withOriginalTripPattern(PATTERN1)
      .withRealTimeStopPatternModified()
      .build();
    var service = new DefaultRealtimeVehicleService(
      repository,
      new DefaultTransitService(new TimetableRepository())
    );

    // the vehicle can be looked up with either the scheduled or the realtime pattern
    assertEquals(List.of(VEHICLE), service.getRealtimeVehicles(PATTERN1));
    assertEquals(List.of(VEHICLE), service.getRealtimeVehicles(realtimePattern));
  }
}
