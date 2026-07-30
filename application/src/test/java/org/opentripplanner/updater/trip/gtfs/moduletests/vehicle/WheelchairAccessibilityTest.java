package org.opentripplanner.updater.trip.gtfs.moduletests.vehicle;

import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED;
import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.REPLACEMENT;
import static com.google.transit.realtime.GtfsRealtime.VehicleDescriptor.WheelchairAccessible.WHEELCHAIR_ACCESSIBLE;
import static com.google.transit.realtime.GtfsRealtime.VehicleDescriptor.WheelchairAccessible.WHEELCHAIR_INACCESSIBLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;
import static org.opentripplanner.updater.trip.UpdateIncrementality.DIFFERENTIAL;

import com.google.transit.realtime.GtfsRealtime;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.accessibility.Accessibility;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;

/**
 * The wheelchair accessibility from the GTFS-RT vehicle descriptor should be propagated to the
 * real-time trip times: when a trip is added, when a follow-up message updates the trip that was
 * added earlier, and when a trip is replaced.
 */
class WheelchairAccessibilityTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop STOP_A = envBuilder.stop(STOP_A_ID);
  private final RegularStop STOP_B = envBuilder.stop(STOP_B_ID);

  /**
   * The scheduled trip is accessible according to the static data, so that a real-time update that
   * says nothing about the vehicle can be shown not to overwrite it.
   */
  private final TransitTestEnvironment env = envBuilder
    .addTrip(
      TripInput.of(TRIP_1_ID).addStop(STOP_A, "12:00", "12:00").addStop(STOP_B, "12:10", "12:10"),
      trip -> trip.withWheelchairBoarding(Accessibility.POSSIBLE)
    )
    .addStops(STOP_C_ID)
    .build();
  private final GtfsRtTestHelper gtfsRt = GtfsRtTestHelper.of(env);

  @Test
  void wheelchairAccessibilityIsSetOnAddedTrip() {
    assertSuccess(gtfsRt.applyTripUpdate(addedTrip(WHEELCHAIR_ACCESSIBLE, "12:30"), DIFFERENTIAL));

    assertEquals(Accessibility.POSSIBLE, wheelchairAccessibilityOfAddedTrip());
  }

  @Test
  void wheelchairAccessibilityIsUpdatedOnFollowUpMessage() {
    assertSuccess(gtfsRt.applyTripUpdate(addedTrip(WHEELCHAIR_ACCESSIBLE, "12:30"), DIFFERENTIAL));
    assertSuccess(
      gtfsRt.applyTripUpdate(addedTrip(WHEELCHAIR_INACCESSIBLE, "12:35"), DIFFERENTIAL)
    );

    assertEquals(Accessibility.NOT_POSSIBLE, wheelchairAccessibilityOfAddedTrip());
  }

  /**
   * GTFS-RT messages carry the full state of the trip, so a follow-up message that leaves the
   * wheelchair accessibility out means that it is no longer known.
   */
  @Test
  void wheelchairAccessibilityIsResetWhenFollowUpMessageOmitsIt() {
    assertSuccess(gtfsRt.applyTripUpdate(addedTrip(WHEELCHAIR_ACCESSIBLE, "12:30"), DIFFERENTIAL));
    assertSuccess(gtfsRt.applyTripUpdate(addedTrip(null, "12:35"), DIFFERENTIAL));

    assertEquals(Accessibility.NO_INFORMATION, wheelchairAccessibilityOfAddedTrip());
  }

  @Test
  void wheelchairAccessibilityIsSetOnReviseTrip() {
    var tripUpdate = gtfsRt
      .tripUpdateScheduled(TRIP_1_ID)
      .withWheelchairAccessible(WHEELCHAIR_INACCESSIBLE)
      .addDelayedStopTime(0, 0)
      .addDelayedStopTime(1, 60)
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(tripUpdate));

    assertEquals(
      Accessibility.NOT_POSSIBLE,
      env.tripData(TRIP_1_ID).tripTimes().getWheelchairAccessibility()
    );
  }

  /**
   * A delay message that says nothing about the vehicle must not overwrite the accessibility of the
   * scheduled trip.
   */
  @Test
  void wheelchairAccessibilityOfScheduledTripIsKeptWhenTheMessageOmitsIt() {
    var tripUpdate = gtfsRt
      .tripUpdateScheduled(TRIP_1_ID)
      .addDelayedStopTime(0, 0)
      .addDelayedStopTime(1, 60)
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(tripUpdate));

    assertEquals(
      Accessibility.POSSIBLE,
      env.tripData(TRIP_1_ID).tripTimes().getWheelchairAccessibility()
    );
  }

  @Test
  void wheelchairAccessibilityIsSetOnReplacementTrip() {
    var tripUpdate = gtfsRt
      .tripUpdate(TRIP_1_ID, REPLACEMENT)
      .withWheelchairAccessible(WHEELCHAIR_INACCESSIBLE)
      .addStopTime(STOP_A_ID, "12:00")
      .addStopTime(STOP_B_ID, "12:10")
      .addStopTime(STOP_C_ID, "12:20")
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(tripUpdate));

    assertEquals(
      Accessibility.NOT_POSSIBLE,
      env.tripData(TRIP_1_ID).tripTimes().getWheelchairAccessibility()
    );
  }

  /**
   * A replacement message that says nothing about the vehicle must not overwrite the accessibility
   * of the trip it replaces.
   */
  @Test
  void wheelchairAccessibilityOfReplacedTripIsKeptWhenTheMessageOmitsIt() {
    var tripUpdate = gtfsRt
      .tripUpdate(TRIP_1_ID, REPLACEMENT)
      .addStopTime(STOP_A_ID, "12:00")
      .addStopTime(STOP_B_ID, "12:10")
      .addStopTime(STOP_C_ID, "12:20")
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(tripUpdate));

    assertEquals(
      Accessibility.POSSIBLE,
      env.tripData(TRIP_1_ID).tripTimes().getWheelchairAccessibility()
    );
  }

  private GtfsRealtime.TripUpdate addedTrip(
    GtfsRealtime.VehicleDescriptor.WheelchairAccessible wheelchairAccessible,
    String firstDeparture
  ) {
    var builder = gtfsRt.tripUpdate(ADDED_TRIP_ID, ADDED);
    if (wheelchairAccessible != null) {
      builder.withWheelchairAccessible(wheelchairAccessible);
    }
    return builder.addStopTime(STOP_A_ID, firstDeparture).addStopTime(STOP_B_ID, "12:40").build();
  }

  private Accessibility wheelchairAccessibilityOfAddedTrip() {
    return env.tripData(ADDED_TRIP_ID).tripTimes().getWheelchairAccessibility();
  }
}
