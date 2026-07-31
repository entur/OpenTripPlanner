package org.opentripplanner.updater.trip.factory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.core.model.id.FeedScopedIdForTestFactory;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.gtfs.GtfsRtRouteCreationStrategy;
import org.opentripplanner.updater.trip.model.change.TripCreation;
import org.opentripplanner.updater.trip.model.command.AddTrip;
import org.opentripplanner.updater.trip.model.command.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.command.StopReference;
import org.opentripplanner.updater.trip.model.command.TimeUpdate;
import org.opentripplanner.updater.trip.model.command.TripCreationInfo;
import org.opentripplanner.updater.trip.model.command.TripReference;
import org.opentripplanner.updater.trip.policy.FormatPolicy;
import org.opentripplanner.updater.trip.policy.UnknownStopPolicy;
import org.opentripplanner.updater.trip.resolver.ServiceDateResolver;
import org.opentripplanner.updater.trip.resolver.StopResolver;
import org.opentripplanner.updater.trip.resolver.TripResolver;

/**
 * Tests for {@link TripAdditionFactory}, and through it the invariants a {@link TripCreation}
 * enforces on construction.
 */
class TripAdditionFactoryTest {

  private static final ZoneId TIME_ZONE = ZoneId.of("America/New_York");
  private static final String FEED_ID = FeedScopedIdForTestFactory.FEED_ID;

  private TransitTestEnvironment env;
  private TripAdditionFactory factory;

  @BeforeEach
  void setUp() {
    var builder = TransitTestEnvironment.of();
    var stopA = builder.stop("A");
    var stopB = builder.stop("B");
    builder.stop("C");
    // A scheduled trip is needed to give the feed a service period covering the default date
    env = builder
      .addTrip(TripInput.of("scheduled-trip").addStop(stopA, "08:00").addStop(stopB, "08:10"))
      .build();

    var transitService = (TransitEditorService) env.transitService();
    var tripResolver = new TripResolver(env.transitService());
    var serviceDateResolver = new ServiceDateResolver(tripResolver, env.transitService());
    var stopResolver = new StopResolver(env.transitService());
    // The GTFS-RT strategy resolves a route for any trip by falling back to a generated route
    var routeCreationStrategy = new GtfsRtRouteCreationStrategy(FEED_ID, null);
    factory = new TripAdditionFactory(
      transitService,
      serviceDateResolver,
      stopResolver,
      routeCreationStrategy,
      TIME_ZONE
    );
  }

  private TripCreation resolve(AddTrip command) {
    return (TripCreation) factory.create(command);
  }

  @Test
  void validNewTrip_succeeds() {
    var tripId = new FeedScopedId(FEED_ID, "new-trip");
    var tripRef = TripReference.ofTripId(tripId);

    var command = AddTrip.builder(
      tripRef,
      env.defaultServiceDate(),
      TripCreationInfo.builder(tripId).build()
    )
      .withFormatPolicy(FormatPolicy.builder().withUnknownStop(UnknownStopPolicy.FAIL).build())
      .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
      .addStopTimeUpdate(createStopUpdate("B", 1, 10 * 3600 + 30 * 60))
      .build();

    assertDoesNotThrow(() -> resolve(command));
  }

  @Test
  void failMode_unknownStop_fails() {
    var tripId = new FeedScopedId(FEED_ID, "new-trip");
    var tripRef = TripReference.ofTripId(tripId);

    var command = AddTrip.builder(
      tripRef,
      env.defaultServiceDate(),
      TripCreationInfo.builder(tripId).build()
    )
      .withFormatPolicy(FormatPolicy.builder().withUnknownStop(UnknownStopPolicy.FAIL).build())
      .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
      .addStopTimeUpdate(createStopUpdate("UNKNOWN", 1, 10 * 3600 + 30 * 60))
      .build();

    var ex = assertThrows(UpdateException.class, () -> resolve(command));
    assertEquals(UpdateErrorType.UNKNOWN_STOP, ex.errorType());
    assertEquals(1, ex.stopPosition());
  }

  @Test
  void ignoreMode_unknownStop_passes() {
    var tripId = new FeedScopedId(FEED_ID, "new-trip");
    var tripRef = TripReference.ofTripId(tripId);

    var command = AddTrip.builder(
      tripRef,
      env.defaultServiceDate(),
      TripCreationInfo.builder(tripId).build()
    )
      .withFormatPolicy(FormatPolicy.builder().withUnknownStop(UnknownStopPolicy.IGNORE).build())
      .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
      .addStopTimeUpdate(createStopUpdate("UNKNOWN", 1, 10 * 3600 + 30 * 60))
      .addStopTimeUpdate(createStopUpdate("B", 2, 11 * 3600))
      .build();

    assertDoesNotThrow(() -> resolve(command));
  }

  @Test
  void outsideServicePeriod_fails() {
    var tripId = new FeedScopedId(FEED_ID, "new-trip");
    var tripRef = TripReference.ofTripId(tripId);

    var command = AddTrip.builder(
      tripRef,
      env.defaultServiceDate().plusYears(1),
      TripCreationInfo.builder(tripId).build()
    )
      .withFormatPolicy(FormatPolicy.builder().withUnknownStop(UnknownStopPolicy.FAIL).build())
      .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
      .addStopTimeUpdate(createStopUpdate("B", 1, 10 * 3600 + 30 * 60))
      .build();

    var ex = assertThrows(UpdateException.class, () -> resolve(command));
    assertEquals(UpdateErrorType.OUTSIDE_SERVICE_PERIOD, ex.errorType());
  }

  @Test
  void tooFewStops_fails() {
    var tripId = new FeedScopedId(FEED_ID, "new-trip");
    var tripRef = TripReference.ofTripId(tripId);

    // Only 1 stop
    var command = AddTrip.builder(
      tripRef,
      env.defaultServiceDate(),
      TripCreationInfo.builder(tripId).build()
    )
      .withFormatPolicy(FormatPolicy.builder().withUnknownStop(UnknownStopPolicy.FAIL).build())
      .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
      .build();

    var ex = assertThrows(UpdateException.class, () -> resolve(command));
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
