package org.opentripplanner.ext.updater.trip.unified.policy;

import javax.annotation.Nullable;
import org.opentripplanner.model.PickDrop;

/**
 * Resolves the effective {@link PickDrop} value to apply for a call end (arrival or departure),
 * given what the real-time message reports and the scheduled value. This replaces the
 * format-divergent {@code PickDropChangeStrategy} enum branching: each format binds the matching
 * policy constant once at the boundary (see {@link FormatPolicy}).
 * <p>
 * A cancelled call end is a question of its own ({@link #effectiveWhenCancelled}) rather than a
 * reported value of {@link PickDrop#CANCELLED}, because the two are not the same thing: a cancelled
 * call end leaves an already non-routable value alone under SIRI-ET, while a call that reports
 * pass-thru boarding overwrites it. Once resolved to a {@code PickDrop} the distinction is gone, so
 * it has to be asked before.
 */
public sealed interface PickDropPolicy
  permits PickDropPolicy.ExactMatch, PickDropPolicy.RoutabilityChangeOnly {
  /**
   * @return the effective {@link PickDrop} value to apply, or {@code null} if no change is needed.
   */
  @Nullable
  PickDrop effective(PickDrop parsed, PickDrop scheduled);

  /**
   * The effective {@link PickDrop} value for a call end the message cancels, or {@code null} to
   * keep the scheduled value - which means the cancellation makes no pattern change at all.
   */
  @Nullable
  PickDrop effectiveWhenCancelled(PickDrop scheduled);

  /** GTFS-RT: any reported value is applied as-is, and a cancelled call cancels boarding. */
  PickDropPolicy EXACT_MATCH = new ExactMatch();

  /** SIRI-ET: only routability transitions matter. */
  PickDropPolicy ROUTABILITY_CHANGE_ONLY = new RoutabilityChangeOnly();

  /**
   * GTFS-RT: the feed states the pick/drop it wants, and a {@code SKIPPED} stop is cancelled at both
   * ends whatever the timetable said (legacy {@code TripTimesUpdater#applyUpdates} and
   * {@code StopTimeUpdate#getEffectivePickDrop}).
   */
  final class ExactMatch implements PickDropPolicy {

    @Override
    public PickDrop effective(PickDrop parsed, PickDrop scheduled) {
      return parsed;
    }

    @Override
    public PickDrop effectiveWhenCancelled(PickDrop scheduled) {
      return PickDrop.CANCELLED;
    }
  }

  /**
   * SIRI-ET: the message carries less information than the OTP pick/drop type, so a value only
   * changes when routability changes (legacy {@code PickDropChange}).
   * <ul>
   *   <li>routable &rarr; routable: preserve scheduled value, no pattern change ({@code null})</li>
   *   <li>non-routable &rarr; routable: re-enable the stop ({@code SCHEDULED})</li>
   *   <li>any &rarr; non-routable: apply the reported value</li>
   *   <li>cancelled: cancel boarding, unless it was not possible in the first place</li>
   * </ul>
   */
  final class RoutabilityChangeOnly implements PickDropPolicy {

    @Override
    public PickDrop effective(PickDrop parsed, PickDrop scheduled) {
      if (parsed.isRoutable()) {
        return scheduled.isNotRoutable() ? PickDrop.SCHEDULED : null;
      }
      return parsed;
    }

    @Override
    public PickDrop effectiveWhenCancelled(PickDrop scheduled) {
      return scheduled.isNotRoutable() ? null : PickDrop.CANCELLED;
    }
  }
}
