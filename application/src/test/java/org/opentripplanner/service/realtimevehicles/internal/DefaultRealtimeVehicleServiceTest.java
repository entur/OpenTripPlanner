package org.opentripplanner.service.realtimevehicles.internal;

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
}
