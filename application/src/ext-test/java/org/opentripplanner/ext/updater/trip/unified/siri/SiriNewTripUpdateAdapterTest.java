package org.opentripplanner.ext.updater.trip.unified.siri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.trip.UpdateIncrementality.DIFFERENTIAL;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.ext.updater.trip.unified.TripUpdateDispatcher;
import org.opentripplanner.ext.updater.trip.unified.TripUpdateParser;
import org.opentripplanner.ext.updater.trip.unified.resolver.NoOpFuzzyTripMatcher;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.framework.Deduplicator;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.organization.Operator;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;
import org.opentripplanner.updater.trip.patterncache.TripPatternIdGenerator;
import org.opentripplanner.updater.trip.siri.EntityResolver;
import org.opentripplanner.updater.trip.siri.SiriEtBuilder;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;

/**
 * Integration tests for the new SIRI trip update adapter that uses the common trip update
 * infrastructure (SiriTripUpdateParser + TripUpdateDispatcher).
 */
class SiriNewTripUpdateAdapterTest implements RealtimeTestConstants {

  private static final String ROUTE_ID = "route-id";
  private static final String OPERATOR_ID = "operator-id";

  /** The journey the stand-in parser of {@link #applyThroughFailingParser} refuses to handle. */
  private static final String POISON_CODE = "journey-the-parser-chokes-on";

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of();
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stop(STOP_B_ID);
  private final Operator OPERATOR = ENV_BUILDER.operator(OPERATOR_ID);
  private final Route ROUTE = ENV_BUILDER.route(ROUTE_ID, OPERATOR);

  private final TripInput TRIP_INPUT = TripInput.of(TRIP_1_ID)
    .withWithTripOnServiceDate(TRIP_1_ID)
    .withRoute(ROUTE)
    .addStop(STOP_A, "0:00:10", "0:00:11")
    .addStop(STOP_B, "0:00:20", "0:00:21");

  /**
   * Test that the new adapter correctly cancels a trip.
   * This test is disabled until all handlers in the new implementation are complete.
   * When enabled, it verifies that the new adapter produces the same result as the old one.
   */
  @Test
  @Disabled("Pending full implementation of all handlers in new implementation")
  void cancelTripUsingNewAdapter() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();

    var newAdapter = new SiriNewTripUpdateAdapter(
      env.transitRepository(),
      new Deduplicator(),
      false,
      env.feedId()
    );

    assertFalse(env.tripData(TRIP_1_ID).tripTimes().hasAnyUpdates());

    var updates = new SiriEtBuilder(env.localTimeParser())
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withCancellation(true)
      .buildEstimatedTimetableDeliveries();

    var result = applyEstimatedTimetable(env, newAdapter, updates);

    assertNotNull(result);
    assertEquals(1, result.successful());
    assertTrue(env.tripData(TRIP_1_ID).tripTimes().isCanceled());
  }

  @Test
  void newAdapterIsInstantiable() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var newAdapter = new SiriNewTripUpdateAdapter(
      env.transitRepository(),
      new Deduplicator(),
      false,
      env.feedId()
    );
    assertNotNull(newAdapter);
  }

  @Test
  void emptyUpdatesReturnsEmptyResult() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var newAdapter = new SiriNewTripUpdateAdapter(
      env.transitRepository(),
      new Deduplicator(),
      false,
      env.feedId()
    );

    var result = applyEstimatedTimetable(env, newAdapter, List.of());

    assertNotNull(result);
    assertEquals(0, result.successful());
    assertEquals(0, result.failed());
  }

  /**
   * No parser can be written that turns every message a real feed can produce into either a command
   * or an {@link UpdateException} - the SIRI schema is far wider than the profile, and this adapter
   * is new code besides. What a journey it cannot handle must never cost is the rest of the message:
   * the remaining journeys, and for a full dataset the whole feed, whose real-time data has already
   * been cleared from the buffer by the time the first journey is parsed.
   */
  @Test
  void aJourneyTheAdapterCannotHandleDoesNotDiscardTheRest() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();

    var poison = new SiriEtBuilder(env.localTimeParser())
      .withEstimatedVehicleJourneyCode(POISON_CODE)
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .buildEstimatedVehicleJourney();
    var cancellation = new SiriEtBuilder(env.localTimeParser())
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withCancellation(true)
      .buildEstimatedVehicleJourney();

    var result = applyThroughFailingParser(env, SiriEtBuilder.deliveryOf(poison, cancellation));

    assertEquals(1, result.successful(), "the journey after the failing one should be applied");
    assertEquals(Set.of(UpdateErrorType.UNKNOWN), result.failures().keySet());
    assertTrue(
      env.tripData(TRIP_1_ID).tripTimes().isCanceled(),
      "the cancellation following the failing journey should have reached the timetable"
    );
  }

  /**
   * Drive the handler with a parser that throws something it does not model as a rejection for one
   * journey, and parses the rest normally - standing in for any defect in the parsing of a single
   * journey.
   */
  private static UpdateResult applyThroughFailingParser(
    TransitTestEnvironment env,
    List<EstimatedTimetableDeliveryStructure> updates
  ) {
    var resultRef = new AtomicReference<UpdateResult>();
    try {
      env
        .updateManager()
        .submit(ctx -> {
          var buffer = ctx.repository(env.timetableHandle());
          var feedId = env.feedId();
          var transitService = new DefaultTransitService(env.transitRepository(), buffer);
          var realParser = new SiriTripUpdateParser(feedId, env.timeZone());
          TripUpdateParser<EstimatedVehicleJourney> failingParser = journey -> {
            if (POISON_CODE.equals(journey.getEstimatedVehicleJourneyCode())) {
              throw new IllegalStateException("simulated defect in the SIRI parser");
            }
            return realParser.parse(journey);
          };
          var dispatcher = TripUpdateDispatcher.create(
            env.timeZone(),
            transitService,
            DeduplicatorService.NOOP,
            new TripPatternCache(new TripPatternIdGenerator()),
            NoOpFuzzyTripMatcher.INSTANCE,
            new SiriRouteCreationStrategy()
          );
          resultRef.set(
            new SiriNewTripUpdateHandler(failingParser, dispatcher, buffer).applyEstimatedTimetable(
              new EntityResolver(transitService, feedId),
              feedId,
              DIFFERENTIAL,
              updates
            )
          );
        })
        .get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return resultRef.get();
  }

  private static UpdateResult applyEstimatedTimetable(
    TransitTestEnvironment env,
    SiriNewTripUpdateAdapter adapter,
    List<EstimatedTimetableDeliveryStructure> updates
  ) {
    var resultRef = new AtomicReference<UpdateResult>();
    try {
      env
        .updateManager()
        .submit(ctx -> {
          var buffer = ctx.repository(env.timetableHandle());
          var feedId = env.feedId();
          var transitService = new DefaultTransitService(env.transitRepository(), buffer);
          resultRef.set(
            adapter
              .forUpdate(buffer)
              .applyEstimatedTimetable(
                new EntityResolver(transitService, feedId),
                feedId,
                DIFFERENTIAL,
                updates
              )
          );
        })
        .get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return resultRef.get();
  }
}
