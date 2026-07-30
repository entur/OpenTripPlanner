package org.opentripplanner.updater.trip.gtfs.moduletests.vehicle;

import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimes;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;

/**
 * The vehicle id from the GTFS-RT vehicle descriptor should be propagated to the real-time trip
 * times.
 */
class VehicleIdTest implements RealtimeTestConstants {

  private static final String VEHICLE_ID = "BUS-42";

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop STOP_A = envBuilder.stop(STOP_A_ID);
  private final RegularStop STOP_B = envBuilder.stop(STOP_B_ID);

  private final TransitTestEnvironment env = envBuilder
    .addTrip(
      TripInput.of(TRIP_1_ID).addStop(STOP_A, "12:00", "12:00").addStop(STOP_B, "12:10", "12:10")
    )
    .build();
  private final GtfsRtTestHelper gtfsRt = GtfsRtTestHelper.of(env);

  @Test
  void vehicleIdIsSetOnReviseTrip() {
    var tripUpdate = gtfsRt
      .tripUpdateScheduled(TRIP_1_ID)
      .withVehicleId(VEHICLE_ID)
      .addDelayedStopTime(0, 0)
      .addDelayedStopTime(1, 60)
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(tripUpdate));

    var realTimeTimes = assertInstanceOf(
      RealTimeTripTimes.class,
      env.tripData(TRIP_1_ID).tripTimes()
    );
    assertTrue(realTimeTimes.getVehicleId().isPresent());
    assertEquals(VEHICLE_ID, realTimeTimes.getVehicleId().get());
  }

  @Test
  void vehicleIdIsSetOnAddedTrip() {
    var tripUpdate = gtfsRt
      .tripUpdate(ADDED_TRIP_ID, ADDED)
      .withVehicleId(VEHICLE_ID)
      .addStopTime(STOP_A_ID, "12:30")
      .addStopTime(STOP_B_ID, "12:40")
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(tripUpdate));

    var realTimeTimes = assertInstanceOf(
      RealTimeTripTimes.class,
      env.tripData(ADDED_TRIP_ID).tripTimes()
    );
    assertTrue(realTimeTimes.getVehicleId().isPresent());
    assertEquals(VEHICLE_ID, realTimeTimes.getVehicleId().get());
  }

  @Test
  void vehicleIdIsEmptyWhenAbsent() {
    var tripUpdate = gtfsRt
      .tripUpdateScheduled(TRIP_1_ID)
      .addDelayedStopTime(0, 0)
      .addDelayedStopTime(1, 60)
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(tripUpdate));

    var realTimeTimes = assertInstanceOf(
      RealTimeTripTimes.class,
      env.tripData(TRIP_1_ID).tripTimes()
    );
    assertTrue(realTimeTimes.getVehicleId().isEmpty());
  }
}
