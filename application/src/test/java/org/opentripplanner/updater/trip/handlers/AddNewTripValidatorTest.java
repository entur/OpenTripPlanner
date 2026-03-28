package org.opentripplanner.updater.trip.handlers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model._data.FeedScopedIdForTestFactory;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.NewTripResolver;
import org.opentripplanner.updater.trip.ServiceDateResolver;
import org.opentripplanner.updater.trip.StopResolver;
import org.opentripplanner.updater.trip.TripResolver;
import org.opentripplanner.updater.trip.model.ParsedAddNewTrip;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ResolvedNewTrip;
import org.opentripplanner.updater.trip.model.StopReference;
import org.opentripplanner.updater.trip.model.TimeUpdate;
import org.opentripplanner.updater.trip.model.TripCreationInfo;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripUpdateOptions;
import org.opentripplanner.updater.trip.model.UnknownStopBehavior;

/**
 * Tests for {@link AddNewTripValidator}.
 */
class AddNewTripValidatorTest {

  private static final ZoneId TIME_ZONE = ZoneId.of("America/New_York");
  private static final String FEED_ID = FeedScopedIdForTestFactory.FEED_ID;

  private TransitTestEnvironment env;
  private NewTripResolver resolver;
  private AddNewTripValidator validator;

  @BeforeEach
  void setUp() {
    var builder = TransitTestEnvironment.of().addStops("A", "B", "C");
    env = builder.build();

    var transitService = (TransitEditorService) env.transitService();
    var tripResolver = new TripResolver(env.transitService());
    var serviceDateResolver = new ServiceDateResolver(tripResolver, env.transitService());
    var stopResolver = new StopResolver(env.transitService());
    resolver = new NewTripResolver(transitService, serviceDateResolver, stopResolver, TIME_ZONE);
    validator = new AddNewTripValidator();
  }

  private ResolvedNewTrip resolve(ParsedAddNewTrip parsedUpdate) {
    return resolver.resolve(parsedUpdate);
  }

  @Test
  void validNewTrip_succeeds() {
    var tripId = new FeedScopedId(FEED_ID, "new-trip");
    var tripRef = TripReference.ofTripId(tripId);

    var parsedUpdate = ParsedAddNewTrip.builder(
      tripRef,
      env.defaultServiceDate(),
      TripCreationInfo.builder(tripId).build()
    )
      .withOptions(
        TripUpdateOptions.builder().withUnknownStopBehavior(UnknownStopBehavior.FAIL).build()
      )
      .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
      .addStopTimeUpdate(createStopUpdate("B", 1, 10 * 3600 + 30 * 60))
      .build();

    assertDoesNotThrow(() -> validator.validate(resolve(parsedUpdate)));
  }

  @Test
  void failMode_unknownStop_fails() {
    var tripId = new FeedScopedId(FEED_ID, "new-trip");
    var tripRef = TripReference.ofTripId(tripId);

    var parsedUpdate = ParsedAddNewTrip.builder(
      tripRef,
      env.defaultServiceDate(),
      TripCreationInfo.builder(tripId).build()
    )
      .withOptions(
        TripUpdateOptions.builder().withUnknownStopBehavior(UnknownStopBehavior.FAIL).build()
      )
      .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
      .addStopTimeUpdate(createStopUpdate("UNKNOWN", 1, 10 * 3600 + 30 * 60))
      .build();

    var ex = assertThrows(UpdateException.class, () -> validator.validate(resolve(parsedUpdate)));
    assertEquals(UpdateErrorType.UNKNOWN_STOP, ex.errorType());
    assertEquals(1, ex.stopIndex());
  }

  @Test
  void ignoreMode_unknownStop_passes() {
    var tripId = new FeedScopedId(FEED_ID, "new-trip");
    var tripRef = TripReference.ofTripId(tripId);

    var parsedUpdate = ParsedAddNewTrip.builder(
      tripRef,
      env.defaultServiceDate(),
      TripCreationInfo.builder(tripId).build()
    )
      .withOptions(
        TripUpdateOptions.builder().withUnknownStopBehavior(UnknownStopBehavior.IGNORE).build()
      )
      .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
      .addStopTimeUpdate(createStopUpdate("UNKNOWN", 1, 10 * 3600 + 30 * 60))
      .addStopTimeUpdate(createStopUpdate("B", 2, 11 * 3600))
      .build();

    assertDoesNotThrow(() -> validator.validate(resolve(parsedUpdate)));
  }

  @Test
  void tooFewStops_fails() {
    var tripId = new FeedScopedId(FEED_ID, "new-trip");
    var tripRef = TripReference.ofTripId(tripId);

    // Only 1 stop
    var parsedUpdate = ParsedAddNewTrip.builder(
      tripRef,
      env.defaultServiceDate(),
      TripCreationInfo.builder(tripId).build()
    )
      .withOptions(
        TripUpdateOptions.builder().withUnknownStopBehavior(UnknownStopBehavior.FAIL).build()
      )
      .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
      .build();

    var ex = assertThrows(UpdateException.class, () -> validator.validate(resolve(parsedUpdate)));
    assertEquals(UpdateErrorType.TOO_FEW_STOPS, ex.errorType());
  }

  private ParsedStopTimeUpdate createStopUpdate(String stopId, int sequence, int timeSeconds) {
    return ParsedStopTimeUpdate.builder(StopReference.ofStopId(new FeedScopedId(FEED_ID, stopId)))
      .withStopSequence(sequence)
      .withArrivalUpdate(TimeUpdate.ofAbsolute(timeSeconds, null))
      .withDepartureUpdate(TimeUpdate.ofAbsolute(timeSeconds, null))
      .build();
  }
}
