package org.opentripplanner.updater.trip.model.change;

import java.util.HashMap;
import java.util.Map;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;

/**
 * Immutable record of the changes accumulated while applying the stop time updates of an existing
 * trip: which stops were replaced, which pickup/dropoff values changed, and whether there were
 * time updates, cancellations or no-data stops. Built by {@link StopTimeUpdateApplication} and
 * consumed by {@link TripRevision#apply}, which asks it whether the update changes
 * anything at all, and - through {@link #applyTo} - what the trip's stop pattern becomes.
 */
public final class PatternModification {

  private final boolean hasTimeUpdates;
  private final boolean hasCancellations;
  private final Map<Integer, StopLocation> stopReplacements;
  private final Map<Integer, PickDrop> pickupChanges;
  private final Map<Integer, PickDrop> dropoffChanges;

  private PatternModification(Builder builder) {
    this.hasTimeUpdates = builder.hasTimeUpdates;
    this.hasCancellations = builder.hasCancellations;
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

  /**
   * Whether this update changes the trip's real-time state: there were time updates, cancellations
   * or pattern changes. NO_DATA stops are deliberately excluded — a trip whose only change is that
   * some (or all) stops are NO_DATA stays scheduled rather than being marked updated.
   */
  public boolean hasRealTimeChanges() {
    return hasTimeUpdates || hasPatternChanges();
  }

  /**
   * Apply the accumulated changes to the stop pattern of the scheduled pattern. The returned stop
   * pattern may still be equal to the original one: the builder deduplicates, so a change that
   * resolves to the scheduled value leaves the pattern untouched.
   */
  public StopPattern applyTo(TripPattern scheduledPattern) {
    var builder = scheduledPattern.copyPlannedStopPattern();

    if (!stopReplacements.isEmpty()) {
      builder.replaceStops(stopReplacements);
    }
    if (!pickupChanges.isEmpty()) {
      builder.updatePickups(pickupChanges);
    }
    if (!dropoffChanges.isEmpty()) {
      builder.updateDropoffs(dropoffChanges);
    }

    return builder.build();
  }

  /** Mutable accumulator, frozen into an immutable {@link PatternModification} on {@link #build()}. */
  public static final class Builder {

    private boolean hasTimeUpdates = false;
    private boolean hasCancellations = false;
    private final Map<Integer, StopLocation> stopReplacements = new HashMap<>();
    private final Map<Integer, PickDrop> pickupChanges = new HashMap<>();
    private final Map<Integer, PickDrop> dropoffChanges = new HashMap<>();

    public void markTimeUpdates() {
      this.hasTimeUpdates = true;
    }

    public void markCancellation() {
      this.hasCancellations = true;
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
