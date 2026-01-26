package org.opentripplanner.updater.trip.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.trip.model.TripUpdateType.ADD_NEW_TRIP;
import static org.opentripplanner.updater.trip.model.TripUpdateType.CANCEL_TRIP;
import static org.opentripplanner.updater.trip.model.TripUpdateType.DELETE_TRIP;
import static org.opentripplanner.updater.trip.model.TripUpdateType.MODIFY_TRIP;
import static org.opentripplanner.updater.trip.model.TripUpdateType.UPDATE_EXISTING;

import org.junit.jupiter.api.Test;

class TripUpdateTypeTest {

  @Test
  void allValuesExist() {
    assertEquals(5, TripUpdateType.values().length);
  }

  @Test
  void updateExistingTrip() {
    assertFalse(UPDATE_EXISTING.createsNewTrip());
    assertFalse(UPDATE_EXISTING.removesTrip());
    assertFalse(UPDATE_EXISTING.modifiesStopPattern());
  }

  @Test
  void cancelTrip() {
    assertFalse(CANCEL_TRIP.createsNewTrip());
    assertTrue(CANCEL_TRIP.removesTrip());
    assertFalse(CANCEL_TRIP.modifiesStopPattern());
  }

  @Test
  void deleteTrip() {
    assertFalse(DELETE_TRIP.createsNewTrip());
    assertTrue(DELETE_TRIP.removesTrip());
    assertFalse(DELETE_TRIP.modifiesStopPattern());
  }

  @Test
  void addNewTrip() {
    assertTrue(ADD_NEW_TRIP.createsNewTrip());
    assertFalse(ADD_NEW_TRIP.removesTrip());
    assertTrue(ADD_NEW_TRIP.modifiesStopPattern());
  }

  @Test
  void modifyTrip() {
    assertTrue(MODIFY_TRIP.createsNewTrip());
    assertFalse(MODIFY_TRIP.removesTrip());
    assertTrue(MODIFY_TRIP.modifiesStopPattern());
  }
}
