package org.opentripplanner.updater.trip.policy;

import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ResolvedStopTimeUpdate;

/**
 * Resolves each stop time update to a position in the scheduled pattern. This replaces the
 * format-divergent {@code StopUpdateStrategy} enum and the FULL/PARTIAL branch in the handler.
 * <p>
 * Matching is stateful within one trip update: {@link #newCursor} returns a fresh {@link Cursor}
 * that advances as the updates are iterated, so a circular route (the same stop id appearing twice)
 * resolves to successive pattern positions.
 */
public sealed interface StopMatchingPolicy
  permits StopMatchingPolicy.Positional, StopMatchingPolicy.BySequenceOrId {
  /** The resolved pattern index for an update, plus the (optionally replaced) stop to apply. */
  record Match(int index, @Nullable StopLocation resolvedStop) {}

  interface Cursor {
    /** @throws UpdateException if the update cannot be matched to a pattern stop. */
    Match resolveIndex(ResolvedStopTimeUpdate update);
  }

  Cursor newCursor(TripPattern scheduledPattern, FeedScopedId tripId);

  /**
   * Whether the update must cover every scheduled stop exactly once by position (the FULL_UPDATE
   * precondition enforced by {@code UpdateExistingTripValidator}).
   */
  boolean requiresExactStopCount();

  /** SIRI-ET: the position in the update list IS the position in the pattern. */
  StopMatchingPolicy POSITIONAL = new Positional();
  /** GTFS-RT: match by explicit stop sequence, or by stop-id lookup in the pattern. */
  StopMatchingPolicy BY_SEQUENCE_OR_ID = new BySequenceOrId();

  final class Positional implements StopMatchingPolicy {

    @Override
    public boolean requiresExactStopCount() {
      return true;
    }

    @Override
    public Cursor newCursor(TripPattern scheduledPattern, FeedScopedId tripId) {
      return new Cursor() {
        private int next = 0;

        @Override
        public Match resolveIndex(ResolvedStopTimeUpdate update) {
          int index = next;
          StopLocation resolvedStop = update.stop();
          if (resolvedStop == null) {
            throw UpdateException.of(tripId, UpdateErrorType.UNKNOWN_STOP, index);
          }
          next++;
          return new Match(index, resolvedStop);
        }
      };
    }
  }

  final class BySequenceOrId implements StopMatchingPolicy {

    @Override
    public boolean requiresExactStopCount() {
      return false;
    }

    @Override
    public Cursor newCursor(TripPattern scheduledPattern, FeedScopedId tripId) {
      return new Cursor() {
        private int nextStopSearchIndex = 0;

        @Override
        public Match resolveIndex(ResolvedStopTimeUpdate update) {
          Integer stopSequence = update.stopSequence();
          if (stopSequence != null) {
            int stopIndex = stopSequence;
            if (stopIndex < 0 || stopIndex >= scheduledPattern.numberOfStops()) {
              throw UpdateException.of(tripId, UpdateErrorType.INVALID_STOP_SEQUENCE, stopIndex);
            }
            // Use the pre-resolved stop only if an assignedStopId was provided (stop replacement).
            StopLocation resolvedStop = update.stopReference().hasAssignedStopId()
              ? update.stop()
              : null;
            return new Match(stopIndex, resolvedStop);
          }

          // No stopSequence: look the stop up by id in the pattern.
          StopLocation resolvedStop = update.stop();
          if (resolvedStop == null) {
            throw UpdateException.of(tripId, UpdateErrorType.INVALID_STOP_REFERENCE);
          }
          int matchIndex = matchStopInPattern(resolvedStop, scheduledPattern, nextStopSearchIndex);
          // If not found from the current position, retry from the beginning (out-of-order updates).
          if (matchIndex < 0 && nextStopSearchIndex > 0) {
            matchIndex = matchStopInPattern(resolvedStop, scheduledPattern, 0);
          }
          if (matchIndex < 0) {
            throw UpdateException.of(tripId, UpdateErrorType.INVALID_STOP_REFERENCE);
          }
          nextStopSearchIndex = matchIndex + 1;
          return new Match(matchIndex, resolvedStop);
        }
      };
    }

    /**
     * Match a pre-resolved stop in the pattern by id lookup, starting from a given index. Supports
     * circular routes where the same stop appears multiple times.
     *
     * @return the matched stop index, or -1 if no match found
     */
    private static int matchStopInPattern(StopLocation stop, TripPattern pattern, int startFrom) {
      for (int i = startFrom; i < pattern.numberOfStops(); i++) {
        StopLocation patternStop = pattern.getStop(i);

        // Direct match by id
        if (patternStop.getId().equals(stop.getId())) {
          return i;
        }

        // Parent station match (quay changes)
        if (
          patternStop.getParentStation() != null &&
          stop.getParentStation() != null &&
          patternStop.getParentStation().getId().equals(stop.getParentStation().getId())
        ) {
          return i;
        }
      }

      return -1;
    }
  }
}
