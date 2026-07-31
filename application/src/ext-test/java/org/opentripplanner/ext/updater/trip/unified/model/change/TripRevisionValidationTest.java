package org.opentripplanner.ext.updater.trip.unified.model.change;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.core.model.id.FeedScopedIdForTestFactory;
import org.opentripplanner.ext.updater.trip.unified.factory.ExistingTripChangeFactory;
import org.opentripplanner.ext.updater.trip.unified.model.StopSequence;
import org.opentripplanner.ext.updater.trip.unified.model.command.ParsedStopTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.ReviseTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.StopReference;
import org.opentripplanner.ext.updater.trip.unified.model.command.TimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripReference;
import org.opentripplanner.ext.updater.trip.unified.policy.FormatPolicy;
import org.opentripplanner.ext.updater.trip.unified.policy.StopMatchingPolicy;
import org.opentripplanner.ext.updater.trip.unified.resolver.NoOpFuzzyTripMatcher;
import org.opentripplanner.ext.updater.trip.unified.resolver.ServiceDateResolver;
import org.opentripplanner.ext.updater.trip.unified.resolver.StopResolver;
import org.opentripplanner.ext.updater.trip.unified.resolver.TripResolver;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;

/**
 * Tests for the preconditions {@link ExistingTripChangeFactory} enforces when it resolves an update to
 * the times of an existing scheduled trip.
 */
class TripRevisionValidationTest {

  private static final ZoneId TIME_ZONE = ZoneId.of("America/New_York");
  private static final String FEED_ID = FeedScopedIdForTestFactory.FEED_ID;
  private static final String TRIP_ID = "trip1";

  private TransitTestEnvironment env;
  private ExistingTripChangeFactory factory;

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
    factory = new ExistingTripChangeFactory(
      transitService,
      tripResolver,
      serviceDateResolver,
      stopResolver,
      NoOpFuzzyTripMatcher.INSTANCE,
      TIME_ZONE
    );
  }

  private TripRevision resolve(ReviseTrip command) {
    return factory.create(command);
  }

  @Test
  void partialUpdate_alwaysValid() {
    var tripRef = TripReference.ofTripId(new FeedScopedId(FEED_ID, TRIP_ID));

    // PARTIAL_UPDATE with one stop — should pass validation
    var stopUpdate = ParsedStopTimeUpdate.builder(
      StopReference.ofStopId(new FeedScopedId(FEED_ID, "A"))
    )
      .withStopSequence(StopSequence.of(0))
      .withArrivalUpdate(TimeUpdate.ofDelay(60))
      .build();

    var command = ReviseTrip.builder(tripRef, env.defaultServiceDate())
      .withFormatPolicy(
        FormatPolicy.builder().withStopMatching(StopMatchingPolicy.BY_SEQUENCE_OR_ID).build()
      )
      .addStopTimeUpdate(stopUpdate)
      .build();

    assertDoesNotThrow(() -> resolve(command));
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

    var options = FormatPolicy.builder().withStopMatching(StopMatchingPolicy.POSITIONAL).build();

    var command = ReviseTrip.builder(tripRef, env.defaultServiceDate())
      .withFormatPolicy(options)
      .withStopTimeUpdates(List.of(stopAUpdate, stopBUpdate))
      .build();

    var ex = assertThrows(UpdateException.class, () -> resolve(command));
    assertEquals(UpdateErrorType.TOO_FEW_STOPS, ex.errorType());
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

    var options = FormatPolicy.builder().withStopMatching(StopMatchingPolicy.POSITIONAL).build();

    var command = ReviseTrip.builder(tripRef, env.defaultServiceDate())
      .withFormatPolicy(options)
      .withStopTimeUpdates(List.of(stopAUpdate, stopBUpdate, stopCUpdate, stopDUpdate))
      .build();

    var ex = assertThrows(UpdateException.class, () -> resolve(command));
    assertEquals(UpdateErrorType.TOO_MANY_STOPS, ex.errorType());
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

    var options = FormatPolicy.builder().withStopMatching(StopMatchingPolicy.POSITIONAL).build();

    var command = ReviseTrip.builder(tripRef, env.defaultServiceDate())
      .withFormatPolicy(options)
      .withStopTimeUpdates(List.of(stopAUpdate, stopBUpdate, stopCUpdate))
      .build();

    assertDoesNotThrow(() -> resolve(command));
  }
}
