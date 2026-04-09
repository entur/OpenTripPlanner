package org.opentripplanner.ext.carpooling.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.ext.carpooling.CarpoolGraphPathBuilder.createGraphPaths;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_CENTER;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_NORTH;
import static org.opentripplanner.ext.carpooling.CarpoolTripTestData.createSimpleTrip;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InsertionCandidateTest {

  @Test
  void additionalDuration_calculatesCorrectly() {
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);
    var segments = createGraphPaths(3);

    var candidate = new InsertionCandidate(
      trip,
      1,
      2,
      segments,
      Duration.ofMinutes(10),
      Duration.ofMinutes(15),
      null
    );

    assertEquals(Duration.ofMinutes(5), candidate.additionalDuration());
  }

  @Test
  void additionalDuration_zeroAdditional_returnsZero() {
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);
    var segments = createGraphPaths(2);

    var candidate = new InsertionCandidate(
      trip,
      1,
      2,
      segments,
      Duration.ofMinutes(10),
      Duration.ofMinutes(10),
      null
    );

    assertEquals(Duration.ZERO, candidate.additionalDuration());
  }

  @Test
  void getPickupSegments_returnsCorrectRange() {
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);
    var segments = createGraphPaths(5);

    var candidate = new InsertionCandidate(
      trip,
      2,
      4,
      segments,
      Duration.ofMinutes(10),
      Duration.ofMinutes(15),
      null
    );

    var pickupSegments = candidate.getPickupSegments();
    assertEquals(2, pickupSegments.size());
    assertEquals(segments.subList(0, 2), pickupSegments);
  }

  @Test
  void getPickupSegments_positionZero_returnsEmpty() {
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);
    var segments = createGraphPaths(3);

    var candidate = new InsertionCandidate(
      trip,
      0,
      2,
      segments,
      Duration.ofMinutes(10),
      Duration.ofMinutes(15),
      null
    );

    var pickupSegments = candidate.getPickupSegments();
    assertTrue(pickupSegments.isEmpty());
  }

  @Test
  void getSharedSegments_returnsCorrectRange() {
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);
    var segments = createGraphPaths(5);

    var candidate = new InsertionCandidate(
      trip,
      1,
      3,
      segments,
      Duration.ofMinutes(10),
      Duration.ofMinutes(15),
      null
    );

    var sharedSegments = candidate.getSharedSegments();
    assertEquals(2, sharedSegments.size());
    assertEquals(segments.subList(1, 3), sharedSegments);
  }

  @Test
  void getSharedSegments_adjacentPositions_returnsSingleSegment() {
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);
    var segments = createGraphPaths(3);

    var candidate = new InsertionCandidate(
      trip,
      1,
      2,
      segments,
      Duration.ofMinutes(10),
      Duration.ofMinutes(15),
      null
    );

    var sharedSegments = candidate.getSharedSegments();
    assertEquals(1, sharedSegments.size());
  }

  @Test
  void getDropoffSegments_returnsCorrectRange() {
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);
    var segments = createGraphPaths(5);

    var candidate = new InsertionCandidate(
      trip,
      1,
      3,
      segments,
      Duration.ofMinutes(10),
      Duration.ofMinutes(15),
      null
    );

    var dropoffSegments = candidate.getDropoffSegments();
    assertEquals(2, dropoffSegments.size());
    assertEquals(segments.subList(3, 5), dropoffSegments);
  }

  @Test
  void getDropoffSegments_atEnd_returnsEmpty() {
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);
    var segments = createGraphPaths(3);

    var candidate = new InsertionCandidate(
      trip,
      1,
      3,
      segments,
      Duration.ofMinutes(10),
      Duration.ofMinutes(15),
      null
    );

    var dropoffSegments = candidate.getDropoffSegments();
    assertTrue(dropoffSegments.isEmpty());
  }

  @Test
  void toString_includesKeyInformation() {
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);
    var segments = createGraphPaths(3);

    var candidate = new InsertionCandidate(
      trip,
      1,
      2,
      segments,
      Duration.ofMinutes(10),
      Duration.ofMinutes(15),
      null
    );

    var str = candidate.toString();
    assertTrue(str.contains("pickup@1"));
    assertTrue(str.contains("dropoff@2"));
    assertTrue(str.contains("300s"));
    assertTrue(str.contains("segments=3"));
  }
}
