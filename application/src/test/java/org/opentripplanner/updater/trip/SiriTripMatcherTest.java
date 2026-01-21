package org.opentripplanner.updater.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.transit.model._data.FeedScopedIdForTestFactory.id;
import static org.opentripplanner.updater.spi.UpdateError.UpdateErrorType.MULTIPLE_FUZZY_TRIP_MATCHES;
import static org.opentripplanner.updater.spi.UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH;
import static org.opentripplanner.updater.spi.UpdateError.UpdateErrorType.NO_VALID_STOPS;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.StopReference;
import org.opentripplanner.updater.trip.model.TimeUpdate;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripUpdateType;

class SiriTripMatcherTest implements RealtimeTestConstants {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 1, 15);
  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of(SERVICE_DATE);
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stop(STOP_B_ID);
  private final RegularStop STOP_C = ENV_BUILDER.stop(STOP_C_ID);

  @Test
  void matchByVehicleRef() {
    var route = ENV_BUILDER.route("RAIL_ROUTE");
    var tripInput = TripInput.of(TRIP_1_ID)
      .withRoute(route)
      .addStop(STOP_A, "0:10:00", "0:10:00")
      .addStop(STOP_B, "0:20:00", "0:20:00");

    var env = ENV_BUILDER.addTrip(tripInput, tripBuilder ->
      tripBuilder.withMode(TransitMode.RAIL).withNetexInternalPlanningCode("VehicleRef123")
    ).build();
    var matcher = new SiriTripMatcher(env.transitService(), env.feedId());

    var parsedUpdate = createParsedUpdate(
      "VehicleRef123",
      null,
      List.of(
        createStopUpdate(STOP_A_ID, "0:10:00", "0:10:00"),
        createStopUpdate(STOP_B_ID, "0:20:00", "0:20:00")
      )
    );

    var result = matcher.match(parsedUpdate, createContext(env));

    assertTrue(result.isSuccess());
    assertEquals(TRIP_1_ID, result.successValue().trip().getId().getId());
  }

  @Test
  void matchByLastStopArrival() {
    var tripInput = TripInput.of(TRIP_1_ID)
      .addStop(STOP_A, "0:10:00", "0:10:00")
      .addStop(STOP_B, "0:20:00", "0:20:00");

    var env = ENV_BUILDER.addTrip(tripInput).build();
    var matcher = new SiriTripMatcher(env.transitService(), env.feedId());

    var parsedUpdate = createParsedUpdate(
      null,
      null,
      List.of(
        createStopUpdate(STOP_A_ID, "0:10:00", "0:10:00"),
        createStopUpdate(STOP_B_ID, "0:20:00", "0:20:00")
      )
    );

    var result = matcher.match(parsedUpdate, createContext(env));

    assertTrue(result.isSuccess());
    assertEquals(TRIP_1_ID, result.successValue().trip().getId().getId());
  }

  @Test
  void matchBySiblingStop() {
    var parentStation = ENV_BUILDER.station("PARENT_STATION");
    var platform1 = ENV_BUILDER.stop("PLATFORM_1", builder ->
      builder.withParentStation(parentStation)
    );
    ENV_BUILDER.stop("PLATFORM_2", builder -> builder.withParentStation(parentStation));

    var tripInput = TripInput.of(TRIP_1_ID)
      .addStop(STOP_A, "0:10:00", "0:10:00")
      .addStop(platform1, "0:20:00", "0:20:00");

    var env = ENV_BUILDER.addTrip(tripInput).build();
    var matcher = new SiriTripMatcher(env.transitService(), env.feedId());

    // Update references platform2, but trip uses platform1 (same station)
    var parsedUpdate = createParsedUpdate(
      null,
      null,
      List.of(
        createStopUpdate(STOP_A_ID, "0:10:00", "0:10:00"),
        createStopUpdate("PLATFORM_2", "0:20:00", "0:20:00")
      )
    );

    var result = matcher.match(parsedUpdate, createContext(env));

    assertTrue(result.isSuccess());
    assertEquals(TRIP_1_ID, result.successValue().trip().getId().getId());
  }

  @Test
  void matchWithLineRefFilter() {
    var route1 = ENV_BUILDER.route("ROUTE_1");
    var route2 = ENV_BUILDER.route("ROUTE_2");

    var trip1Input = TripInput.of(TRIP_1_ID)
      .withRoute(route1)
      .addStop(STOP_A, "0:10:00", "0:10:00")
      .addStop(STOP_B, "0:20:00", "0:20:00");

    var trip2Input = TripInput.of(TRIP_2_ID)
      .withRoute(route2)
      .addStop(STOP_A, "0:10:00", "0:10:00")
      .addStop(STOP_B, "0:20:00", "0:20:00");

    var env = ENV_BUILDER.addTrip(trip1Input, tripBuilder ->
      tripBuilder.withMode(TransitMode.RAIL).withNetexInternalPlanningCode("VehicleRef123")
    )
      .addTrip(trip2Input, tripBuilder ->
        tripBuilder.withMode(TransitMode.RAIL).withNetexInternalPlanningCode("VehicleRef123")
      )
      .build();
    var matcher = new SiriTripMatcher(env.transitService(), env.feedId());

    // With lineRef, should match only trip1
    var parsedUpdate = createParsedUpdate(
      "VehicleRef123",
      "ROUTE_1",
      List.of(
        createStopUpdate(STOP_A_ID, "0:10:00", "0:10:00"),
        createStopUpdate(STOP_B_ID, "0:20:00", "0:20:00")
      )
    );

    var result = matcher.match(parsedUpdate, createContext(env));

    assertTrue(result.isSuccess());
    assertEquals(TRIP_1_ID, result.successValue().trip().getId().getId());
  }

  @Test
  void noMatchReturnsError() {
    var tripInput = TripInput.of(TRIP_1_ID)
      .addStop(STOP_A, "0:10:00", "0:10:00")
      .addStop(STOP_B, "0:20:00", "0:20:00");

    var env = ENV_BUILDER.addTrip(tripInput).build();
    var matcher = new SiriTripMatcher(env.transitService(), env.feedId());

    // Different first departure time, should not match
    var parsedUpdate = createParsedUpdate(
      null,
      null,
      List.of(
        createStopUpdate(STOP_A_ID, "0:11:00", "0:11:00"),
        createStopUpdate(STOP_B_ID, "0:21:00", "0:21:00")
      )
    );

    var result = matcher.match(parsedUpdate, createContext(env));

    assertTrue(result.isFailure());
    assertEquals(NO_FUZZY_TRIP_MATCH, result.failureValue().errorType());
  }

  @Test
  void multipleMatchesReturnsError() {
    var route = ENV_BUILDER.route("RAIL_ROUTE");
    var trip1Input = TripInput.of(TRIP_1_ID)
      .withRoute(route)
      .addStop(STOP_A, "0:10:00", "0:10:00")
      .addStop(STOP_B, "0:20:00", "0:20:00");

    var trip2Input = TripInput.of(TRIP_2_ID)
      .withRoute(route)
      .addStop(STOP_A, "0:10:00", "0:10:00")
      .addStop(STOP_B, "0:20:00", "0:20:00");

    var env = ENV_BUILDER.addTrip(trip1Input, tripBuilder ->
      tripBuilder.withMode(TransitMode.RAIL).withNetexInternalPlanningCode("VehicleRef123")
    )
      .addTrip(trip2Input, tripBuilder ->
        tripBuilder.withMode(TransitMode.RAIL).withNetexInternalPlanningCode("VehicleRef123")
      )
      .build();
    var matcher = new SiriTripMatcher(env.transitService(), env.feedId());

    var parsedUpdate = createParsedUpdate(
      "VehicleRef123",
      null,
      List.of(
        createStopUpdate(STOP_A_ID, "0:10:00", "0:10:00"),
        createStopUpdate(STOP_B_ID, "0:20:00", "0:20:00")
      )
    );

    var result = matcher.match(parsedUpdate, createContext(env));

    assertTrue(result.isFailure());
    assertEquals(MULTIPLE_FUZZY_TRIP_MATCHES, result.failureValue().errorType());
  }

  @Test
  void emptyStopUpdatesReturnsError() {
    var tripInput = TripInput.of(TRIP_1_ID)
      .addStop(STOP_A, "0:10:00", "0:10:00")
      .addStop(STOP_B, "0:20:00", "0:20:00");

    var env = ENV_BUILDER.addTrip(tripInput).build();
    var matcher = new SiriTripMatcher(env.transitService(), env.feedId());

    var parsedUpdate = createParsedUpdate("VehicleRef123", null, List.of());

    var result = matcher.match(parsedUpdate, createContext(env));

    assertTrue(result.isFailure());
    assertEquals(NO_VALID_STOPS, result.failureValue().errorType());
  }

  @Test
  void invalidStopReturnsError() {
    var tripInput = TripInput.of(TRIP_1_ID)
      .addStop(STOP_A, "0:10:00", "0:10:00")
      .addStop(STOP_B, "0:20:00", "0:20:00");

    var env = ENV_BUILDER.addTrip(tripInput).build();
    var matcher = new SiriTripMatcher(env.transitService(), env.feedId());

    var parsedUpdate = createParsedUpdate(
      null,
      null,
      List.of(
        createStopUpdate(STOP_A_ID, "0:10:00", "0:10:00"),
        createStopUpdate("INVALID_STOP", "0:20:00", "0:20:00")
      )
    );

    var result = matcher.match(parsedUpdate, createContext(env));

    assertTrue(result.isFailure());
    assertEquals(NO_FUZZY_TRIP_MATCH, result.failureValue().errorType());
  }

  @Test
  void firstStopMismatchReturnsError() {
    var tripInput = TripInput.of(TRIP_1_ID)
      .addStop(STOP_A, "0:10:00", "0:10:00")
      .addStop(STOP_B, "0:20:00", "0:20:00");

    var env = ENV_BUILDER.addTrip(tripInput).build();
    var matcher = new SiriTripMatcher(env.transitService(), env.feedId());

    // Update has STOP_C as first stop, trip has STOP_A
    var parsedUpdate = createParsedUpdate(
      null,
      null,
      List.of(
        createStopUpdate(STOP_C_ID, "0:10:00", "0:10:00"),
        createStopUpdate(STOP_B_ID, "0:20:00", "0:20:00")
      )
    );

    var result = matcher.match(parsedUpdate, createContext(env));

    assertTrue(result.isFailure());
    assertEquals(NO_FUZZY_TRIP_MATCH, result.failureValue().errorType());
  }

  @Test
  void serviceDateFilteringWorks() {
    var route = ENV_BUILDER.route("RAIL_ROUTE");
    var tripInput = TripInput.of(TRIP_1_ID)
      .withRoute(route)
      .addStop(STOP_A, "0:10:00", "0:10:00")
      .addStop(STOP_B, "0:20:00", "0:20:00")
      .withServiceDates(SERVICE_DATE);

    var env = ENV_BUILDER.addTrip(tripInput, tripBuilder ->
      tripBuilder.withMode(TransitMode.RAIL).withNetexInternalPlanningCode("VehicleRef123")
    ).build();
    var matcher = new SiriTripMatcher(env.transitService(), env.feedId());

    // Service is not active on this date (far future)
    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      TripReference.builder().withVehicleRef("VehicleRef123").build(),
      LocalDate.of(2099, 12, 31)
    )
      .withStopTimeUpdates(
        List.of(
          createStopUpdate(STOP_A_ID, "0:10:00", "0:10:00"),
          createStopUpdate(STOP_B_ID, "0:20:00", "0:20:00")
        )
      )
      .build();

    var result = matcher.match(parsedUpdate, createContext(env));

    assertTrue(result.isFailure());
    assertEquals(NO_FUZZY_TRIP_MATCH, result.failureValue().errorType());
  }

  private ParsedTripUpdate createParsedUpdate(
    String vehicleRef,
    String lineRef,
    List<ParsedStopTimeUpdate> stopUpdates
  ) {
    return ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      TripReference.builder().withVehicleRef(vehicleRef).withLineRef(lineRef).build(),
      SERVICE_DATE
    )
      .withStopTimeUpdates(stopUpdates)
      .build();
  }

  private ParsedStopTimeUpdate createStopUpdate(
    String stopId,
    String departureTime,
    String arrivalTime
  ) {
    int departureSeconds = timeToSeconds(departureTime);
    int arrivalSeconds = timeToSeconds(arrivalTime);

    return ParsedStopTimeUpdate.builder(new StopReference(id(stopId), null, null))
      .withDepartureUpdate(TimeUpdate.ofAbsolute(departureSeconds, null))
      .withArrivalUpdate(TimeUpdate.ofAbsolute(arrivalSeconds, null))
      .build();
  }

  private int timeToSeconds(String time) {
    String[] parts = time.split(":");
    return (
      Integer.parseInt(parts[0]) * 3600 +
      Integer.parseInt(parts[1]) * 60 +
      Integer.parseInt(parts[2])
    );
  }

  private TripUpdateApplierContext createContext(TransitTestEnvironment env) {
    return new TripUpdateApplierContext(env.feedId(), env.timetableSnapshotManager());
  }
}
