package org.opentripplanner.updater.trip.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model._data.FeedScopedIdForTestFactory;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.ExistingTripResolver;
import org.opentripplanner.updater.trip.ServiceDateResolver;
import org.opentripplanner.updater.trip.StopResolver;
import org.opentripplanner.updater.trip.TripResolver;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedUpdateExisting;
import org.opentripplanner.updater.trip.model.ResolvedExistingTrip;
import org.opentripplanner.updater.trip.model.StopReference;
import org.opentripplanner.updater.trip.model.StopUpdateStrategy;
import org.opentripplanner.updater.trip.model.TimeUpdate;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripUpdateOptions;

/**
 * Tests for {@link UpdateExistingTripValidator}.
 */
class UpdateExistingTripValidatorTest {

  private static final ZoneId TIME_ZONE = ZoneId.of("America/New_York");
  private static final String FEED_ID = FeedScopedIdForTestFactory.FEED_ID;
  private static final String TRIP_ID = "trip1";

  private TransitTestEnvironment env;
  private ExistingTripResolver resolver;
  private UpdateExistingTripValidator validator;

  @BeforeEach
  void setUp() {
    var builder = TransitTestEnvironment.of().addStops("A", "B", "C");

    var stopA = builder.stop("A");
    var stopB = builder.stop("B");
    var stopC = builder.stop("C");

    env = builder
      .addTrip(
        TripInput.of(TRIP_ID)
          .addStop(stopA, "10:00")
          .addStop(stopB, "10:30")
          .addStop(stopC, "11:00")
      )
      .build();

    var transitService = (TransitEditorService) env.transitService();
    var tripResolver = new TripResolver(env.transitService());
    var serviceDateResolver = new ServiceDateResolver(tripResolver, env.transitService());
    var stopResolver = new StopResolver(env.transitService());
    resolver = new ExistingTripResolver(
      transitService,
      tripResolver,
      serviceDateResolver,
      stopResolver,
      null,
      TIME_ZONE
    );
    validator = new UpdateExistingTripValidator();
  }

  private ResolvedExistingTrip resolve(ParsedUpdateExisting parsedUpdate) {
    var result = resolver.resolve(parsedUpdate);
    if (result.isFailure()) {
      throw new IllegalStateException("Failed to resolve update: " + result.failureValue());
    }
    return result.successValue();
  }

  @Test
  void partialUpdate_alwaysValid() {
    var tripRef = TripReference.ofTripId(new FeedScopedId(FEED_ID, TRIP_ID));

    // PARTIAL_UPDATE with one stop — should pass validation
    var stopUpdate = ParsedStopTimeUpdate.builder(
      StopReference.ofStopId(new FeedScopedId(FEED_ID, "A"))
    )
      .withStopSequence(0)
      .withArrivalUpdate(TimeUpdate.ofDelay(60))
      .build();

    var parsedUpdate = ParsedUpdateExisting.builder(tripRef, env.defaultServiceDate())
      .withOptions(
        TripUpdateOptions.builder()
          .withStopUpdateStrategy(StopUpdateStrategy.PARTIAL_UPDATE)
          .build()
      )
      .addStopTimeUpdate(stopUpdate)
      .build();

    var result = validator.validate(resolve(parsedUpdate));
    assertTrue(result.isSuccess());
  }

  @Test
  void fullUpdate_rejectsStopSequence() {
    var tripRef = TripReference.ofTripId(new FeedScopedId(FEED_ID, TRIP_ID));

    var stopUpdate = ParsedStopTimeUpdate.builder(
      StopReference.ofStopId(new FeedScopedId(FEED_ID, "A"))
    )
      .withStopSequence(0)
      .withArrivalUpdate(TimeUpdate.ofDelay(60))
      .build();

    var options = TripUpdateOptions.builder()
      .withStopUpdateStrategy(StopUpdateStrategy.FULL_UPDATE)
      .build();

    var parsedUpdate = ParsedUpdateExisting.builder(tripRef, env.defaultServiceDate())
      .withOptions(options)
      .addStopTimeUpdate(stopUpdate)
      .build();

    var result = validator.validate(resolve(parsedUpdate));
    assertTrue(result.isFailure());
    assertEquals(
      UpdateError.UpdateErrorType.INVALID_STOP_SEQUENCE,
      result.failureValue().errorType()
    );
  }

  @Test
  void fullUpdate_rejectsTooFewStops() {
    var tripRef = TripReference.ofTripId(new FeedScopedId(FEED_ID, TRIP_ID));

    // Pattern has 3 stops but only provide 2
    var stopAUpdate = ParsedStopTimeUpdate.builder(
      StopReference.ofStopId(new FeedScopedId(FEED_ID, "A"))
    )
      .withArrivalUpdate(TimeUpdate.ofDelay(60))
      .build();

    var stopBUpdate = ParsedStopTimeUpdate.builder(
      StopReference.ofStopId(new FeedScopedId(FEED_ID, "B"))
    )
      .withArrivalUpdate(TimeUpdate.ofDelay(120))
      .build();

    var options = TripUpdateOptions.builder()
      .withStopUpdateStrategy(StopUpdateStrategy.FULL_UPDATE)
      .build();

    var parsedUpdate = ParsedUpdateExisting.builder(tripRef, env.defaultServiceDate())
      .withOptions(options)
      .withStopTimeUpdates(List.of(stopAUpdate, stopBUpdate))
      .build();

    var result = validator.validate(resolve(parsedUpdate));
    assertTrue(result.isFailure());
    assertEquals(UpdateError.UpdateErrorType.TOO_FEW_STOPS, result.failureValue().errorType());
  }

  @Test
  void fullUpdate_rejectsTooManyStops() {
    var tripRef = TripReference.ofTripId(new FeedScopedId(FEED_ID, TRIP_ID));

    // Pattern has 3 stops but provide 4
    var stopAUpdate = ParsedStopTimeUpdate.builder(
      StopReference.ofStopId(new FeedScopedId(FEED_ID, "A"))
    )
      .withArrivalUpdate(TimeUpdate.ofDelay(60))
      .build();

    var stopBUpdate = ParsedStopTimeUpdate.builder(
      StopReference.ofStopId(new FeedScopedId(FEED_ID, "B"))
    )
      .withArrivalUpdate(TimeUpdate.ofDelay(120))
      .build();

    var stopCUpdate = ParsedStopTimeUpdate.builder(
      StopReference.ofStopId(new FeedScopedId(FEED_ID, "C"))
    )
      .withArrivalUpdate(TimeUpdate.ofDelay(180))
      .build();

    var stopDUpdate = ParsedStopTimeUpdate.builder(
      StopReference.ofStopId(new FeedScopedId(FEED_ID, "A"))
    )
      .withArrivalUpdate(TimeUpdate.ofDelay(240))
      .build();

    var options = TripUpdateOptions.builder()
      .withStopUpdateStrategy(StopUpdateStrategy.FULL_UPDATE)
      .build();

    var parsedUpdate = ParsedUpdateExisting.builder(tripRef, env.defaultServiceDate())
      .withOptions(options)
      .withStopTimeUpdates(List.of(stopAUpdate, stopBUpdate, stopCUpdate, stopDUpdate))
      .build();

    var result = validator.validate(resolve(parsedUpdate));
    assertTrue(result.isFailure());
    assertEquals(UpdateError.UpdateErrorType.TOO_MANY_STOPS, result.failureValue().errorType());
  }

  @Test
  void fullUpdate_exactStopCount_succeeds() {
    var tripRef = TripReference.ofTripId(new FeedScopedId(FEED_ID, TRIP_ID));

    // Pattern has 3 stops, provide exactly 3 (no stop sequences)
    var stopAUpdate = ParsedStopTimeUpdate.builder(
      StopReference.ofStopId(new FeedScopedId(FEED_ID, "A"))
    )
      .withArrivalUpdate(TimeUpdate.ofDelay(60))
      .withDepartureUpdate(TimeUpdate.ofDelay(60))
      .build();

    var stopBUpdate = ParsedStopTimeUpdate.builder(
      StopReference.ofStopId(new FeedScopedId(FEED_ID, "B"))
    )
      .withArrivalUpdate(TimeUpdate.ofDelay(120))
      .withDepartureUpdate(TimeUpdate.ofDelay(120))
      .build();

    var stopCUpdate = ParsedStopTimeUpdate.builder(
      StopReference.ofStopId(new FeedScopedId(FEED_ID, "C"))
    )
      .withArrivalUpdate(TimeUpdate.ofDelay(180))
      .withDepartureUpdate(TimeUpdate.ofDelay(180))
      .build();

    var options = TripUpdateOptions.builder()
      .withStopUpdateStrategy(StopUpdateStrategy.FULL_UPDATE)
      .build();

    var parsedUpdate = ParsedUpdateExisting.builder(tripRef, env.defaultServiceDate())
      .withOptions(options)
      .withStopTimeUpdates(List.of(stopAUpdate, stopBUpdate, stopCUpdate))
      .build();

    var result = validator.validate(resolve(parsedUpdate));
    assertTrue(result.isSuccess());
  }
}
