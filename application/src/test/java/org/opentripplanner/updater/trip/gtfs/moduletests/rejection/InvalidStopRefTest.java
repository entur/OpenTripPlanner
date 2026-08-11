package org.opentripplanner.updater.trip.gtfs.moduletests.rejection;

import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_STOP_REFERENCE;
import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_STOP_SEQUENCE;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;

import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;

class InvalidStopRefTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder builder = TransitTestEnvironment.of();
  private final RegularStop stopA = builder.stop(STOP_A_ID);
  private final RegularStop stopB = builder.stop(STOP_B_ID);
  private final TripInput tripInput = TripInput.of(TRIP_1_ID)
    .addStop(stopA, "10:00", "10:00")
    .addStop(stopB, "10:10", "10:10");

  @Test
  void unknownStopId() {
    var env = builder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(env);
    var update = rt.tripUpdateScheduled(TRIP_1_ID).addStopTime("unknown stop", "10:00").build();

    assertFailure(INVALID_STOP_REFERENCE, rt.applyTripUpdate(update));
  }

  /**
   * A flex stop id identifies no call of a fixed-stop trip: it is as unknown as an id the model
   * has never seen.
   */
  @Test
  void flexStopId() {
    var areaStop = builder.areaStop("FlexArea");
    var env = builder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(env);
    var update = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addStopTime(areaStop.getId().getId(), "10:00")
      .build();

    assertFailure(INVALID_STOP_REFERENCE, rt.applyTripUpdate(update));
  }

  @Test
  void knownAndUnknownStopId() {
    var env = builder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(env);
    var update = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addStopTime(STOP_A_ID, "10:00")
      .addStopTime("unknown stop", "10:00")
      .build();

    assertFailure(INVALID_STOP_REFERENCE, rt.applyTripUpdate(update));
  }

  @Test
  void invalidStopSequence() {
    var env = builder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(env);
    var update = rt.tripUpdateScheduled(TRIP_1_ID).addDelayedStopTime(100, 60).build();
    assertFailure(INVALID_STOP_SEQUENCE, rt.applyTripUpdate(update));
  }

  @Test
  void validAndInvalidStopSequence() {
    var env = builder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(env);
    var update = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addDelayedStopTime(0, 60)
      .addDelayedStopTime(100, 60)
      .build();
    assertFailure(INVALID_STOP_SEQUENCE, rt.applyTripUpdate(update));
  }

  /**
   * A sibling quay of a scheduled stop is a different stop: a call at one identifies no call of the
   * trip, even though the two share a station.
   */
  @Test
  void stopIdOfASiblingQuayInTheSameStation() {
    var stationBuilder = TransitTestEnvironment.of();
    var scheduledQuay = stationBuilder.stopAtStation(STOP_A_ID, STATION_OMEGA_ID);
    var siblingQuay = stationBuilder.stopAtStation(STOP_B_ID, STATION_OMEGA_ID);
    var lastStop = stationBuilder.stop(STOP_C_ID);
    var env = stationBuilder
      .addTrip(
        TripInput.of(TRIP_1_ID)
          .addStop(scheduledQuay, "10:00", "10:00")
          .addStop(lastStop, "10:10", "10:10")
      )
      .build();

    var rt = GtfsRtTestHelper.of(env);
    var update = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addStopTime(siblingQuay.getId().getId(), "10:05")
      .build();

    assertFailure(INVALID_STOP_REFERENCE, rt.applyTripUpdate(update));
  }

  /**
   * No stop id or stop sequence leads to a graceful failure.
   */
  @Test
  void noStopRef() {
    var env = builder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(env);
    var update = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addRawStopTime(
        StopTimeUpdate.newBuilder().setDeparture(StopTimeEvent.newBuilder().setDelay(60)).build()
      )
      .build();
    assertFailure(INVALID_STOP_REFERENCE, rt.applyTripUpdate(update));
  }
}
