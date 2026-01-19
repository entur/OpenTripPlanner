package org.opentripplanner.updater.trip.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Describes modifications to a trip's stop pattern. This includes:
 * <ul>
 *   <li>Stops that are skipped (not served on this trip)</li>
 *   <li>Extra stops that are added to the trip</li>
 * </ul>
 */
public final class StopPatternModification {

  private static final StopPatternModification EMPTY = new StopPatternModification(
    Set.of(),
    List.of()
  );

  private final Set<Integer> skippedStopIndices;
  private final List<AddedStop> addedStops;

  /**
   * @param skippedStopIndices Indices of stops in the original pattern that are skipped
   * @param addedStops Stops to add to the pattern (inserted after the specified index)
   */
  public StopPatternModification(Set<Integer> skippedStopIndices, List<AddedStop> addedStops) {
    this.skippedStopIndices = Set.copyOf(skippedStopIndices);
    this.addedStops = List.copyOf(addedStops);
  }

  /**
   * Information about a stop to be added to the pattern.
   */
  public static final class AddedStop {

    private final int insertAfterIndex;
    private final StopReference stopReference;

    /**
     * @param insertAfterIndex The index after which to insert the stop (-1 to insert at beginning)
     * @param stopReference Reference to the stop to add
     */
    public AddedStop(int insertAfterIndex, StopReference stopReference) {
      this.insertAfterIndex = insertAfterIndex;
      this.stopReference = Objects.requireNonNull(stopReference);
    }

    public int insertAfterIndex() {
      return insertAfterIndex;
    }

    public StopReference stopReference() {
      return stopReference;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      AddedStop addedStop = (AddedStop) o;
      return (
        insertAfterIndex == addedStop.insertAfterIndex &&
        Objects.equals(stopReference, addedStop.stopReference)
      );
    }

    @Override
    public int hashCode() {
      return Objects.hash(insertAfterIndex, stopReference);
    }

    @Override
    public String toString() {
      return (
        "AddedStop{" +
        "insertAfterIndex=" +
        insertAfterIndex +
        ", stopReference=" +
        stopReference +
        '}'
      );
    }
  }

  /**
   * Returns an empty modification (no changes to the stop pattern).
   */
  public static StopPatternModification empty() {
    return EMPTY;
  }

  /**
   * Create a builder for stop pattern modifications.
   */
  public static Builder builder() {
    return new Builder();
  }

  public Set<Integer> skippedStopIndices() {
    return skippedStopIndices;
  }

  public List<AddedStop> addedStops() {
    return addedStops;
  }

  /**
   * Returns true if this modification has any changes (skipped or added stops).
   */
  public boolean hasModifications() {
    return !skippedStopIndices.isEmpty() || !addedStops.isEmpty();
  }

  /**
   * Returns true if the stop at the given index is skipped.
   */
  public boolean isStopSkipped(int index) {
    return skippedStopIndices.contains(index);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    StopPatternModification that = (StopPatternModification) o;
    return (
      Objects.equals(skippedStopIndices, that.skippedStopIndices) &&
      Objects.equals(addedStops, that.addedStops)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(skippedStopIndices, addedStops);
  }

  @Override
  public String toString() {
    return (
      "StopPatternModification{" +
      "skippedStopIndices=" +
      skippedStopIndices +
      ", addedStops=" +
      addedStops +
      '}'
    );
  }

  /**
   * Builder for StopPatternModification.
   */
  public static class Builder {

    private Set<Integer> skippedStopIndices = new HashSet<>();
    private List<AddedStop> addedStops = new ArrayList<>();

    public Builder withSkippedStopIndices(Set<Integer> skippedStopIndices) {
      this.skippedStopIndices = new HashSet<>(skippedStopIndices);
      return this;
    }

    public Builder addSkippedStopIndex(int index) {
      this.skippedStopIndices.add(index);
      return this;
    }

    public Builder withAddedStops(List<AddedStop> addedStops) {
      this.addedStops = new ArrayList<>(addedStops);
      return this;
    }

    public Builder addAddedStop(int insertAfterIndex, StopReference stopReference) {
      this.addedStops.add(new AddedStop(insertAfterIndex, stopReference));
      return this;
    }

    public StopPatternModification build() {
      return new StopPatternModification(skippedStopIndices, addedStops);
    }
  }
}
