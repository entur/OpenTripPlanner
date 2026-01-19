package org.opentripplanner.updater.trip.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.trip.model.TripUpdateType.ADD_NEW_TRIP;
import static org.opentripplanner.updater.trip.model.TripUpdateType.CANCEL_TRIP;
import static org.opentripplanner.updater.trip.model.TripUpdateType.UPDATE_EXISTING;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;

class ParsedTripUpdateTest {

  private static final String FEED_ID = "F";
  private static final FeedScopedId TRIP_ID = new FeedScopedId(FEED_ID, "trip1");
  private static final FeedScopedId STOP_ID = new FeedScopedId(FEED_ID, "stop1");
  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 1, 15);
  private static final TripReference TRIP_REF = TripReference.ofTripId(TRIP_ID);
  private static final StopReference STOP_REF = StopReference.ofStopId(STOP_ID);

  @Test
  void builderCreatesMinimalUpdate() {
    var update = ParsedTripUpdate.builder(UPDATE_EXISTING, TRIP_REF, SERVICE_DATE).build();

    assertEquals(UPDATE_EXISTING, update.updateType());
    assertEquals(TRIP_REF, update.tripReference());
    assertEquals(SERVICE_DATE, update.serviceDate());
    assertTrue(update.stopTimeUpdates().isEmpty());
    assertNull(update.tripCreationInfo());
    assertNull(update.stopPatternModification());
    assertEquals(TripUpdateOptions.siriDefaults(), update.options());
    assertNull(update.dataSource());
  }

  @Test
  void builderWithStopTimeUpdates() {
    var stopTimeUpdate = ParsedStopTimeUpdate.builder(STOP_REF)
      .withArrivalUpdate(TimeUpdate.ofDelay(60))
      .build();

    var update = ParsedTripUpdate.builder(UPDATE_EXISTING, TRIP_REF, SERVICE_DATE)
      .withStopTimeUpdates(List.of(stopTimeUpdate))
      .build();

    assertEquals(1, update.stopTimeUpdates().size());
    assertEquals(stopTimeUpdate, update.stopTimeUpdates().get(0));
  }

  @Test
  void builderAddStopTimeUpdate() {
    var stopTimeUpdate1 = ParsedStopTimeUpdate.builder(STOP_REF)
      .withArrivalUpdate(TimeUpdate.ofDelay(60))
      .build();
    var stopTimeUpdate2 = ParsedStopTimeUpdate.builder(STOP_REF)
      .withDepartureUpdate(TimeUpdate.ofDelay(120))
      .build();

    var update = ParsedTripUpdate.builder(UPDATE_EXISTING, TRIP_REF, SERVICE_DATE)
      .addStopTimeUpdate(stopTimeUpdate1)
      .addStopTimeUpdate(stopTimeUpdate2)
      .build();

    assertEquals(2, update.stopTimeUpdates().size());
  }

  @Test
  void builderWithTripCreationInfo() {
    var tripCreationInfo = TripCreationInfo.builder(TRIP_ID).build();

    var update = ParsedTripUpdate.builder(ADD_NEW_TRIP, TRIP_REF, SERVICE_DATE)
      .withTripCreationInfo(tripCreationInfo)
      .build();

    assertEquals(tripCreationInfo, update.tripCreationInfo());
    assertTrue(update.isNewTrip());
  }

  @Test
  void builderWithStopPatternModification() {
    var modification = StopPatternModification.builder().addSkippedStopIndex(1).build();

    var update = ParsedTripUpdate.builder(UPDATE_EXISTING, TRIP_REF, SERVICE_DATE)
      .withStopPatternModification(modification)
      .build();

    assertEquals(modification, update.stopPatternModification());
    assertTrue(update.hasStopPatternModification());
  }

  @Test
  void builderWithOptions() {
    var options = TripUpdateOptions.gtfsRtDefaults(
      org.opentripplanner.updater.trip.gtfs.ForwardsDelayPropagationType.DEFAULT,
      org.opentripplanner.updater.trip.gtfs.BackwardsDelayPropagationType.REQUIRED_NO_DATA
    );

    var update = ParsedTripUpdate.builder(UPDATE_EXISTING, TRIP_REF, SERVICE_DATE)
      .withOptions(options)
      .build();

    assertEquals(options, update.options());
  }

  @Test
  void builderWithDataSource() {
    var update = ParsedTripUpdate.builder(UPDATE_EXISTING, TRIP_REF, SERVICE_DATE)
      .withDataSource("entur-siri")
      .build();

    assertEquals("entur-siri", update.dataSource());
  }

  @Test
  void isCancellation() {
    var cancelUpdate = ParsedTripUpdate.builder(CANCEL_TRIP, TRIP_REF, SERVICE_DATE).build();
    var regularUpdate = ParsedTripUpdate.builder(UPDATE_EXISTING, TRIP_REF, SERVICE_DATE).build();

    assertTrue(cancelUpdate.isCancellation());
    assertFalse(regularUpdate.isCancellation());
  }

  @Test
  void isNewTrip() {
    var newTripUpdate = ParsedTripUpdate.builder(ADD_NEW_TRIP, TRIP_REF, SERVICE_DATE).build();
    var regularUpdate = ParsedTripUpdate.builder(UPDATE_EXISTING, TRIP_REF, SERVICE_DATE).build();

    assertTrue(newTripUpdate.isNewTrip());
    assertFalse(regularUpdate.isNewTrip());
  }

  @Test
  void hasStopPatternModificationWhenPresent() {
    var modification = StopPatternModification.builder().addSkippedStopIndex(1).build();

    var updateWithMod = ParsedTripUpdate.builder(UPDATE_EXISTING, TRIP_REF, SERVICE_DATE)
      .withStopPatternModification(modification)
      .build();

    var updateWithoutMod = ParsedTripUpdate.builder(
      UPDATE_EXISTING,
      TRIP_REF,
      SERVICE_DATE
    ).build();

    assertTrue(updateWithMod.hasStopPatternModification());
    assertFalse(updateWithoutMod.hasStopPatternModification());
  }

  @Test
  void hasStopPatternModificationFalseForEmptyModification() {
    var update = ParsedTripUpdate.builder(UPDATE_EXISTING, TRIP_REF, SERVICE_DATE)
      .withStopPatternModification(StopPatternModification.empty())
      .build();

    assertFalse(update.hasStopPatternModification());
  }
}
