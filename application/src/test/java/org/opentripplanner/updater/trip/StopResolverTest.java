package org.opentripplanner.updater.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model._data.FeedScopedIdForTestFactory;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.trip.model.StopReference;
import org.opentripplanner.updater.trip.model.StopResolutionStrategy;

/**
 * Tests for {@link StopResolver}.
 */
class StopResolverTest {

  private static final String FEED_ID = FeedScopedIdForTestFactory.FEED_ID;
  private static final String STOP_A = "stopA";
  private static final String STOP_B = "stopB";

  private TransitService transitService;
  private StopResolver resolver;

  @BeforeEach
  void setUp() {
    var env = TransitTestEnvironment.of().addStops(STOP_A, STOP_B).build();
    transitService = env.transitService();
    resolver = new StopResolver(transitService);
  }

  @Test
  void resolveByStopId() {
    var stopId = new FeedScopedId(FEED_ID, STOP_A);
    var reference = StopReference.ofStopId(stopId);

    var stop = resolver.resolve(reference);

    assertNotNull(stop);
    assertEquals(stopId, stop.getId());
  }

  @Test
  void resolveByAssignedStopId() {
    var originalId = new FeedScopedId(FEED_ID, STOP_A);
    var assignedId = new FeedScopedId(FEED_ID, STOP_B);
    var reference = StopReference.ofStopId(originalId, assignedId);

    var stop = resolver.resolve(reference);

    // assignedStopId takes precedence
    assertNotNull(stop);
    assertEquals(assignedId, stop.getId());
  }

  @Test
  void resolveByScheduledStopPointOrStopId() {
    var stopId = new FeedScopedId(FEED_ID, STOP_A);
    var reference = StopReference.ofScheduledStopPointOrStopId(stopId);

    var stop = resolver.resolve(reference);

    // Falls back to direct lookup since no scheduled stop point mapping exists
    assertNotNull(stop);
    assertEquals(stopId, stop.getId());
  }

  @Test
  void resolveUnknownStopId_returnsNull() {
    var unknownId = new FeedScopedId(FEED_ID, "unknown-stop");
    var reference = StopReference.ofStopId(unknownId);

    var stop = resolver.resolve(reference);

    assertNull(stop);
  }

  @Test
  void resolveUnknownScheduledStopPointOrStopId_returnsNull() {
    var unknownId = new FeedScopedId(FEED_ID, "unknown-stop");
    var reference = StopReference.ofScheduledStopPointOrStopId(unknownId);

    var stop = resolver.resolve(reference);

    assertNull(stop);
  }

  @Test
  void resolveEmptyReference_returnsNull() {
    var reference = new StopReference(null, null, StopResolutionStrategy.DIRECT);

    var stop = resolver.resolve(reference);

    assertNull(stop);
  }
}
