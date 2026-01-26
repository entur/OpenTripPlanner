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
  private static final FeedScopedId STOP_FEED_SCOPED_ID = new FeedScopedId(FEED_ID, STOP_ID);
  private static final FeedScopedId ASSIGNED_STOP_ID = new FeedScopedId(FEED_ID, "assigned1");

  @Test
  void ofStopIdCreatesStopIdReference() {
    var ref = StopReference.ofStopId(STOP_FEED_SCOPED_ID);

    assertEquals(STOP_FEED_SCOPED_ID, ref.stopId());
    assertNull(ref.assignedStopId());
    assertTrue(ref.hasStopId());
    assertFalse(ref.hasAssignedStopId());
    assertEquals(StopResolutionStrategy.DIRECT, ref.resolutionStrategy());
  }

  @Test
  void ofStopIdWithAssignedStop() {
    var ref = StopReference.ofStopId(STOP_FEED_SCOPED_ID, ASSIGNED_STOP_ID);

    assertEquals(STOP_FEED_SCOPED_ID, ref.stopId());
    assertEquals(ASSIGNED_STOP_ID, ref.assignedStopId());
    assertTrue(ref.hasStopId());
    assertTrue(ref.hasAssignedStopId());
    assertEquals(StopResolutionStrategy.DIRECT, ref.resolutionStrategy());
  }

  @Test
  void ofScheduledStopPointOrStopIdCreatesScheduledStopPointFirstReference() {
    var ref = StopReference.ofScheduledStopPointOrStopId(STOP_FEED_SCOPED_ID);

    assertEquals(STOP_FEED_SCOPED_ID, ref.stopId());
    assertNull(ref.assignedStopId());
    assertTrue(ref.hasStopId());
    assertFalse(ref.hasAssignedStopId());
    assertEquals(StopResolutionStrategy.SCHEDULED_STOP_POINT_FIRST, ref.resolutionStrategy());
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
  void primaryIdReturnsStopIdForScheduledStopPointFirst() {
    var ref = StopReference.ofScheduledStopPointOrStopId(STOP_FEED_SCOPED_ID);

    assertEquals(STOP_FEED_SCOPED_ID, ref.primaryId());
  }

  @Test
  void equalityIncludesResolutionStrategy() {
    var directRef = StopReference.ofStopId(STOP_FEED_SCOPED_ID);
    var scheduledRef = StopReference.ofScheduledStopPointOrStopId(STOP_FEED_SCOPED_ID);

    // Same stopId but different resolution strategy should not be equal
    assertFalse(directRef.equals(scheduledRef));
  }

  @Test
  void equalityWithSameValues() {
    var ref1 = StopReference.ofStopId(STOP_FEED_SCOPED_ID);
    var ref2 = StopReference.ofStopId(STOP_FEED_SCOPED_ID);

    assertTrue(ref1.equals(ref2));
    assertEquals(ref1.hashCode(), ref2.hashCode());
  }

  @Test
  void toStringContainsAllFields() {
    var ref = StopReference.ofStopId(STOP_FEED_SCOPED_ID);

    var str = ref.toString();
    assertTrue(str.contains("stopId="));
    assertTrue(str.contains("resolutionStrategy=DIRECT"));
  }
}
