package org.opentripplanner.ext.updater.trip.unified.policy;

import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.model.StopSequence;
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
 * which is handed the updates in the order the message lists them, so a format that identifies its
 * calls by their position can count them off.
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
        private int nextUpdateIndex = 0;

        @Override
        public Match resolveIndex(ResolvedStopTimeUpdate update) {
          int updateIndex = nextUpdateIndex++;
          // A stop assignment says which stop the vehicle will use instead of the scheduled one, so
          // it is a replacement and never identifies the call.
          StopLocation assignedStop = update.assignedStop();

          StopSequence stopSequence = update.stopSequence();
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
          return new Match(stopPositionOfOnlyCallAt(referencedStop, updateIndex), assignedStop);
        }

        /**
         * The position in the pattern of the one call the trip makes at {@code stop}.
         * <p>
         * A stop id identifies a call only as long as the trip calls at that stop once. GTFS-RT
         * requires a trip that visits the same stop more than once to number its calls with their
         * stop sequence, exactly so that an update can say which of the visits it is for. Without
         * that number there is nothing in the message to pick a visit by, so rather than guess -
         * and move a prediction onto a call the producer did not mean - the reference is rejected
         * as the invalid data it is.
         * <p>
         * The match is on the stop id alone. A sibling quay of a scheduled stop is a different stop,
         * and a call at one says nothing about which call of the trip it is - the format that names
         * a replacement stop does so through its own field, not by naming a stop the trip does not
         * call at.
         *
         * @param updateIndex the position of the update in the message, for diagnostics
         */
        private int stopPositionOfOnlyCallAt(StopLocation stop, int updateIndex) {
          int position = -1;
          for (int i = 0; i < scheduledPattern.numberOfStops(); i++) {
            if (scheduledPattern.getStop(i).getId().equals(stop.getId())) {
              if (position >= 0) {
                throw UpdateException.of(
                  tripId,
                  UpdateErrorType.INVALID_STOP_REFERENCE,
                  updateIndex,
                  "the trip calls at stop %s more than once, so only a stop sequence can say which of the calls the update is for".formatted(
                    stop.getId()
                  )
                );
              }
              position = i;
            }
          }
          if (position < 0) {
            throw UpdateException.of(
              tripId,
              UpdateErrorType.INVALID_STOP_REFERENCE,
              updateIndex,
              "stop %s is not served by the trip".formatted(stop.getId())
            );
          }
          return position;
        }

        /**
         * The position in the pattern of the call the static feed numbered {@code stopSequence},
         * looked up in the numbering of the trip itself.
         *
         * @param updateIndex the position of the update in the message, for diagnostics
         */
        private int stopPositionOf(StopSequence stopSequence, int updateIndex) {
          var stopPosition = scheduledTripTimes.stopPositionForGtfsSequence(stopSequence.value());
          if (stopPosition.isEmpty()) {
            throw UpdateException.of(
              tripId,
              UpdateErrorType.INVALID_STOP_SEQUENCE,
              updateIndex,
              "stop_sequence %s is not one of the stop sequences of the trip".formatted(
                stopSequence
              )
            );
          }
          return stopPosition.getAsInt();
        }
      };
    }
  }
}
