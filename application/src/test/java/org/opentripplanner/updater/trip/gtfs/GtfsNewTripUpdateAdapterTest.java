package org.opentripplanner.updater.trip.gtfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.organization.Operator;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.UpdateIncrementality;

/**
 * Integration tests for the new GTFS-RT trip update adapter that uses the common trip update
 * infrastructure (GtfsRtTripUpdateParser + DefaultTripUpdateApplier).
 */
class GtfsNewTripUpdateAdapterTest implements RealtimeTestConstants {

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

  @Test
  void newAdapterIsInstantiable() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var newAdapter = new GtfsNewTripUpdateAdapter(
      env.timetableRepository(),
      env.timetableSnapshotManager(),
      ForwardsDelayPropagationType.DEFAULT,
      BackwardsDelayPropagationType.REQUIRED_NO_DATA
    );
    assertNotNull(newAdapter);
  }

  @Test
  void emptyUpdatesReturnsEmptyResult() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var newAdapter = new GtfsNewTripUpdateAdapter(
      env.timetableRepository(),
      env.timetableSnapshotManager(),
      ForwardsDelayPropagationType.DEFAULT,
      BackwardsDelayPropagationType.REQUIRED_NO_DATA
    );

    var result = newAdapter.applyTripUpdates(
      null,
      ForwardsDelayPropagationType.DEFAULT,
      BackwardsDelayPropagationType.REQUIRED_NO_DATA,
      UpdateIncrementality.DIFFERENTIAL,
      List.of(),
      env.feedId()
    );

    assertNotNull(result);
    assertEquals(0, result.successful());
    assertEquals(0, result.failed());
  }

  @Test
  void nullUpdatesReturnsEmptyResult() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var newAdapter = new GtfsNewTripUpdateAdapter(
      env.timetableRepository(),
      env.timetableSnapshotManager(),
      ForwardsDelayPropagationType.DEFAULT,
      BackwardsDelayPropagationType.REQUIRED_NO_DATA
    );

    var result = newAdapter.applyTripUpdates(
      null,
      ForwardsDelayPropagationType.DEFAULT,
      BackwardsDelayPropagationType.REQUIRED_NO_DATA,
      UpdateIncrementality.DIFFERENTIAL,
      null,
      env.feedId()
    );

    assertNotNull(result);
    assertEquals(0, result.successful());
    assertEquals(0, result.failed());
  }
}
