package org.opentripplanner.updater.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.transit.model._data.FeedScopedIdForTestFactory.id;
import static org.opentripplanner.updater.spi.UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH;
import static org.opentripplanner.updater.spi.UpdateError.UpdateErrorType.NO_VALID_STOPS;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.timetable.Direction;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.StopReference;
import org.opentripplanner.updater.trip.model.TimeUpdate;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripUpdateType;

class GtfsTripMatcherTest implements RealtimeTestConstants {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 1, 15);
  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of(SERVICE_DATE);

  @Test
  void matchByRouteDirectionAndStartTime() {
    var route = ENV_BUILDER.route(ROUTE_1_ID);
    var tripInput = TripInput.of(TRIP_1_ID)
      .withRoute(route)
      .addStop(ENV_BUILDER.stop(STOP_A_ID), "08:30:00", "08:30:00")
      .addStop(ENV_BUILDER.stop(STOP_B_ID), "08:40:00", "08:40:00");

    var env = ENV_BUILDER.addTrip(tripInput, tripBuilder ->
      tripBuilder.withDirection(Direction.OUTBOUND)
    ).build();

    var matcher = new GtfsTripMatcher(env.transitService(), env.feedId());

    var parsedUpdate = createParsedUpdate(
      id(ROUTE_1_ID),
      "08:30:00",
      Direction.OUTBOUND,
      List.of(
        createStopUpdate(STOP_A_ID, "08:30:00", "08:30:00"),
        createStopUpdate(STOP_B_ID, "08:40:00", "08:40:00")
      )
    );

    var result = matcher.match(parsedUpdate, createContext(env));

    assertTrue(result.isSuccess());
    assertEquals(TRIP_1_ID, result.successValue().trip().getId().getId());
  }

  @Test
  void matchWithCarryoverFromPreviousDay() {
    var route = ENV_BUILDER.route(ROUTE_1_ID);
    var tripInput = TripInput.of(TRIP_1_ID)
      .withRoute(route)
      .addStop(ENV_BUILDER.stop(STOP_A_ID), "25:30:00", "25:30:00")
      .addStop(ENV_BUILDER.stop(STOP_B_ID), "25:40:00", "25:40:00");

    var env = ENV_BUILDER.addTrip(tripInput, tripBuilder ->
      tripBuilder.withDirection(Direction.OUTBOUND)
    ).build();

    var matcher = new GtfsTripMatcher(env.transitService(), env.feedId());

    // Update specifies current date (2024-01-16) with start time 01:30:00
    // but trip operates on previous service date (2024-01-15) with time 25:30:00
    var parsedUpdate = createParsedUpdateWithDate(
      id(ROUTE_1_ID),
      "01:30:00",
      SERVICE_DATE.plusDays(1),
      Direction.OUTBOUND,
      List.of(
        createStopUpdate(STOP_A_ID, "01:30:00", "01:30:00"),
        createStopUpdate(STOP_B_ID, "01:40:00", "01:40:00")
      )
    );

    var result = matcher.match(parsedUpdate, createContext(env));

    assertTrue(result.isSuccess());
    assertEquals(TRIP_1_ID, result.successValue().trip().getId().getId());
  }

  @Test
  void noMatchWhenRouteNotFound() {
    var env = ENV_BUILDER.build();
    var matcher = new GtfsTripMatcher(env.transitService(), env.feedId());

    var parsedUpdate = createParsedUpdate(
      id("NONEXISTENT_ROUTE"),
      "08:30:00",
      Direction.OUTBOUND,
      List.of(createStopUpdate(STOP_A_ID, "08:30:00", "08:30:00"))
    );

    var result = matcher.match(parsedUpdate, createContext(env));

    assertTrue(result.isFailure());
    assertEquals(NO_FUZZY_TRIP_MATCH, result.failureValue().errorType());
  }

  @Test
  void noMatchWhenDirectionMismatch() {
    var route = ENV_BUILDER.route(ROUTE_1_ID);
    var tripInput = TripInput.of(TRIP_1_ID)
      .withRoute(route)
      .addStop(ENV_BUILDER.stop(STOP_A_ID), "08:30:00", "08:30:00")
      .addStop(ENV_BUILDER.stop(STOP_B_ID), "08:40:00", "08:40:00");

    var env = ENV_BUILDER.addTrip(tripInput, tripBuilder ->
      tripBuilder.withDirection(Direction.OUTBOUND)
    ).build();

    var matcher = new GtfsTripMatcher(env.transitService(), env.feedId());

    var parsedUpdate = createParsedUpdate(
      id(ROUTE_1_ID),
      "08:30:00",
      Direction.INBOUND,
      List.of(
        createStopUpdate(STOP_A_ID, "08:30:00", "08:30:00"),
        createStopUpdate(STOP_B_ID, "08:40:00", "08:40:00")
      )
    );

    var result = matcher.match(parsedUpdate, createContext(env));

    assertTrue(result.isFailure());
    assertEquals(NO_FUZZY_TRIP_MATCH, result.failureValue().errorType());
  }

  @Test
  void noMatchWhenStartTimeMismatch() {
    var route = ENV_BUILDER.route(ROUTE_1_ID);
    var tripInput = TripInput.of(TRIP_1_ID)
      .withRoute(route)
      .addStop(ENV_BUILDER.stop(STOP_A_ID), "08:30:00", "08:30:00")
      .addStop(ENV_BUILDER.stop(STOP_B_ID), "08:40:00", "08:40:00");

    var env = ENV_BUILDER.addTrip(tripInput, tripBuilder ->
      tripBuilder.withDirection(Direction.OUTBOUND)
    ).build();

    var matcher = new GtfsTripMatcher(env.transitService(), env.feedId());

    var parsedUpdate = createParsedUpdate(
      id(ROUTE_1_ID),
      "08:35:00",
      Direction.OUTBOUND,
      List.of(
        createStopUpdate(STOP_A_ID, "08:35:00", "08:35:00"),
        createStopUpdate(STOP_B_ID, "08:45:00", "08:45:00")
      )
    );

    var result = matcher.match(parsedUpdate, createContext(env));

    assertTrue(result.isFailure());
    assertEquals(NO_FUZZY_TRIP_MATCH, result.failureValue().errorType());
  }

  @Test
  void noMatchWhenServiceDateMismatch() {
    var route = ENV_BUILDER.route(ROUTE_1_ID);
    var tripInput = TripInput.of(TRIP_1_ID)
      .withRoute(route)
      .addStop(ENV_BUILDER.stop(STOP_A_ID), "08:30:00", "08:30:00")
      .addStop(ENV_BUILDER.stop(STOP_B_ID), "08:40:00", "08:40:00");

    var env = ENV_BUILDER.addTrip(tripInput, tripBuilder ->
      tripBuilder.withDirection(Direction.OUTBOUND)
    ).build();

    var matcher = new GtfsTripMatcher(env.transitService(), env.feedId());

    var parsedUpdate = createParsedUpdateWithDate(
      id(ROUTE_1_ID),
      "08:30:00",
      LocalDate.of(2024, 1, 20),
      Direction.OUTBOUND,
      List.of(
        createStopUpdate(STOP_A_ID, "08:30:00", "08:30:00"),
        createStopUpdate(STOP_B_ID, "08:40:00", "08:40:00")
      )
    );

    var result = matcher.match(parsedUpdate, createContext(env));

    assertTrue(result.isFailure());
    assertEquals(NO_FUZZY_TRIP_MATCH, result.failureValue().errorType());
  }

  @Test
  void errorWhenEmptyStops() {
    var env = ENV_BUILDER.build();
    var matcher = new GtfsTripMatcher(env.transitService(), env.feedId());

    var parsedUpdate = createParsedUpdate(
      id(ROUTE_1_ID),
      "08:30:00",
      Direction.OUTBOUND,
      List.of()
    );

    var result = matcher.match(parsedUpdate, createContext(env));

    assertTrue(result.isFailure());
    assertEquals(NO_VALID_STOPS, result.failureValue().errorType());
  }

  @Test
  void errorWhenMissingRouteId() {
    var env = ENV_BUILDER.build();
    var matcher = new GtfsTripMatcher(env.transitService(), env.feedId());

    var tripRef = TripReference.builder()
      .withStartTime("08:30:00")
      .withStartDate(SERVICE_DATE)
      .withDirection(Direction.OUTBOUND)
      .withFuzzyMatchingHint(TripReference.FuzzyMatchingHint.FUZZY_MATCH_ALLOWED)
      .build();

    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      tripRef,
      SERVICE_DATE
    )
      .addStopTimeUpdate(createStopUpdate(STOP_A_ID, "08:30:00", "08:30:00"))
      .build();

    var result = matcher.match(parsedUpdate, createContext(env));

    assertTrue(result.isFailure());
    assertEquals(NO_FUZZY_TRIP_MATCH, result.failureValue().errorType());
  }

  @Test
  void errorWhenMissingStartTime() {
    var env = ENV_BUILDER.build();
    var matcher = new GtfsTripMatcher(env.transitService(), env.feedId());

    var tripRef = TripReference.builder()
      .withRouteId(id(ROUTE_1_ID))
      .withStartDate(SERVICE_DATE)
      .withDirection(Direction.OUTBOUND)
      .withFuzzyMatchingHint(TripReference.FuzzyMatchingHint.FUZZY_MATCH_ALLOWED)
      .build();

    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      tripRef,
      SERVICE_DATE
    )
      .addStopTimeUpdate(createStopUpdate(STOP_A_ID, "08:30:00", "08:30:00"))
      .build();

    var result = matcher.match(parsedUpdate, createContext(env));

    assertTrue(result.isFailure());
    assertEquals(NO_FUZZY_TRIP_MATCH, result.failureValue().errorType());
  }

  @Test
  void errorWhenMissingDirection() {
    var env = ENV_BUILDER.build();
    var matcher = new GtfsTripMatcher(env.transitService(), env.feedId());

    var tripRef = TripReference.builder()
      .withRouteId(id(ROUTE_1_ID))
      .withStartTime("08:30:00")
      .withStartDate(SERVICE_DATE)
      .withFuzzyMatchingHint(TripReference.FuzzyMatchingHint.FUZZY_MATCH_ALLOWED)
      .build();

    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      tripRef,
      SERVICE_DATE
    )
      .addStopTimeUpdate(createStopUpdate(STOP_A_ID, "08:30:00", "08:30:00"))
      .build();

    var result = matcher.match(parsedUpdate, createContext(env));

    assertTrue(result.isFailure());
    assertEquals(NO_FUZZY_TRIP_MATCH, result.failureValue().errorType());
  }

  // Helper methods

  private ParsedTripUpdate createParsedUpdate(
    org.opentripplanner.core.model.id.FeedScopedId routeId,
    String startTime,
    Direction direction,
    List<ParsedStopTimeUpdate> stopUpdates
  ) {
    return createParsedUpdateWithDate(routeId, startTime, SERVICE_DATE, direction, stopUpdates);
  }

  private ParsedTripUpdate createParsedUpdateWithDate(
    org.opentripplanner.core.model.id.FeedScopedId routeId,
    String startTime,
    LocalDate serviceDate,
    Direction direction,
    List<ParsedStopTimeUpdate> stopUpdates
  ) {
    var tripRef = TripReference.builder()
      .withRouteId(routeId)
      .withStartTime(startTime)
      .withStartDate(serviceDate)
      .withDirection(direction)
      .withFuzzyMatchingHint(TripReference.FuzzyMatchingHint.FUZZY_MATCH_ALLOWED)
      .build();

    return ParsedTripUpdate.builder(TripUpdateType.UPDATE_EXISTING, tripRef, serviceDate)
      .withStopTimeUpdates(stopUpdates)
      .build();
  }

  private ParsedStopTimeUpdate createStopUpdate(
    String stopId,
    String arrivalTime,
    String departureTime
  ) {
    int arrivalSeconds = timeToSeconds(arrivalTime);
    int departureSeconds = timeToSeconds(departureTime);

    return ParsedStopTimeUpdate.builder(StopReference.ofStopId(id(stopId)))
      .withArrivalUpdate(TimeUpdate.ofAbsolute(arrivalSeconds, null))
      .withDepartureUpdate(TimeUpdate.ofAbsolute(departureSeconds, null))
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
