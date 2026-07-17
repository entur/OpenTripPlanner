package org.opentripplanner.updater.trip.siri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.trip.UpdateIncrementality.DIFFERENTIAL;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.framework.Deduplicator;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.organization.Operator;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;

/**
 * Integration tests for the new SIRI trip update adapter that uses the common trip update
 * infrastructure (SiriTripUpdateParser + TripUpdateDispatcher).
 */
class SiriNewTripUpdateAdapterTest implements RealtimeTestConstants {

  private static final String ROUTE_ID = "route-id";
  private static final String OPERATOR_ID = "operator-id";

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
      env.timetableRepository(),
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
      env.timetableRepository(),
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
      env.timetableRepository(),
      new Deduplicator(),
      false,
      env.feedId()
    );

    var result = applyEstimatedTimetable(env, newAdapter, List.of());

    assertNotNull(result);
    assertEquals(0, result.successful());
    assertEquals(0, result.failed());
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
          var transitService = new DefaultTransitService(env.timetableRepository(), buffer);
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
