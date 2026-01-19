package org.opentripplanner.updater.trip.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;

class StopPatternModificationTest {

  private static final String FEED_ID = "F";
  private static final FeedScopedId STOP_1 = new FeedScopedId(FEED_ID, "stop1");
  private static final FeedScopedId STOP_2 = new FeedScopedId(FEED_ID, "stop2");
  private static final StopReference STOP_REF_1 = StopReference.ofStopId(STOP_1);
  private static final StopReference STOP_REF_2 = StopReference.ofStopId(STOP_2);

  @Test
  void emptyModificationHasNoChanges() {
    var modification = StopPatternModification.empty();

    assertTrue(modification.skippedStopIndices().isEmpty());
    assertTrue(modification.addedStops().isEmpty());
    assertFalse(modification.hasModifications());
  }

  @Test
  void withSkippedStops() {
    var modification = StopPatternModification.builder()
      .withSkippedStopIndices(Set.of(1, 3, 5))
      .build();

    assertEquals(Set.of(1, 3, 5), modification.skippedStopIndices());
    assertTrue(modification.addedStops().isEmpty());
    assertTrue(modification.hasModifications());
  }

  @Test
  void withAddedStops() {
    var addedStop = new StopPatternModification.AddedStop(2, STOP_REF_1);
    var modification = StopPatternModification.builder().withAddedStops(List.of(addedStop)).build();

    assertTrue(modification.skippedStopIndices().isEmpty());
    assertEquals(1, modification.addedStops().size());
    assertEquals(addedStop, modification.addedStops().get(0));
    assertTrue(modification.hasModifications());
  }

  @Test
  void withBothSkippedAndAddedStops() {
    var addedStop = new StopPatternModification.AddedStop(2, STOP_REF_1);
    var modification = StopPatternModification.builder()
      .withSkippedStopIndices(Set.of(1))
      .withAddedStops(List.of(addedStop))
      .build();

    assertEquals(Set.of(1), modification.skippedStopIndices());
    assertEquals(1, modification.addedStops().size());
    assertTrue(modification.hasModifications());
  }

  @Test
  void isStopSkippedAtIndex() {
    var modification = StopPatternModification.builder()
      .withSkippedStopIndices(Set.of(1, 3))
      .build();

    assertFalse(modification.isStopSkipped(0));
    assertTrue(modification.isStopSkipped(1));
    assertFalse(modification.isStopSkipped(2));
    assertTrue(modification.isStopSkipped(3));
  }

  @Test
  void addedStopRecord() {
    var addedStop = new StopPatternModification.AddedStop(5, STOP_REF_2);

    assertEquals(5, addedStop.insertAfterIndex());
    assertEquals(STOP_REF_2, addedStop.stopReference());
  }

  @Test
  void builderAddSkippedIndex() {
    var modification = StopPatternModification.builder()
      .addSkippedStopIndex(2)
      .addSkippedStopIndex(4)
      .build();

    assertEquals(Set.of(2, 4), modification.skippedStopIndices());
  }

  @Test
  void builderAddAddedStop() {
    var modification = StopPatternModification.builder()
      .addAddedStop(1, STOP_REF_1)
      .addAddedStop(3, STOP_REF_2)
      .build();

    assertEquals(2, modification.addedStops().size());
    assertEquals(1, modification.addedStops().get(0).insertAfterIndex());
    assertEquals(3, modification.addedStops().get(1).insertAfterIndex());
  }
}
