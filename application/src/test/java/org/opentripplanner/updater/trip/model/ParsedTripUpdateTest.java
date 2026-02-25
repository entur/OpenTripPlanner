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

class ParsedTripUpdateTest {

  private static final String FEED_ID = "F";
  private static final FeedScopedId TRIP_ID = new FeedScopedId(FEED_ID, "trip1");
  private static final FeedScopedId STOP_ID = new FeedScopedId(FEED_ID, "stop1");
  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 1, 15);
  private static final TripReference TRIP_REF = TripReference.ofTripId(TRIP_ID);
  private static final StopReference STOP_REF = StopReference.ofStopId(STOP_ID);

  @Test
  void updateExistingBuilderCreatesMinimalUpdate() {
    var update = ParsedUpdateExisting.builder(TRIP_REF, SERVICE_DATE).build();

    assertInstanceOf(ParsedUpdateExisting.class, update);
    assertEquals(TRIP_REF, update.tripReference());
    assertEquals(SERVICE_DATE, update.serviceDate());
    assertTrue(update.stopTimeUpdates().isEmpty());
    assertEquals(TripUpdateOptions.siriDefaults(), update.options());
    assertNull(update.dataSource());
  }

  @Test
  void updateExistingWithStopTimeUpdates() {
    var stopTimeUpdate = ParsedStopTimeUpdate.builder(STOP_REF)
      .withArrivalUpdate(TimeUpdate.ofDelay(60))
      .build();

    var update = ParsedUpdateExisting.builder(TRIP_REF, SERVICE_DATE)
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

    var update = ParsedUpdateExisting.builder(TRIP_REF, SERVICE_DATE)
      .addStopTimeUpdate(stopTimeUpdate1)
      .addStopTimeUpdate(stopTimeUpdate2)
      .build();

    assertEquals(2, update.stopTimeUpdates().size());
  }

  @Test
  void addNewTripBuilderRequiresTripCreationInfo() {
    var tripCreationInfo = TripCreationInfo.builder(TRIP_ID).build();

    var update = ParsedAddNewTrip.builder(TRIP_REF, SERVICE_DATE, tripCreationInfo).build();

    assertInstanceOf(ParsedAddNewTrip.class, update);
    assertEquals(tripCreationInfo, update.tripCreationInfo());
    assertNotNull(update.tripCreationInfo());
  }

  @Test
  void updateExistingWithOptions() {
    var options = TripUpdateOptions.gtfsRtDefaults(
      org.opentripplanner.updater.trip.gtfs.ForwardsDelayPropagationType.DEFAULT,
      org.opentripplanner.updater.trip.gtfs.BackwardsDelayPropagationType.REQUIRED_NO_DATA
    );

    var update = ParsedUpdateExisting.builder(TRIP_REF, SERVICE_DATE).withOptions(options).build();

    assertEquals(options, update.options());
  }

  @Test
  void updateExistingWithDataSource() {
    var update = ParsedUpdateExisting.builder(TRIP_REF, SERVICE_DATE)
      .withDataSource("entur-siri")
      .build();

    assertEquals("entur-siri", update.dataSource());
  }

  @Test
  void cancelTripIsInstanceOfParsedTripRemoval() {
    var cancelUpdate = new ParsedCancelTrip(TRIP_REF, SERVICE_DATE, null, null);

    assertInstanceOf(ParsedTripRemoval.class, cancelUpdate);
    assertInstanceOf(ParsedTripUpdate.class, cancelUpdate);
    assertEquals(TRIP_REF, cancelUpdate.tripReference());
    assertEquals(SERVICE_DATE, cancelUpdate.serviceDate());
  }

  @Test
  void deleteTripIsInstanceOfParsedTripRemoval() {
    var deleteUpdate = new ParsedDeleteTrip(TRIP_REF, SERVICE_DATE, null, null);

    assertInstanceOf(ParsedTripRemoval.class, deleteUpdate);
    assertInstanceOf(ParsedTripUpdate.class, deleteUpdate);
    assertEquals(TRIP_REF, deleteUpdate.tripReference());
    assertEquals(SERVICE_DATE, deleteUpdate.serviceDate());
  }

  @Test
  void sealedHierarchyTypeCheck() {
    ParsedTripUpdate updateExisting = ParsedUpdateExisting.builder(TRIP_REF, SERVICE_DATE).build();
    ParsedTripUpdate modifyTrip = ParsedModifyTrip.builder(TRIP_REF, SERVICE_DATE).build();
    var tripCreationInfo = TripCreationInfo.builder(TRIP_ID).build();
    ParsedTripUpdate addNewTrip = ParsedAddNewTrip.builder(
      TRIP_REF,
      SERVICE_DATE,
      tripCreationInfo
    ).build();
    ParsedTripUpdate cancelTrip = new ParsedCancelTrip(TRIP_REF, SERVICE_DATE, null, null);
    ParsedTripUpdate deleteTrip = new ParsedDeleteTrip(TRIP_REF, SERVICE_DATE, null, null);

    assertInstanceOf(ParsedExistingTripUpdate.class, updateExisting);
    assertInstanceOf(ParsedExistingTripUpdate.class, modifyTrip);
    assertFalse(addNewTrip instanceof ParsedExistingTripUpdate);
    assertInstanceOf(ParsedTripRemoval.class, cancelTrip);
    assertInstanceOf(ParsedTripRemoval.class, deleteTrip);
  }
}
