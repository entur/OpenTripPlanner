package org.opentripplanner.updater.trip.siri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.opentripplanner.updater.trip.UpdateIncrementality.DIFFERENTIAL;

import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.organization.Operator;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.timetable.RealTimeState;
import org.opentripplanner.updater.DefaultRealTimeUpdateContext;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.siri.updater.EstimatedTimetableHandler;

/**
 * Integration tests for the new SIRI trip update adapter that uses the common trip update
 * infrastructure (SiriTripUpdateParser + DefaultTripUpdateApplier).
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

    // Create the new adapter
    var newAdapter = new SiriNewTripUpdateAdapter(
      env.timetableRepository(),
      env.timetableSnapshotManager(),
      env.feedId()
    );

    assertEquals(RealTimeState.SCHEDULED, env.tripData(TRIP_1_ID).realTimeState());

    var updates = new SiriEtBuilder(env.localTimeParser())
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withCancellation(true)
      .buildEstimatedTimetableDeliveries();

    // Use the new adapter via the handler
    var handler = new EstimatedTimetableHandler(newAdapter, false, env.feedId());
    var result = handler.applyUpdate(
      updates,
      DIFFERENTIAL,
      new DefaultRealTimeUpdateContext(
        new Graph(),
        env.timetableRepository(),
        env.timetableSnapshotManager().getTimetableSnapshotBuffer()
      )
    );

    env.timetableSnapshotManager().purgeAndCommit();

    assertNotNull(result);
    assertEquals(1, result.successful());
    assertEquals(RealTimeState.CANCELED, env.tripData(TRIP_1_ID).realTimeState());
  }

  @Test
  void newAdapterIsInstantiable() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var newAdapter = new SiriNewTripUpdateAdapter(
      env.timetableRepository(),
      env.timetableSnapshotManager(),
      env.feedId()
    );
    assertNotNull(newAdapter);
  }

  @Test
  void emptyUpdatesReturnsEmptyResult() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var newAdapter = new SiriNewTripUpdateAdapter(
      env.timetableRepository(),
      env.timetableSnapshotManager(),
      env.feedId()
    );

    var handler = new EstimatedTimetableHandler(newAdapter, false, env.feedId());
    var result = handler.applyUpdate(
      List.of(),
      DIFFERENTIAL,
      new DefaultRealTimeUpdateContext(
        new Graph(),
        env.timetableRepository(),
        env.timetableSnapshotManager().getTimetableSnapshotBuffer()
      )
    );

    assertNotNull(result);
    assertEquals(0, result.successful());
    assertEquals(0, result.failed());
  }
}
