package org.opentripplanner.service.realtimevehicles.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.opentripplanner.core.model.id.FeedScopedIdForTestFactory.id;
import static org.opentripplanner.street.geometry.WgsCoordinate.GREENWICH;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import com.google.common.collect.ImmutableListMultimap;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.service.realtimevehicles.model.RealtimeVehicle;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.updater.trip.siri.SiriTestHelper;

class DefaultRealtimeVehicleServiceTest {

  private static final RealtimeVehicle VEHICLE = RealtimeVehicle.builder()
    .withTime(Instant.ofEpochSecond(1000))
    .withCoordinates(GREENWICH)
    .build();

  @Test
  void realtimePatternLookupResolvesToScheduledPatternKey() {
    var envBuilder = TransitTestEnvironment.of();
    var stopA = envBuilder.stop("A");
    var stopB = envBuilder.stop("B");
    var stopC = envBuilder.stop("C");
    var tripInput = TripInput.of("trip1")
      .withRoute(envBuilder.route("route1"))
      .withWithTripOnServiceDate("trip1")
      .addStop(stopA, "0:01:00", "0:01:01")
      .addStop(stopB, "0:01:10", "0:01:11")
      .addStop(stopC, "0:01:20", "0:01:21");
    var env = envBuilder.addTrip(tripInput).build();
    var siri = SiriTestHelper.of(env);

    // cancel stop B on the trip - a realtime pattern with a modified stop pattern is created
    var updates = siri
      .etBuilder()
      .withDatedVehicleJourneyRef("trip1")
      .withEstimatedCalls(builder ->
        builder
          .call(stopA)
          .departAimedExpected("00:01:01", "00:01:01")
          .call(stopB)
          .withIsCancellation(true)
          .call(stopC)
          .arriveAimedExpected("00:01:20", "00:01:20")
      )
      .buildEstimatedTimetableDeliveries();
    assertSuccess(siri.applyEstimatedTimetable(updates));

    var transitService = env.transitService();
    var trip = transitService.getTrip(id("trip1"));
    var scheduledPattern = transitService.findPattern(trip);
    var realtimePattern = transitService.findPattern(trip, env.defaultServiceDate());
    assertNotEquals(scheduledPattern, realtimePattern);
    assertEquals(List.of(trip), transitService.listTrips(realtimePattern));

    var repository = new DefaultRealtimeVehicleRepository();
    repository.setRealtimeVehiclesForFeed(
      env.feedId(),
      ImmutableListMultimap.of(scheduledPattern, VEHICLE)
    );
    var service = new DefaultRealtimeVehicleService(repository, transitService);

    // the vehicle is stored under the scheduled pattern only, but can be looked up with either
    // the scheduled or the realtime pattern of the trip
    assertEquals(List.of(VEHICLE), service.getRealtimeVehicles(scheduledPattern));
    assertEquals(List.of(VEHICLE), service.getRealtimeVehicles(realtimePattern));
  }

  @Test
  void sharedRealtimePatternResolvesEachTripToItsOwnScheduledPatternKey() {
    var envBuilder = TransitTestEnvironment.of();
    var stopA = envBuilder.stop("A");
    var stopB = envBuilder.stop("B");
    var stopC = envBuilder.stop("C");
    var trip1Input = TripInput.of("trip1")
      .withRoute(envBuilder.route("route1"))
      .withWithTripOnServiceDate("trip1")
      .addStop(stopA, "0:01:00", "0:01:01")
      .addStop(stopB, "0:01:10", "0:01:11")
      .addStop(stopC, "0:01:20", "0:01:21");
    var trip2Input = TripInput.of("trip2")
      .withRoute(envBuilder.route("route2"))
      .withWithTripOnServiceDate("trip2")
      .addStop(stopA, "0:02:00", "0:02:01")
      .addStop(stopB, "0:02:10", "0:02:11")
      .addStop(stopC, "0:02:20", "0:02:21");
    var env = envBuilder.addTrip(trip1Input).addTrip(trip2Input).build();
    var siri = SiriTestHelper.of(env);

    // cancel stop B on both trips - both produce the same modified stop pattern, so the
    // TripPatternCache (keyed by StopPattern only) returns one realtime pattern shared by both
    // trips, even though they belong to different routes. This is the corner case the PR fixes.
    assertSuccess(
      siri.applyEstimatedTimetable(
        siri
          .etBuilder()
          .withDatedVehicleJourneyRef("trip1")
          .withEstimatedCalls(builder ->
            builder
              .call(stopA)
              .departAimedExpected("00:01:01", "00:01:01")
              .call(stopB)
              .withIsCancellation(true)
              .call(stopC)
              .arriveAimedExpected("00:01:20", "00:01:20")
          )
          .buildEstimatedTimetableDeliveries()
      )
    );
    assertSuccess(
      siri.applyEstimatedTimetable(
        siri
          .etBuilder()
          .withDatedVehicleJourneyRef("trip2")
          .withEstimatedCalls(builder ->
            builder
              .call(stopA)
              .departAimedExpected("00:02:01", "00:02:01")
              .call(stopB)
              .withIsCancellation(true)
              .call(stopC)
              .arriveAimedExpected("00:02:20", "00:02:20")
          )
          .buildEstimatedTimetableDeliveries()
      )
    );

    var transitService = env.transitService();
    var trip1 = transitService.getTrip(id("trip1"));
    var trip2 = transitService.getTrip(id("trip2"));
    var scheduledPattern1 = transitService.findPattern(trip1);
    var scheduledPattern2 = transitService.findPattern(trip2);
    var realtimePattern1 = transitService.findPattern(trip1, env.defaultServiceDate());
    var realtimePattern2 = transitService.findPattern(trip2, env.defaultServiceDate());

    // the two trips are on distinct scheduled patterns but share a single realtime pattern
    assertNotEquals(scheduledPattern1, scheduledPattern2);
    assertEquals(realtimePattern1, realtimePattern2);
    assertThat(transitService.listTrips(realtimePattern1)).containsExactly(trip1, trip2);

    // each vehicle is stored under its own trip's scheduled pattern
    var vehicle1 = RealtimeVehicle.builder()
      .withTime(Instant.ofEpochSecond(1000))
      .withCoordinates(GREENWICH)
      .withTrip(trip1)
      .build();
    var vehicle2 = RealtimeVehicle.builder()
      .withTime(Instant.ofEpochSecond(1000))
      .withCoordinates(GREENWICH)
      .withTrip(trip2)
      .build();
    var repository = new DefaultRealtimeVehicleRepository();
    repository.setRealtimeVehiclesForFeed(
      env.feedId(),
      ImmutableListMultimap.of(scheduledPattern1, vehicle1, scheduledPattern2, vehicle2)
    );
    var service = new DefaultRealtimeVehicleService(repository, transitService);

    // a lookup on each route's scheduled pattern returns only that route's vehicle
    assertEquals(List.of(vehicle1), service.getRealtimeVehicles(scheduledPattern1));
    assertEquals(List.of(vehicle2), service.getRealtimeVehicles(scheduledPattern2));

    // a lookup on the shared realtime pattern resolves both trips back to their scheduled
    // patterns and returns the union of their vehicles - each attributed to the right route
    assertThat(service.getRealtimeVehicles(realtimePattern1)).containsExactly(vehicle1, vehicle2);
  }
}
