package org.opentripplanner.ext.carpooling.routing;

import static org.junit.jupiter.api.Assertions.*;
import static org.opentripplanner.ext.carpooling.MockGraphPathFactory.*;
import static org.opentripplanner.ext.carpooling.TestCarpoolTripBuilder.*;
import static org.opentripplanner.ext.carpooling.TestFixtures.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InsertionCandidateTest {

  @Test
  void additionalDuration_calculatesCorrectly() {
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);
    var segments = createMockGraphPaths(3); // 3 segments

    var candidate = new InsertionCandidate(
      trip,
      1, // pickup position
      2, // dropoff position
      segments,
      Duration.ofMinutes(10), // baseline
      Duration.ofMinutes(15) // total
    );

    assertEquals(Duration.ofMinutes(5), candidate.additionalDuration());
  }

  @Test
  void additionalDuration_zeroAdditional_returnsZero() {
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);
    var segments = createMockGraphPaths(2);

    var candidate = new InsertionCandidate(
      trip,
      1,
      2,
      segments,
      Duration.ofMinutes(10),
      Duration.ofMinutes(10) // Same as baseline
    );

    assertEquals(Duration.ZERO, candidate.additionalDuration());
  }

  @Test
  void isWithinDeviationBudget_withinBudget_returnsTrue() {
    var trip = createTripWithDeviationBudget(Duration.ofMinutes(10), OSLO_CENTER, OSLO_NORTH);
    var segments = createMockGraphPaths(2);

    var candidate = new InsertionCandidate(
      trip,
      1,
      2,
      segments,
      Duration.ofMinutes(10), // baseline
      Duration.ofMinutes(18) // total (8 min additional, within 10 min budget)
    );

    assertTrue(candidate.isWithinDeviationBudget());
  }

  @Test
  void isWithinDeviationBudget_exceedsBudget_returnsFalse() {
    var trip = createTripWithDeviationBudget(Duration.ofMinutes(5), OSLO_CENTER, OSLO_NORTH);
    var segments = createMockGraphPaths(2);

    var candidate = new InsertionCandidate(
      trip,
      1,
      2,
      segments,
      Duration.ofMinutes(10), // baseline
      Duration.ofMinutes(20) // total (10 min additional, exceeds 5 min budget)
    );

    assertFalse(candidate.isWithinDeviationBudget());
  }

  @Test
  void isWithinDeviationBudget_exactlyAtBudget_returnsTrue() {
    var trip = createTripWithDeviationBudget(Duration.ofMinutes(5), OSLO_CENTER, OSLO_NORTH);
    var segments = createMockGraphPaths(2);

    var candidate = new InsertionCandidate(
      trip,
      1,
      2,
      segments,
      Duration.ofMinutes(10),
      Duration.ofMinutes(15) // Exactly 5 min additional
    );

    assertTrue(candidate.isWithinDeviationBudget());
  }

  @Test
  void getPickupSegments_returnsCorrectRange() {
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);
    var segments = createMockGraphPaths(5);

    var candidate = new InsertionCandidate(
      trip,
      2,
      4,
      segments,
      Duration.ofMinutes(10),
      Duration.ofMinutes(15)
    );

    var pickupSegments = candidate.getPickupSegments();
    assertEquals(2, pickupSegments.size()); // Segments 0-1 (before position 2)
    assertEquals(segments.subList(0, 2), pickupSegments);
  }

  @Test
  void getPickupSegments_positionZero_returnsEmpty() {
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);
    var segments = createMockGraphPaths(3);

    var candidate = new InsertionCandidate(
      trip,
      0,
      2,
      segments,
      Duration.ofMinutes(10),
      Duration.ofMinutes(15)
    );

    var pickupSegments = candidate.getPickupSegments();
    assertTrue(pickupSegments.isEmpty());
  }

  @Test
  void getSharedSegments_returnsCorrectRange() {
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);
    var segments = createMockGraphPaths(5);

    var candidate = new InsertionCandidate(
      trip,
      1,
      3,
      segments,
      Duration.ofMinutes(10),
      Duration.ofMinutes(15)
    );

    var sharedSegments = candidate.getSharedSegments();
    assertEquals(2, sharedSegments.size()); // Segments 1-2 (positions 1 to 3)
    assertEquals(segments.subList(1, 3), sharedSegments);
  }

  @Test
  void getSharedSegments_adjacentPositions_returnsSingleSegment() {
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);
    var segments = createMockGraphPaths(3);

    var candidate = new InsertionCandidate(
      trip,
      1,
      2,
      segments,
      Duration.ofMinutes(10),
      Duration.ofMinutes(15)
    );

    var sharedSegments = candidate.getSharedSegments();
    assertEquals(1, sharedSegments.size());
  }

  @Test
  void getDropoffSegments_returnsCorrectRange() {
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);
    var segments = createMockGraphPaths(5);

    var candidate = new InsertionCandidate(
      trip,
      1,
      3,
      segments,
      Duration.ofMinutes(10),
      Duration.ofMinutes(15)
    );

    var dropoffSegments = candidate.getDropoffSegments();
    assertEquals(2, dropoffSegments.size()); // Segments 3-4 (after position 3)
    assertEquals(segments.subList(3, 5), dropoffSegments);
  }

  @Test
  void getDropoffSegments_atEnd_returnsEmpty() {
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);
    var segments = createMockGraphPaths(3);

    var candidate = new InsertionCandidate(
      trip,
      1,
      3,
      segments,
      Duration.ofMinutes(10),
      Duration.ofMinutes(15)
    );

    var dropoffSegments = candidate.getDropoffSegments();
    assertTrue(dropoffSegments.isEmpty());
  }

  @Test
  void toString_includesKeyInformation() {
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);
    var segments = createMockGraphPaths(3);

    var candidate = new InsertionCandidate(
      trip,
      1,
      2,
      segments,
      Duration.ofMinutes(10),
      Duration.ofMinutes(15)
    );

    var str = candidate.toString();
    assertTrue(str.contains("pickup@1"));
    assertTrue(str.contains("dropoff@2"));
    assertTrue(str.contains("300s")); // 5 min = 300s additional
    assertTrue(str.contains("segments=3"));
  }
}
