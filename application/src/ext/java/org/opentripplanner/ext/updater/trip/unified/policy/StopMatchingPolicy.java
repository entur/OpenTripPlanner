package org.opentripplanner.ext.updater.trip.unified.policy;

import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.model.change.ResolvedStopTimeUpdate;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;

/**
 * Resolves each stop time update to a position in the scheduled pattern. This replaces the
 * format-divergent {@code StopUpdateStrategy} enum and the FULL/PARTIAL branch in the apply path.
 * <p>
 * Matching is stateful within one trip update: {@link #newCursor} returns a fresh {@link Cursor}
 * that advances as the updates are iterated, so a circular route (the same stop id appearing twice)
 * resolves to successive pattern positions.
 */
public sealed interface StopMatchingPolicy
  permits StopMatchingPolicy.Positional, StopMatchingPolicy.BySequenceOrId {
  /**
   * The resolved pattern index for an update.
   *
   * @param replacementStop the stop to serve the position with instead of the scheduled one, or
   *                        {@code null} if the update does not replace it. Which of the stops a
   *                        call names is a replacement is a format question, hence answered here.
   */
  record Match(int index, @Nullable StopLocation replacementStop) {}

  interface Cursor {
    /** @throws UpdateException if the update cannot be matched to a pattern stop. */
    Match resolveIndex(ResolvedStopTimeUpdate update);
  }

  /**
   * @param scheduledTripTimes the scheduled times of the trip, which know the {@code stop_sequence}
   *                           each of its calls is numbered with. Only a format that numbers its
   *                           calls needs them.
   */
  Cursor newCursor(TripPattern scheduledPattern, TripTimes scheduledTripTimes, FeedScopedId tripId);

  /**
   * Whether the update must cover every scheduled stop exactly once by position (the FULL_UPDATE
   * precondition enforced by {@code ExistingTripChangeFactory}).
   */
  boolean requiresExactStopCount();

  /** SIRI-ET: the position in the update list IS the position in the pattern. */
  StopMatchingPolicy POSITIONAL = new Positional();
  /** GTFS-RT: match by the stop sequence the static feed numbered the call with, or by stop id. */
  StopMatchingPolicy BY_SEQUENCE_OR_ID = new BySequenceOrId();

  final class Positional implements StopMatchingPolicy {

    @Override
    public boolean requiresExactStopCount() {
      return true;
    }

    @Override
    public Cursor newCursor(
      TripPattern scheduledPattern,
      TripTimes scheduledTripTimes,
      FeedScopedId tripId
    ) {
      return new Cursor() {
        private int next = 0;

        @Override
        public Match resolveIndex(ResolvedStopTimeUpdate update) {
          int index = next;
          // A format that matches by position states a stop change through the call's own stop
          // reference (a SIRI-ET StopPointRef naming another quay), so the referenced stop is both
          // required and the replacement.
          StopLocation referencedStop = update.referencedStop();
          if (referencedStop == null) {
            throw UpdateException.of(tripId, UpdateErrorType.UNKNOWN_STOP, index);
          }
          next++;
          return new Match(index, referencedStop);
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
    public Cursor newCursor(
      TripPattern scheduledPattern,
      TripTimes scheduledTripTimes,
      FeedScopedId tripId
    ) {
      return new Cursor() {
        private int nextStopSearchIndex = 0;
        private int nextUpdateIndex = 0;

        @Override
        public Match resolveIndex(ResolvedStopTimeUpdate update) {
          int updateIndex = nextUpdateIndex++;
          // A stop assignment says which stop the vehicle will use instead of the scheduled one, so
          // it is a replacement and never identifies the call.
          StopLocation assignedStop = update.assignedStop();

          Integer stopSequence = update.stopSequence();
          if (stopSequence != null) {
            return new Match(stopPositionOf(stopSequence, updateIndex), assignedStop);
          }

          // No stopSequence: look the stop the call reports it is at up by id in the pattern.
          StopLocation referencedStop = update.referencedStop();
          if (referencedStop == null) {
            throw UpdateException.of(
              tripId,
              UpdateErrorType.INVALID_STOP_REFERENCE,
              updateIndex,
              "the update identifies its stop neither by stop sequence nor by a known stop id"
            );
          }
          int matchIndex = matchStopInPattern(
            referencedStop,
            scheduledPattern,
            nextStopSearchIndex
          );
          // If not found from the current position, retry from the beginning (out-of-order updates).
          if (matchIndex < 0 && nextStopSearchIndex > 0) {
            matchIndex = matchStopInPattern(referencedStop, scheduledPattern, 0);
          }
          if (matchIndex < 0) {
            throw UpdateException.of(
              tripId,
              UpdateErrorType.INVALID_STOP_REFERENCE,
              updateIndex,
              "stop %s is not served by the trip".formatted(referencedStop.getId())
            );
          }
          nextStopSearchIndex = matchIndex + 1;
          return new Match(matchIndex, assignedStop);
        }

        /**
         * The position in the pattern of the call the static feed numbered {@code stopSequence}.
         * <p>
         * A GTFS {@code stop_sequence} is the number the call carries in {@code stop_times.txt}. It
         * only has to increase along the trip, so it is not a position in the pattern and has to be
         * looked up in the numbering of the trip itself.
         *
         * @param updateIndex the position of the update in the message, for diagnostics
         */
        private int stopPositionOf(int stopSequence, int updateIndex) {
          var stopPosition = scheduledTripTimes.stopPositionForGtfsSequence(stopSequence);
          if (stopPosition.isEmpty()) {
            throw UpdateException.of(
              tripId,
              UpdateErrorType.INVALID_STOP_SEQUENCE,
              updateIndex,
              "stop_sequence %d is not one of the stop sequences of the trip".formatted(
                stopSequence
              )
            );
          }
          return stopPosition.getAsInt();
        }
      };
    }

    /**
     * Match a pre-resolved stop in the pattern by id lookup, starting from a given index. Supports
     * circular routes where the same stop appears multiple times.
     * <p>
     * The match is on the stop id alone. A sibling quay of a scheduled stop is a different stop,
     * and a call at one says nothing about which call of the trip it is - the format that names a
     * replacement stop does so through its own field, not by naming a stop the trip does not call
     * at.
     *
     * @return the matched stop index, or -1 if no match found
     */
    private static int matchStopInPattern(StopLocation stop, TripPattern pattern, int startFrom) {
      for (int i = startFrom; i < pattern.numberOfStops(); i++) {
        if (pattern.getStop(i).getId().equals(stop.getId())) {
          return i;
        }
      }

      return -1;
    }
  }
}
