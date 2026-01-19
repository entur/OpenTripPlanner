package org.opentripplanner.updater.trip.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate.StopUpdateStatus.ADDED;
import static org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate.StopUpdateStatus.CANCELLED;
import static org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate.StopUpdateStatus.NO_DATA;
import static org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate.StopUpdateStatus.SCHEDULED;
import static org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate.StopUpdateStatus.SKIPPED;

import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model.timetable.OccupancyStatus;

class ParsedStopTimeUpdateTest {

  private static final String FEED_ID = "F";
  private static final FeedScopedId STOP_ID = new FeedScopedId(FEED_ID, "stop1");
  private static final StopReference STOP_REF = StopReference.ofStopId(STOP_ID);

  @Test
  void builderCreatesMinimalUpdate() {
    var update = ParsedStopTimeUpdate.builder(STOP_REF).build();

    assertEquals(STOP_REF, update.stopReference());
    assertNull(update.stopSequence());
    assertEquals(SCHEDULED, update.status());
    assertNull(update.arrivalUpdate());
    assertNull(update.departureUpdate());
    assertNull(update.pickup());
    assertNull(update.dropoff());
    assertNull(update.stopHeadsign());
    assertNull(update.occupancy());
    assertFalse(update.isExtraCall());
    assertFalse(update.predictionInaccurate());
    assertFalse(update.recorded());
  }

  @Test
  void builderWithAllFields() {
    var arrivalUpdate = TimeUpdate.ofDelay(60);
    var departureUpdate = TimeUpdate.ofDelay(120);
    var headsign = new NonLocalizedString("Downtown");

    var update = ParsedStopTimeUpdate.builder(STOP_REF)
      .withStopSequence(5)
      .withStatus(SKIPPED)
      .withArrivalUpdate(arrivalUpdate)
      .withDepartureUpdate(departureUpdate)
      .withPickup(PickDrop.SCHEDULED)
      .withDropoff(PickDrop.NONE)
      .withStopHeadsign(headsign)
      .withOccupancy(OccupancyStatus.FEW_SEATS_AVAILABLE)
      .withIsExtraCall(true)
      .withPredictionInaccurate(true)
      .withRecorded(true)
      .build();

    assertEquals(STOP_REF, update.stopReference());
    assertEquals(5, update.stopSequence());
    assertEquals(SKIPPED, update.status());
    assertEquals(arrivalUpdate, update.arrivalUpdate());
    assertEquals(departureUpdate, update.departureUpdate());
    assertEquals(PickDrop.SCHEDULED, update.pickup());
    assertEquals(PickDrop.NONE, update.dropoff());
    assertEquals(headsign, update.stopHeadsign());
    assertEquals(OccupancyStatus.FEW_SEATS_AVAILABLE, update.occupancy());
    assertTrue(update.isExtraCall());
    assertTrue(update.predictionInaccurate());
    assertTrue(update.recorded());
  }

  @Test
  void stopUpdateStatusValues() {
    assertEquals(5, ParsedStopTimeUpdate.StopUpdateStatus.values().length);
    assertEquals(SCHEDULED, ParsedStopTimeUpdate.StopUpdateStatus.valueOf("SCHEDULED"));
    assertEquals(SKIPPED, ParsedStopTimeUpdate.StopUpdateStatus.valueOf("SKIPPED"));
    assertEquals(CANCELLED, ParsedStopTimeUpdate.StopUpdateStatus.valueOf("CANCELLED"));
    assertEquals(NO_DATA, ParsedStopTimeUpdate.StopUpdateStatus.valueOf("NO_DATA"));
    assertEquals(ADDED, ParsedStopTimeUpdate.StopUpdateStatus.valueOf("ADDED"));
  }

  @Test
  void hasArrivalUpdateWhenPresent() {
    var update = ParsedStopTimeUpdate.builder(STOP_REF)
      .withArrivalUpdate(TimeUpdate.ofDelay(60))
      .build();

    assertTrue(update.hasArrivalUpdate());
    assertFalse(update.hasDepartureUpdate());
  }

  @Test
  void hasDepartureUpdateWhenPresent() {
    var update = ParsedStopTimeUpdate.builder(STOP_REF)
      .withDepartureUpdate(TimeUpdate.ofDelay(60))
      .build();

    assertFalse(update.hasArrivalUpdate());
    assertTrue(update.hasDepartureUpdate());
  }

  @Test
  void isSkipped() {
    var skippedUpdate = ParsedStopTimeUpdate.builder(STOP_REF).withStatus(SKIPPED).build();
    var cancelledUpdate = ParsedStopTimeUpdate.builder(STOP_REF).withStatus(CANCELLED).build();
    var scheduledUpdate = ParsedStopTimeUpdate.builder(STOP_REF).withStatus(SCHEDULED).build();

    assertTrue(skippedUpdate.isSkipped());
    assertTrue(cancelledUpdate.isSkipped());
    assertFalse(scheduledUpdate.isSkipped());
  }
}
