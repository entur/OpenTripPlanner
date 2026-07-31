package org.opentripplanner.ext.updater.trip.unified.gtfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.transit.realtime.GtfsRealtime;
import java.time.LocalDate;
import java.time.ZoneId;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.accessibility.Accessibility;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.model.command.AbsoluteTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.AddTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.CancelTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.DeleteTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.ModifyTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.ParsedStopTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.ParsedTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.RemoveTripCommand;
import org.opentripplanner.ext.updater.trip.unified.model.command.ReviseTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.TimeUpdate;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;

class GtfsRtTripUpdateParserTest {

  private static final String FEED_ID = "TEST";
  private static final LocalDate TEST_DATE = LocalDate.of(2024, 1, 15);
  private static final ZoneId TIME_ZONE = ZoneId.of("America/New_York");
  private static final long MIDNIGHT_EPOCH = TEST_DATE.atStartOfDay(TIME_ZONE).toEpochSecond();
  private static final long TIME_0830 = MIDNIGHT_EPOCH + 30600;
  private static final long TIME_0831 = MIDNIGHT_EPOCH + 30660;
  private static final long TIME_0820 = MIDNIGHT_EPOCH + 30000;
  private static final long TIME_0821 = MIDNIGHT_EPOCH + 30060;
  private GtfsRtTripUpdateParser parser;

  @BeforeEach
  void setUp() {
    parser = new GtfsRtTripUpdateParser(
      ForwardsDelayPropagationType.DEFAULT,
      BackwardsDelayPropagationType.ALWAYS,
      FEED_ID,
      TIME_ZONE,
      () -> TEST_DATE
    );
  }

  @Test
  void parseReviseTrip() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setRouteId("route1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
      )
      .addStopTimeUpdate(
        GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
          .setStopId("stop1")
          .setStopSequence(0)
          .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(60))
          .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(120))
      )
      .build();

    var command = assertInstanceOf(ReviseTrip.class, parser.parse(tripUpdate));

    assertEquals(new FeedScopedId(FEED_ID, "trip1"), command.tripReference().tripId());
    assertEquals(TEST_DATE, command.serviceDate());
    assertEquals(1, command.stopTimeUpdates().size());

    var stopUpdate = command.stopTimeUpdates().get(0);
    assertEquals(new FeedScopedId(FEED_ID, "stop1"), stopUpdate.stopReference().stopId());
    assertEquals(0, stopUpdate.stopSequence());
    assertEquals(TimeUpdate.ofDelay(60), stopUpdate.arrivalUpdate());
    assertEquals(TimeUpdate.ofDelay(120), stopUpdate.departureUpdate());
  }

  @Test
  void parseCancelledTrip() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED)
      )
      .build();

    assertInstanceOf(CancelTrip.class, parser.parse(tripUpdate));
  }

  @Test
  void parseDeletedTrip() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.DELETED)
      )
      .build();

    var command = parser.parse(tripUpdate);
    assertInstanceOf(DeleteTrip.class, command);
    assertInstanceOf(RemoveTripCommand.class, command);
  }

  @Test
  void parseNewTrip() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setRouteId("route1")
          .setStartTime("08:30:00")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED)
      )
      .setVehicle(
        GtfsRealtime.VehicleDescriptor.newBuilder().setWheelchairAccessible(
          GtfsRealtime.VehicleDescriptor.WheelchairAccessible.WHEELCHAIR_ACCESSIBLE
        )
      )
      .addStopTimeUpdate(
        GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
          .setStopId("stop1")
          .setStopSequence(0)
          .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(TIME_0830))
          .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(TIME_0831))
      )
      .build();

    var command = assertInstanceOf(AddTrip.class, parser.parse(tripUpdate));

    assertNotNull(command.tripCreationInfo());
    assertEquals(new FeedScopedId(FEED_ID, "trip1"), command.tripCreationInfo().tripId());
    assertEquals(new FeedScopedId(FEED_ID, "route1"), command.tripCreationInfo().routeId());
    assertEquals(Accessibility.POSSIBLE, command.vehicleDescription().wheelchairAccessibility());

    assertEquals(1, command.stopTimeUpdates().size());
    var stopUpdate = command.stopTimeUpdates().get(0);
    assertNotNull(stopUpdate.arrivalUpdate());
    assertEquals(30600, asAbsolute(stopUpdate.arrivalUpdate()).time());
    assertNotNull(stopUpdate.departureUpdate());
    assertEquals(30660, asAbsolute(stopUpdate.departureUpdate()).time());
  }

  /**
   * A vehicle descriptor that carries no wheelchair information leaves the accessibility unset, so
   * that a replacement does not overwrite the accessibility of the trip it replaces.
   */
  @Test
  void parseNewTripWithoutWheelchairInformation() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setRouteId("route1")
          .setStartTime("08:30:00")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED)
      )
      .setVehicle(
        GtfsRealtime.VehicleDescriptor.newBuilder().setWheelchairAccessible(
          GtfsRealtime.VehicleDescriptor.WheelchairAccessible.NO_VALUE
        )
      )
      .addStopTimeUpdate(
        GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
          .setStopId("stop1")
          .setStopSequence(0)
          .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(TIME_0830))
          .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(TIME_0831))
      )
      .build();

    var command = assertInstanceOf(AddTrip.class, parser.parse(tripUpdate));

    assertNull(command.vehicleDescription().wheelchairAccessibility());
  }

  @Test
  void parseReplacementTrip() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.REPLACEMENT)
      )
      .addStopTimeUpdate(
        GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
          .setStopId("stop1")
          .setStopSequence(0)
          .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(60))
      )
      .build();

    assertInstanceOf(ModifyTrip.class, parser.parse(tripUpdate));
  }

  @Test
  void parseSkippedStop() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
      )
      .addStopTimeUpdate(
        GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
          .setStopId("stop1")
          .setStopSequence(0)
          .setScheduleRelationship(
            GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED
          )
      )
      .build();

    var command = assertInstanceOf(ReviseTrip.class, parser.parse(tripUpdate));

    assertEquals(1, command.stopTimeUpdates().size());
    var stopUpdate = command.stopTimeUpdates().get(0);
    assertEquals(ParsedStopTimeUpdate.StopUpdateStatus.SKIPPED, stopUpdate.status());
    assertTrue(stopUpdate.isSkipped());
  }

  @Test
  void parseWithAssignedStop() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
      )
      .addStopTimeUpdate(
        GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
          .setStopId("stop1")
          .setStopSequence(0)
          .setStopTimeProperties(
            GtfsRealtime.TripUpdate.StopTimeUpdate.StopTimeProperties.newBuilder().setAssignedStopId(
              "stop1_platform_2"
            )
          )
          .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(60))
      )
      .build();

    var command = assertInstanceOf(ReviseTrip.class, parser.parse(tripUpdate));

    var stopUpdate = command.stopTimeUpdates().get(0);
    assertEquals(new FeedScopedId(FEED_ID, "stop1"), stopUpdate.stopReference().stopId());
    assertEquals(
      new FeedScopedId(FEED_ID, "stop1_platform_2"),
      stopUpdate.stopReference().assignedStopId()
    );
  }

  @Test
  void parseWithTripProperties() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED)
      )
      .setTripProperties(
        GtfsRealtime.TripUpdate.TripProperties.newBuilder()
          .setTripHeadsign("Downtown")
          .setTripShortName("X1")
      )
      .addStopTimeUpdate(
        GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
          .setStopId("stop1")
          .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(TIME_0830))
      )
      .build();

    var command = assertInstanceOf(AddTrip.class, parser.parse(tripUpdate));

    assertNotNull(command.tripCreationInfo());
    assertNotNull(command.tripHeadsign());
    assertEquals("Downtown", command.tripHeadsign().toString());
    assertEquals("X1", command.tripCreationInfo().shortName());
  }

  @Test
  void parseWithStopHeadsign() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
      )
      .addStopTimeUpdate(
        GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
          .setStopId("stop1")
          .setStopSequence(0)
          .setStopTimeProperties(
            GtfsRealtime.TripUpdate.StopTimeUpdate.StopTimeProperties.newBuilder().setStopHeadsign(
              "Downtown Express"
            )
          )
          .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(60))
      )
      .build();

    var command = assertInstanceOf(ReviseTrip.class, parser.parse(tripUpdate));

    var stopUpdate = command.stopTimeUpdates().get(0);
    assertNotNull(stopUpdate.stopHeadsign());
    assertEquals("Downtown Express", stopUpdate.stopHeadsign().toString());
  }

  // Direction parsing not implemented yet - requires DirectionMapper integration
  // @Test
  // void parseWithDirection() { ... }

  @Test
  void parseMissingTripId() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder().setScheduleRelationship(
          GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED
        )
      )
      .build();

    assertThrows(UpdateException.class, () -> parser.parse(tripUpdate));
  }

  @Test
  void parseUnscheduledTrip() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.UNSCHEDULED)
      )
      .build();

    assertThrows(UpdateException.class, () -> parser.parse(tripUpdate));
  }

  @Test
  void parseDuplicatedTrip() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.DUPLICATED)
      )
      .build();

    assertThrows(UpdateException.class, () -> parser.parse(tripUpdate));
  }

  @Test
  void parseStopTimeUpdateWithPickupDropoff() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
      )
      .addStopTimeUpdate(
        GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
          .setStopId("stop1")
          .setStopSequence(0)
          .setStopTimeProperties(
            GtfsRealtime.TripUpdate.StopTimeUpdate.StopTimeProperties.newBuilder()
              .setPickupType(
                GtfsRealtime.TripUpdate.StopTimeUpdate.StopTimeProperties.DropOffPickupType.PHONE_AGENCY
              )
              .setDropOffType(
                GtfsRealtime.TripUpdate.StopTimeUpdate.StopTimeProperties.DropOffPickupType.NONE
              )
          )
          .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(60))
      )
      .build();

    var command = assertInstanceOf(ReviseTrip.class, parser.parse(tripUpdate));

    var stopUpdate = command.stopTimeUpdates().get(0);
    assertNotNull(stopUpdate.pickup());
    assertNotNull(stopUpdate.dropoff());
  }

  @Test
  void parseNewTripWithScheduledTimes() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED)
      )
      .addStopTimeUpdate(
        GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
          .setStopId("stop1")
          .setStopSequence(0)
          .setArrival(
            GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
              .setTime(TIME_0830)
              .setScheduledTime(TIME_0820)
          )
          .setDeparture(
            GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
              .setTime(TIME_0831)
              .setScheduledTime(TIME_0821)
          )
      )
      .build();

    var command = assertInstanceOf(AddTrip.class, parser.parse(tripUpdate));

    var stopUpdate = command.stopTimeUpdates().get(0);
    assertNotNull(stopUpdate.arrivalUpdate());
    assertEquals(30600, asAbsolute(stopUpdate.arrivalUpdate()).time());
    assertEquals(30000, asAbsolute(stopUpdate.arrivalUpdate()).aimedTime());

    assertNotNull(stopUpdate.departureUpdate());
    assertEquals(30660, asAbsolute(stopUpdate.departureUpdate()).time());
    assertEquals(30060, asAbsolute(stopUpdate.departureUpdate()).aimedTime());
  }

  @Test
  void parseEmptyStopTimeUpdates() {
    var tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
      .setTrip(
        GtfsRealtime.TripDescriptor.newBuilder()
          .setTripId("trip1")
          .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
      )
      .build();

    var command = assertInstanceOf(ReviseTrip.class, parser.parse(tripUpdate));

    assertTrue(command.stopTimeUpdates().isEmpty());
  }

  @Test
  void parseOptionsPreserved() {
    var command = assertInstanceOf(
      ReviseTrip.class,
      parser.parse(
        GtfsRealtime.TripUpdate.newBuilder()
          .setTrip(
            GtfsRealtime.TripDescriptor.newBuilder()
              .setTripId("trip1")
              .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
          )
          .build()
      )
    );

    assertEquals(
      ForwardsDelayPropagationType.DEFAULT,
      command.formatPolicy().delayPropagation().forwards()
    );
    assertEquals(
      BackwardsDelayPropagationType.ALWAYS,
      command.formatPolicy().delayPropagation().backwards()
    );
  }

  private static AbsoluteTimeUpdate asAbsolute(@Nullable ParsedTimeUpdate parsedTimeUpdate) {
    return assertInstanceOf(AbsoluteTimeUpdate.class, parsedTimeUpdate);
  }
}
