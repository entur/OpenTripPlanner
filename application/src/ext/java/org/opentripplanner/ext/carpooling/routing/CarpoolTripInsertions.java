package org.opentripplanner.ext.carpooling.routing;

import java.util.List;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;

public class CarpoolTripInsertions {

  public List<InsertionPosition> getInsertionPositions() {
    return insertionPositions;
  }

  public CarpoolTrip getCarpoolTrip() {
    return carpoolTrip;
  }

  private final CarpoolTrip carpoolTrip;
  private final List<InsertionPosition> insertionPositions;

  public CarpoolTripInsertions(CarpoolTrip carpoolTrip, List<InsertionPosition> insertionPositions) {
    this.carpoolTrip = carpoolTrip;
    this.insertionPositions = insertionPositions;
  }
}
