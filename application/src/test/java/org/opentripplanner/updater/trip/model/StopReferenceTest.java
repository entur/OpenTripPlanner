package org.opentripplanner.updater.trip.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;

class StopReferenceTest {

  private static final String FEED_ID = "F";
  private static final String STOP_ID = "stop1";
  private static final String QUAY_REF = "NSR:Quay:1234";
  private static final FeedScopedId STOP_FEED_SCOPED_ID = new FeedScopedId(FEED_ID, STOP_ID);
  private static final FeedScopedId ASSIGNED_STOP_ID = new FeedScopedId(FEED_ID, "assigned1");

  @Test
  void ofStopIdCreatesStopIdReference() {
    var ref = StopReference.ofStopId(STOP_FEED_SCOPED_ID);

    assertEquals(STOP_FEED_SCOPED_ID, ref.stopId());
    assertNull(ref.stopPointRef());
    assertNull(ref.assignedStopId());
    assertTrue(ref.hasStopId());
    assertFalse(ref.hasStopPointRef());
  }

  @Test
  void ofStopPointRefCreatesStopPointReference() {
    var ref = StopReference.ofStopPointRef(QUAY_REF);

    assertNull(ref.stopId());
    assertEquals(QUAY_REF, ref.stopPointRef());
    assertNull(ref.assignedStopId());
    assertFalse(ref.hasStopId());
    assertTrue(ref.hasStopPointRef());
  }

  @Test
  void ofStopPointRefWithAssignedStop() {
    var ref = StopReference.ofStopPointRef(QUAY_REF, ASSIGNED_STOP_ID);

    assertNull(ref.stopId());
    assertEquals(QUAY_REF, ref.stopPointRef());
    assertEquals(ASSIGNED_STOP_ID, ref.assignedStopId());
    assertFalse(ref.hasStopId());
    assertTrue(ref.hasStopPointRef());
    assertTrue(ref.hasAssignedStopId());
  }

  @Test
  void ofStopIdWithAssignedStop() {
    var ref = StopReference.ofStopId(STOP_FEED_SCOPED_ID, ASSIGNED_STOP_ID);

    assertEquals(STOP_FEED_SCOPED_ID, ref.stopId());
    assertNull(ref.stopPointRef());
    assertEquals(ASSIGNED_STOP_ID, ref.assignedStopId());
    assertTrue(ref.hasStopId());
    assertFalse(ref.hasStopPointRef());
    assertTrue(ref.hasAssignedStopId());
  }

  @Test
  void hasAssignedStopIdWhenNone() {
    var ref = StopReference.ofStopId(STOP_FEED_SCOPED_ID);

    assertFalse(ref.hasAssignedStopId());
  }

  @Test
  void primaryIdReturnsAssignedStopIdIfPresent() {
    var ref = StopReference.ofStopId(STOP_FEED_SCOPED_ID, ASSIGNED_STOP_ID);

    assertEquals(ASSIGNED_STOP_ID, ref.primaryId());
  }

  @Test
  void primaryIdReturnsStopIdIfNoAssignedId() {
    var ref = StopReference.ofStopId(STOP_FEED_SCOPED_ID);

    assertEquals(STOP_FEED_SCOPED_ID, ref.primaryId());
  }

  @Test
  void primaryIdReturnsNullForStopPointRefOnlyWithoutAssignment() {
    var ref = StopReference.ofStopPointRef(QUAY_REF);

    assertNull(ref.primaryId());
  }
}
