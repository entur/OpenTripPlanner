package org.opentripplanner.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.opentripplanner._support.asserts.AssertEqualsAndHashCode;
import org.opentripplanner.core.model.id.FeedScopedId;

class GenericLocationTest {

  private static final String LABEL = "A place";
  private static final FeedScopedId STOP_ID = new FeedScopedId("F", "Stop:1");
  private static final double LATITUDE = 20.0;
  private static final double LONGITUDE = 30.0;
  private final GenericLocation subject = GenericLocation.fromStopIdWithFallback(
    STOP_ID,
    LATITUDE,
    LONGITUDE,
    LABEL
  );

  private final GenericLocation other = GenericLocation.fromCoordinate(LATITUDE, LONGITUDE, LABEL);

  @Test
  void fromStopId() {
    assertEquals(STOP_ID, subject.stopId());
  }

  @Test
  void getCoordinate() {
    assertEquals(STOP_ID, subject.stopId());
  }

  @Test
  void isSpecified() {
    assertTrue(subject.isSpecified());
  }

  @Test
  void testEquals() {
    var copy = GenericLocation.fromStopIdWithFallback(STOP_ID, LATITUDE, LONGITUDE, LABEL);
    AssertEqualsAndHashCode.verify(subject).sameAs(copy).differentFrom(other);
  }

  @Test
  void testToString() {
    assertEquals("A place F:Stop:1 (20.0, 30.0)", subject.toString());
  }
}
