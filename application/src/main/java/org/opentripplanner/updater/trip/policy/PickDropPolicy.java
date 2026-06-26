package org.opentripplanner.updater.trip.policy;

import javax.annotation.Nullable;
import org.opentripplanner.model.PickDrop;

/**
 * Resolves the effective {@link PickDrop} value to apply for a stop, given the value parsed from
 * the real-time message and the scheduled value. This replaces the format-divergent
 * {@code PickDropChangeStrategy} enum branching: each format binds the matching policy constant
 * once at the boundary (see {@link FormatPolicy}).
 */
public interface PickDropPolicy {
  /**
   * @return the effective {@link PickDrop} value to apply, or {@code null} if no change is needed.
   */
  @Nullable
  PickDrop effective(PickDrop parsed, PickDrop scheduled);

  /** GTFS-RT: any parsed value is applied as-is. */
  PickDropPolicy EXACT_MATCH = (parsed, scheduled) -> parsed;

  /**
   * SIRI-ET: only routability transitions matter.
   * <ul>
   *   <li>routable &rarr; routable: preserve scheduled value, no pattern change ({@code null})</li>
   *   <li>non-routable &rarr; routable: re-enable the stop ({@code SCHEDULED})</li>
   *   <li>any &rarr; non-routable: apply the parsed value</li>
   * </ul>
   */
  PickDropPolicy ROUTABILITY_CHANGE_ONLY = (parsed, scheduled) -> {
    if (parsed.isRoutable()) {
      return scheduled.isNotRoutable() ? PickDrop.SCHEDULED : null;
    }
    return parsed;
  };
}
