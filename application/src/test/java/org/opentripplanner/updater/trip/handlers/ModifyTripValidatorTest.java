package org.opentripplanner.updater.trip.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import org.opentripplanner.updater.trip.gtfs.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.ForwardsDelayPropagationType;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.ResolvedExistingTrip;
import org.opentripplanner.updater.trip.model.StopReference;
import org.opentripplanner.updater.trip.model.TimeUpdate;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripUpdateOptions;
import org.opentripplanner.updater.trip.model.TripUpdateType;

/**
 * Tests for {@link ModifyTripValidator}.
 */
class ModifyTripValidatorTest {

  private static final ZoneId TIME_ZONE = ZoneId.of("America/New_York");
  private static final String FEED_ID = FeedScopedIdForTestFactory.FEED_ID;
  private static final String TRIP_ID = "trip1";

  private final ModifyTripValidator validator = new ModifyTripValidator();

  @Nested
  class MinimumStopsTests {

    private TransitTestEnvironment env;
    private ExistingTripResolver resolver;

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
    }

    private ResolvedExistingTrip resolve(ParsedTripUpdate parsedUpdate) {
      var result = resolver.resolve(parsedUpdate);
      if (result.isFailure()) {
        throw new IllegalStateException("Failed to resolve update: " + result.failureValue());
      }
      return result.successValue();
    }

    @Test
    void rejectsTooFewStops() {
      var tripRef = TripReference.ofTripId(new FeedScopedId(FEED_ID, TRIP_ID));

      // Only 1 stop — need at least 2
      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.MODIFY_TRIP,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(
          TripUpdateOptions.gtfsRtDefaults(
            ForwardsDelayPropagationType.NONE,
            BackwardsDelayPropagationType.NONE
          )
        )
        .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
        .build();

      var result = validator.validate(resolve(parsedUpdate));
      assertTrue(result.isFailure());
      assertEquals(UpdateError.UpdateErrorType.TOO_FEW_STOPS, result.failureValue().errorType());
    }

    @Test
    void acceptsTwoOrMoreStops() {
      var tripRef = TripReference.ofTripId(new FeedScopedId(FEED_ID, TRIP_ID));

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.MODIFY_TRIP,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(
          TripUpdateOptions.gtfsRtDefaults(
            ForwardsDelayPropagationType.NONE,
            BackwardsDelayPropagationType.NONE
          )
        )
        .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
        .addStopTimeUpdate(createStopUpdate("C", 1, 11 * 3600))
        .build();

      var result = validator.validate(resolve(parsedUpdate));
      assertTrue(result.isSuccess());
    }

    private ParsedStopTimeUpdate createStopUpdate(String stopId, int sequence, int timeSeconds) {
      return ParsedStopTimeUpdate.builder(StopReference.ofStopId(new FeedScopedId(FEED_ID, stopId)))
        .withStopSequence(sequence)
        .withArrivalUpdate(TimeUpdate.ofAbsolute(timeSeconds, null))
        .withDepartureUpdate(TimeUpdate.ofAbsolute(timeSeconds, null))
        .build();
    }
  }

  @Nested
  class SiriExtraCallTests {

    private TransitTestEnvironment env;
    private ExistingTripResolver resolver;

    @BeforeEach
    void setUp() {
      var builder = TransitTestEnvironment.of();

      builder.station("station1");
      builder.station("station2");
      builder.station("station3");

      var stopA = builder.stopAtStation("A", "station1");
      var stopB = builder.stopAtStation("B", "station2");
      builder.stopAtStation("A2", "station1");
      builder.stopAtStation("D", "station3");

      env = builder
        .addTrip(TripInput.of(TRIP_ID).addStop(stopA, "10:00").addStop(stopB, "10:30"))
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
    }

    private ResolvedExistingTrip resolve(ParsedTripUpdate parsedUpdate) {
      var result = resolver.resolve(parsedUpdate);
      if (result.isFailure()) {
        throw new IllegalStateException("Failed to resolve update: " + result.failureValue());
      }
      return result.successValue();
    }

    @Test
    void validExtraCall_succeeds() {
      var tripRef = TripReference.ofTripId(new FeedScopedId(FEED_ID, TRIP_ID));

      // Original: A -> B; update: A -> D(extra) -> B
      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.MODIFY_TRIP,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(TripUpdateOptions.siriDefaults())
        .addStopTimeUpdate(createSiriStopUpdate("A", 10 * 3600, false))
        .addStopTimeUpdate(createSiriStopUpdate("D", 10 * 3600 + 15 * 60, true))
        .addStopTimeUpdate(createSiriStopUpdate("B", 10 * 3600 + 30 * 60, false))
        .build();

      var result = validator.validate(resolve(parsedUpdate));
      assertTrue(result.isSuccess());
    }

    @Test
    void wrongNumberOfNonExtraStops_fails() {
      var tripRef = TripReference.ofTripId(new FeedScopedId(FEED_ID, TRIP_ID));

      // Original: A -> B (2 stops); update: A (non-extra) + D (extra) = 1 non-extra
      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.MODIFY_TRIP,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(TripUpdateOptions.siriDefaults())
        .addStopTimeUpdate(createSiriStopUpdate("A", 10 * 3600, false))
        .addStopTimeUpdate(createSiriStopUpdate("D", 10 * 3600 + 15 * 60, true))
        .build();

      var result = validator.validate(resolve(parsedUpdate));
      assertTrue(result.isFailure());
      assertEquals(
        UpdateError.UpdateErrorType.INVALID_STOP_SEQUENCE,
        result.failureValue().errorType()
      );
    }

    @Test
    void nonExtraStopDoesNotMatchOriginal_fails() {
      var tripRef = TripReference.ofTripId(new FeedScopedId(FEED_ID, TRIP_ID));

      // Original: A -> B; update: A -> D(extra) -> D(non-extra, should be B)
      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.MODIFY_TRIP,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(TripUpdateOptions.siriDefaults())
        .addStopTimeUpdate(createSiriStopUpdate("A", 10 * 3600, false))
        .addStopTimeUpdate(createSiriStopUpdate("D", 10 * 3600 + 15 * 60, true))
        .addStopTimeUpdate(createSiriStopUpdate("D", 10 * 3600 + 30 * 60, false))
        .build();

      var result = validator.validate(resolve(parsedUpdate));
      assertTrue(result.isFailure());
      assertEquals(UpdateError.UpdateErrorType.STOP_MISMATCH, result.failureValue().errorType());
    }

    @Test
    void sameStationReplacement_succeeds() {
      var tripRef = TripReference.ofTripId(new FeedScopedId(FEED_ID, TRIP_ID));

      // Original: A -> B; update: A2(same station as A, non-extra) -> D(extra) -> B
      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.MODIFY_TRIP,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(TripUpdateOptions.siriDefaults())
        .addStopTimeUpdate(createSiriStopUpdate("A2", 10 * 3600, false))
        .addStopTimeUpdate(createSiriStopUpdate("D", 10 * 3600 + 15 * 60, true))
        .addStopTimeUpdate(createSiriStopUpdate("B", 10 * 3600 + 30 * 60, false))
        .build();

      var result = validator.validate(resolve(parsedUpdate));
      assertTrue(result.isSuccess());
    }

    @Test
    void noExtraCalls_skipsValidation() {
      var tripRef = TripReference.ofTripId(new FeedScopedId(FEED_ID, TRIP_ID));

      // No extra calls — SIRI extra call validation is skipped
      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.MODIFY_TRIP,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(TripUpdateOptions.siriDefaults())
        .addStopTimeUpdate(createSiriStopUpdate("A", 10 * 3600, false))
        .addStopTimeUpdate(createSiriStopUpdate("B", 10 * 3600 + 30 * 60, false))
        .build();

      var result = validator.validate(resolve(parsedUpdate));
      assertTrue(result.isSuccess());
    }

    private ParsedStopTimeUpdate createSiriStopUpdate(
      String stopId,
      int timeSeconds,
      boolean isExtraCall
    ) {
      return ParsedStopTimeUpdate.builder(StopReference.ofStopId(new FeedScopedId(FEED_ID, stopId)))
        .withArrivalUpdate(TimeUpdate.ofAbsolute(timeSeconds, null))
        .withDepartureUpdate(TimeUpdate.ofAbsolute(timeSeconds, null))
        .withIsExtraCall(isExtraCall)
        .build();
    }
  }
}
