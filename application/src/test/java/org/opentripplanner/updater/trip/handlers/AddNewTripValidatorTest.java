package org.opentripplanner.updater.trip.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model._data.FeedScopedIdForTestFactory;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.NewTripResolver;
import org.opentripplanner.updater.trip.ServiceDateResolver;
import org.opentripplanner.updater.trip.StopResolver;
import org.opentripplanner.updater.trip.TripResolver;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.ResolvedNewTrip;
import org.opentripplanner.updater.trip.model.StopReference;
import org.opentripplanner.updater.trip.model.TimeUpdate;
import org.opentripplanner.updater.trip.model.TripCreationInfo;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripUpdateOptions;
import org.opentripplanner.updater.trip.model.TripUpdateType;
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

  private ResolvedNewTrip resolve(ParsedTripUpdate parsedUpdate) {
    var result = resolver.resolve(parsedUpdate);
    if (result.isFailure()) {
      throw new IllegalStateException("Failed to resolve update: " + result.failureValue());
    }
    return result.successValue();
  }

  @Test
  void validNewTrip_succeeds() {
    var tripId = new FeedScopedId(FEED_ID, "new-trip");
    var tripRef = TripReference.ofTripId(tripId);

    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.ADD_NEW_TRIP,
      tripRef,
      env.defaultServiceDate()
    )
      .withOptions(
        TripUpdateOptions.builder().withUnknownStopBehavior(UnknownStopBehavior.FAIL).build()
      )
      .withTripCreationInfo(TripCreationInfo.builder(tripId).build())
      .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
      .addStopTimeUpdate(createStopUpdate("B", 1, 10 * 3600 + 30 * 60))
      .build();

    var result = validator.validate(resolve(parsedUpdate));
    assertTrue(result.isSuccess());
  }

  @Test
  void failMode_unknownStop_fails() {
    var tripId = new FeedScopedId(FEED_ID, "new-trip");
    var tripRef = TripReference.ofTripId(tripId);

    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.ADD_NEW_TRIP,
      tripRef,
      env.defaultServiceDate()
    )
      .withOptions(
        TripUpdateOptions.builder().withUnknownStopBehavior(UnknownStopBehavior.FAIL).build()
      )
      .withTripCreationInfo(TripCreationInfo.builder(tripId).build())
      .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
      .addStopTimeUpdate(createStopUpdate("UNKNOWN", 1, 10 * 3600 + 30 * 60))
      .build();

    var result = validator.validate(resolve(parsedUpdate));
    assertTrue(result.isFailure());
    assertEquals(UpdateError.UpdateErrorType.UNKNOWN_STOP, result.failureValue().errorType());
    assertEquals(1, result.failureValue().stopIndex());
  }

  @Test
  void ignoreMode_unknownStop_passes() {
    var tripId = new FeedScopedId(FEED_ID, "new-trip");
    var tripRef = TripReference.ofTripId(tripId);

    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.ADD_NEW_TRIP,
      tripRef,
      env.defaultServiceDate()
    )
      .withOptions(
        TripUpdateOptions.builder().withUnknownStopBehavior(UnknownStopBehavior.IGNORE).build()
      )
      .withTripCreationInfo(TripCreationInfo.builder(tripId).build())
      .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
      .addStopTimeUpdate(createStopUpdate("UNKNOWN", 1, 10 * 3600 + 30 * 60))
      .addStopTimeUpdate(createStopUpdate("B", 2, 11 * 3600))
      .build();

    var result = validator.validate(resolve(parsedUpdate));
    assertTrue(result.isSuccess());
  }

  @Test
  void tooFewStops_fails() {
    var tripId = new FeedScopedId(FEED_ID, "new-trip");
    var tripRef = TripReference.ofTripId(tripId);

    // Only 1 stop
    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.ADD_NEW_TRIP,
      tripRef,
      env.defaultServiceDate()
    )
      .withOptions(
        TripUpdateOptions.builder().withUnknownStopBehavior(UnknownStopBehavior.FAIL).build()
      )
      .withTripCreationInfo(TripCreationInfo.builder(tripId).build())
      .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
      .build();

    var result = validator.validate(resolve(parsedUpdate));
    assertTrue(result.isFailure());
    assertEquals(UpdateError.UpdateErrorType.TOO_FEW_STOPS, result.failureValue().errorType());
  }

  private ParsedStopTimeUpdate createStopUpdate(String stopId, int sequence, int timeSeconds) {
    return ParsedStopTimeUpdate.builder(StopReference.ofStopId(new FeedScopedId(FEED_ID, stopId)))
      .withStopSequence(sequence)
      .withArrivalUpdate(TimeUpdate.ofAbsolute(timeSeconds, null))
      .withDepartureUpdate(TimeUpdate.ofAbsolute(timeSeconds, null))
      .build();
  }
}
