package org.opentripplanner.updater.trip.model;

import java.util.HashMap;
import java.util.Map;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model.site.StopLocation;

/**
 * Immutable record of the changes accumulated while applying the stop time updates of an existing
 * trip: which stops were replaced, which pickup/dropoff values changed, and whether there were
 * time updates, cancellations or no-data stops. Built by {@code StopTimeUpdateApplication}; consumed
 * by the {@code ScheduledTripUpdater} to decide whether a modified pattern is needed and how to mark the real-time state.
 */
public final class PatternModification {

  private final boolean hasTimeUpdates;
  private final boolean hasCancellations;
  private final boolean hasNoDataUpdates;
  private final Map<Integer, StopLocation> stopReplacements;
  private final Map<Integer, PickDrop> pickupChanges;
  private final Map<Integer, PickDrop> dropoffChanges;

  private PatternModification(Builder builder) {
    this.hasTimeUpdates = builder.hasTimeUpdates;
    this.hasCancellations = builder.hasCancellations;
    this.hasNoDataUpdates = builder.hasNoDataUpdates;
    this.stopReplacements = Map.copyOf(builder.stopReplacements);
    this.pickupChanges = Map.copyOf(builder.pickupChanges);
    this.dropoffChanges = Map.copyOf(builder.dropoffChanges);
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean hasPatternChanges() {
    return (
      !stopReplacements.isEmpty() ||
      !pickupChanges.isEmpty() ||
      !dropoffChanges.isEmpty() ||
      hasCancellations
    );
  }

  public boolean hasAnyUpdates() {
    return hasTimeUpdates || hasCancellations || hasNoDataUpdates || hasPatternChanges();
  }

  /**
   * Whether this update changes the trip's real-time state: there were time updates, cancellations
   * or pattern changes. NO_DATA stops are deliberately excluded — a trip whose only change is that
   * some (or all) stops are NO_DATA stays scheduled rather than being marked updated.
   */
  public boolean hasRealTimeChanges() {
    return hasTimeUpdates || hasPatternChanges();
  }

  public Map<Integer, StopLocation> stopReplacements() {
    return stopReplacements;
  }

  public Map<Integer, PickDrop> pickupChanges() {
    return pickupChanges;
  }

  public Map<Integer, PickDrop> dropoffChanges() {
    return dropoffChanges;
  }

  /** Mutable accumulator, frozen into an immutable {@link PatternModification} on {@link #build()}. */
  public static final class Builder {

    private boolean hasTimeUpdates = false;
    private boolean hasCancellations = false;
    private boolean hasNoDataUpdates = false;
    private final Map<Integer, StopLocation> stopReplacements = new HashMap<>();
    private final Map<Integer, PickDrop> pickupChanges = new HashMap<>();
    private final Map<Integer, PickDrop> dropoffChanges = new HashMap<>();

    public void markTimeUpdates() {
      this.hasTimeUpdates = true;
    }

    public void markCancellation() {
      this.hasCancellations = true;
    }

    public void markNoData() {
      this.hasNoDataUpdates = true;
    }

    public void putStopReplacement(int stopIndex, StopLocation stop) {
      stopReplacements.put(stopIndex, stop);
    }

    public void putPickup(int stopIndex, PickDrop pickup) {
      pickupChanges.put(stopIndex, pickup);
    }

    public void putDropoff(int stopIndex, PickDrop dropoff) {
      dropoffChanges.put(stopIndex, dropoff);
    }

    public PatternModification build() {
      return new PatternModification(this);
    }
  }
}
