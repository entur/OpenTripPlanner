package org.opentripplanner.updater.trip.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.updater.trip.policy.FormatPolicy;

class ParsedTripUpdateTest {

  private static final String FEED_ID = "F";
  private static final FeedScopedId TRIP_ID = new FeedScopedId(FEED_ID, "trip1");
  private static final FeedScopedId STOP_ID = new FeedScopedId(FEED_ID, "stop1");
  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 1, 15);
  private static final TripReference TRIP_REF = TripReference.ofTripId(TRIP_ID);
  private static final StopReference STOP_REF = StopReference.ofStopId(STOP_ID);

  @Test
  void updateExistingBuilderCreatesMinimalUpdate() {
    var update = ScheduledTripUpdate.builder(TRIP_REF, SERVICE_DATE).build();

    assertInstanceOf(ScheduledTripUpdate.class, update);
    assertEquals(TRIP_REF, update.tripReference());
    assertEquals(SERVICE_DATE, update.serviceDate());
    assertTrue(update.stopTimeUpdates().isEmpty());
    assertEquals(FormatPolicy.siri(), update.formatPolicy());
    assertNull(update.dataSource());
  }

  @Test
  void updateExistingWithStopTimeUpdates() {
    var stopTimeUpdate = ParsedStopTimeUpdate.builder(STOP_REF)
      .withArrivalUpdate(TimeUpdate.ofDelay(60))
      .build();

    var update = ScheduledTripUpdate.builder(TRIP_REF, SERVICE_DATE)
      .withStopTimeUpdates(List.of(stopTimeUpdate))
      .build();

    assertEquals(1, update.stopTimeUpdates().size());
    assertEquals(stopTimeUpdate, update.stopTimeUpdates().get(0));
  }

  @Test
  void updateExistingAddStopTimeUpdate() {
    var stopTimeUpdate1 = ParsedStopTimeUpdate.builder(STOP_REF)
      .withArrivalUpdate(TimeUpdate.ofDelay(60))
      .build();
    var stopTimeUpdate2 = ParsedStopTimeUpdate.builder(STOP_REF)
      .withDepartureUpdate(TimeUpdate.ofDelay(120))
      .build();

    var update = ScheduledTripUpdate.builder(TRIP_REF, SERVICE_DATE)
      .addStopTimeUpdate(stopTimeUpdate1)
      .addStopTimeUpdate(stopTimeUpdate2)
      .build();

    assertEquals(2, update.stopTimeUpdates().size());
  }

  @Test
  void addNewTripBuilderRequiresTripCreationInfo() {
    var tripCreationInfo = TripCreationInfo.builder(TRIP_ID).build();

    var update = TripAddition.builder(TRIP_REF, SERVICE_DATE, tripCreationInfo).build();

    assertInstanceOf(TripAddition.class, update);
    assertEquals(tripCreationInfo, update.tripCreationInfo());
    assertNotNull(update.tripCreationInfo());
  }

  @Test
  void updateExistingWithFormatPolicy() {
    var policy = FormatPolicy.gtfsRt(
      org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType.DEFAULT,
      org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType.REQUIRED_NO_DATA
    );

    var update = ScheduledTripUpdate.builder(TRIP_REF, SERVICE_DATE)
      .withFormatPolicy(policy)
      .build();

    assertEquals(policy, update.formatPolicy());
  }

  @Test
  void updateExistingWithDataSource() {
    var update = ScheduledTripUpdate.builder(TRIP_REF, SERVICE_DATE)
      .withDataSource("entur-siri")
      .build();

    assertEquals("entur-siri", update.dataSource());
  }

  @Test
  void cancellationIsInstanceOfTripRemoval() {
    var cancelUpdate = new TripCancellation(TRIP_REF, SERVICE_DATE, null, null);

    assertInstanceOf(TripRemoval.class, cancelUpdate);
    assertInstanceOf(ParsedTripUpdate.class, cancelUpdate);
    assertEquals(TRIP_REF, cancelUpdate.tripReference());
    assertEquals(SERVICE_DATE, cancelUpdate.serviceDate());
  }

  @Test
  void deletionIsInstanceOfTripRemoval() {
    var deleteUpdate = new TripDeletion(TRIP_REF, SERVICE_DATE, null, null);

    assertInstanceOf(TripRemoval.class, deleteUpdate);
    assertInstanceOf(ParsedTripUpdate.class, deleteUpdate);
    assertEquals(TRIP_REF, deleteUpdate.tripReference());
    assertEquals(SERVICE_DATE, deleteUpdate.serviceDate());
  }

  @Test
  void sealedHierarchyTypeCheck() {
    ParsedTripUpdate updateExisting = ScheduledTripUpdate.builder(TRIP_REF, SERVICE_DATE).build();
    ParsedTripUpdate modifyTrip = TripModification.builder(TRIP_REF, SERVICE_DATE).build();
    var tripCreationInfo = TripCreationInfo.builder(TRIP_ID).build();
    ParsedTripUpdate addNewTrip = TripAddition.builder(
      TRIP_REF,
      SERVICE_DATE,
      tripCreationInfo
    ).build();
    ParsedTripUpdate cancelTrip = new TripCancellation(TRIP_REF, SERVICE_DATE, null, null);
    ParsedTripUpdate deleteTrip = new TripDeletion(TRIP_REF, SERVICE_DATE, null, null);

    assertInstanceOf(ExistingTripUpdate.class, updateExisting);
    assertInstanceOf(ExistingTripUpdate.class, modifyTrip);
    assertFalse(addNewTrip instanceof ExistingTripUpdate);
    assertInstanceOf(TripRemoval.class, cancelTrip);
    assertInstanceOf(TripRemoval.class, deleteTrip);
  }
}
