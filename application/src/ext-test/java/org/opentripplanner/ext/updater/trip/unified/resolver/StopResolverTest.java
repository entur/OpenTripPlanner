package org.opentripplanner.ext.updater.trip.unified.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.core.model.id.FeedScopedIdForTestFactory;
import org.opentripplanner.ext.updater.trip.unified.model.command.StopReference;
import org.opentripplanner.ext.updater.trip.unified.model.command.StopResolutionStrategy;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.service.TransitService;

/**
 * Tests for {@link StopResolver}.
 */
class StopResolverTest {

  private static final String FEED_ID = FeedScopedIdForTestFactory.FEED_ID;
  private static final String STOP_A = "stopA";
  private static final String STOP_B = "stopB";
  private static final String AREA_STOP = "areaStop";

  private TransitService transitService;
  private StopResolver resolver;

  @BeforeEach
  void setUp() {
    var envBuilder = TransitTestEnvironment.of().addStops(STOP_A, STOP_B);
    envBuilder.areaStop(AREA_STOP);
    var env = envBuilder.build();
    transitService = env.transitService();
    resolver = new StopResolver(transitService);
  }

  @Test
  void resolveByStopId() {
    var stopId = new FeedScopedId(FEED_ID, STOP_A);
    var reference = StopReference.ofStopId(stopId);

    var stop = resolver.resolveReferencedStop(reference);

    assertNotNull(stop);
    assertEquals(stopId, stop.getId());
  }

  @Test
  void referencedStopIsTheCallsOwnStopEvenWhenAnotherIsAssigned() {
    var originalId = new FeedScopedId(FEED_ID, STOP_A);
    var assignedId = new FeedScopedId(FEED_ID, STOP_B);
    var reference = StopReference.ofStopId(originalId, assignedId);

    var stop = resolver.resolveReferencedStop(reference);

    // The assignment says where the vehicle goes instead, not which call this is.
    assertNotNull(stop);
    assertEquals(originalId, stop.getId());
  }

  @Test
  void resolveAssignedStopId() {
    var originalId = new FeedScopedId(FEED_ID, STOP_A);
    var assignedId = new FeedScopedId(FEED_ID, STOP_B);
    var reference = StopReference.ofStopId(originalId, assignedId);

    var stop = resolver.resolveAssignedStop(reference);

    assertNotNull(stop);
    assertEquals(assignedId, stop.getId());
  }

  @Test
  void resolveAbsentAssignedStopId_returnsNull() {
    var reference = StopReference.ofStopId(new FeedScopedId(FEED_ID, STOP_A));

    assertNull(resolver.resolveAssignedStop(reference));
  }

  @Test
  void resolveUnknownAssignedStopId_returnsNull() {
    var reference = StopReference.ofStopId(
      new FeedScopedId(FEED_ID, STOP_A),
      new FeedScopedId(FEED_ID, "unknown-stop")
    );

    // An unresolvable assignment is no assignment: the scheduled stop is kept.
    assertNull(resolver.resolveAssignedStop(reference));
  }

  @Test
  void resolveByScheduledStopPointOrStopId() {
    var stopId = new FeedScopedId(FEED_ID, STOP_A);
    var reference = StopReference.ofScheduledStopPointOrStopId(stopId);

    var stop = resolver.resolveReferencedStop(reference);

    // Falls back to direct lookup since no scheduled stop point mapping exists
    assertNotNull(stop);
    assertEquals(stopId, stop.getId());
  }

  @Test
  void resolveUnknownStopId_returnsNull() {
    var unknownId = new FeedScopedId(FEED_ID, "unknown-stop");
    var reference = StopReference.ofStopId(unknownId);

    var stop = resolver.resolveReferencedStop(reference);

    assertNull(stop);
  }

  @Test
  void resolveUnknownScheduledStopPointOrStopId_returnsNull() {
    var unknownId = new FeedScopedId(FEED_ID, "unknown-stop");
    var reference = StopReference.ofScheduledStopPointOrStopId(unknownId);

    var stop = resolver.resolveReferencedStop(reference);

    assertNull(stop);
  }

  @Test
  void resolveFlexStopId_returnsNull() {
    var areaStopId = new FeedScopedId(FEED_ID, AREA_STOP);
    var reference = StopReference.ofStopId(areaStopId);

    // A trip update describes a call at a fixed stop: an id naming a flex stop is unknown, like
    // the legacy updaters treat it.
    assertNull(resolver.resolveReferencedStop(reference));
  }

  @Test
  void resolveFlexScheduledStopPointOrStopId_returnsNull() {
    var areaStopId = new FeedScopedId(FEED_ID, AREA_STOP);
    var reference = StopReference.ofScheduledStopPointOrStopId(areaStopId);

    assertNull(resolver.resolveReferencedStop(reference));
  }

  @Test
  void resolveFlexAssignedStopId_returnsNull() {
    var reference = StopReference.ofStopId(
      new FeedScopedId(FEED_ID, STOP_A),
      new FeedScopedId(FEED_ID, AREA_STOP)
    );

    // An assignment to a flex stop is unresolvable: the scheduled stop is kept.
    assertNull(resolver.resolveAssignedStop(reference));
  }

  @Test
  void resolveEmptyReference_returnsNull() {
    var reference = new StopReference(null, null, StopResolutionStrategy.DIRECT);

    assertNull(resolver.resolveReferencedStop(reference));
    assertNull(resolver.resolveAssignedStop(reference));
  }
}
