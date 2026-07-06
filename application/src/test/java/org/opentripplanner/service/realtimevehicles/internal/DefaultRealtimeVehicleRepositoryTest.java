package org.opentripplanner.service.realtimevehicles.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.street.geometry.WgsCoordinate.GREENWICH;
import static org.opentripplanner.transit.model._data.TimetableRepositoryForTest.route;
import static org.opentripplanner.transit.model._data.TimetableRepositoryForTest.tripPattern;

import com.google.common.collect.ImmutableListMultimap;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.service.realtimevehicles.model.RealtimeVehicle;
import org.opentripplanner.transit.model._data.TimetableRepositoryForTest;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.network.TripPattern;

class DefaultRealtimeVehicleRepositoryTest {

  private static final Route ROUTE = route("r1").build();
  private static final TimetableRepositoryForTest MODEL = TimetableRepositoryForTest.of();
  private static final StopPattern STOP_PATTERN = TimetableRepositoryForTest.stopPattern(
    MODEL.stop("1").build(),
    MODEL.stop("2").build()
  );
  private static final TripPattern PATTERN1 = tripPattern("p1", ROUTE)
    .withStopPattern(STOP_PATTERN)
    .build();
  private static final TripPattern PATTERN2 = tripPattern(
    "p2",
    ROUTE.copy().withId(new FeedScopedId("f2", "r2")).build()
  )
    .withId(new FeedScopedId("f2", "p2"))
    .withStopPattern(STOP_PATTERN)
    .build();
  private static final Instant TIME = Instant.ofEpochSecond(1000);
  private static final RealtimeVehicle VEHICLE = RealtimeVehicle.builder()
    .withTime(TIME)
    .withCoordinates(GREENWICH)
    .build();

  private static final String FEED_ID = PATTERN1.getFeedId();

  @Test
  void empty() {
    var repository = new DefaultRealtimeVehicleRepository();
    assertThat(repository.getRealtimeVehicles(PATTERN1)).isEmpty();
  }

  @Test
  void lookupByPattern() {
    var repository = new DefaultRealtimeVehicleRepository();
    repository.setRealtimeVehiclesForFeed(FEED_ID, ImmutableListMultimap.of(PATTERN1, VEHICLE));
    assertEquals(List.of(VEHICLE), repository.getRealtimeVehicles(PATTERN1));
  }

  @Test
  void clearFeed() {
    var repository = new DefaultRealtimeVehicleRepository();
    repository.setRealtimeVehiclesForFeed(FEED_ID, ImmutableListMultimap.of(PATTERN1, VEHICLE));
    repository.setRealtimeVehiclesForFeed(FEED_ID, ImmutableListMultimap.of());
    assertThat(repository.getRealtimeVehicles(PATTERN1)).isEmpty();
  }

  @Test
  void keepOtherFeeds() {
    var repository = new DefaultRealtimeVehicleRepository();
    repository.setRealtimeVehiclesForFeed(FEED_ID, ImmutableListMultimap.of(PATTERN1, VEHICLE));
    repository.setRealtimeVehiclesForFeed(
      PATTERN2.getFeedId(),
      ImmutableListMultimap.of(PATTERN2, VEHICLE)
    );
    repository.setRealtimeVehiclesForFeed(FEED_ID, ImmutableListMultimap.of());
    assertEquals(List.of(VEHICLE), repository.getRealtimeVehicles(PATTERN2));
  }
}
