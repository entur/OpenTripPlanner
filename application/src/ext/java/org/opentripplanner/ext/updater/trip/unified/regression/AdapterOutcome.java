package org.opentripplanner.ext.updater.trip.unified.regression;

import javax.annotation.Nullable;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateResult;

/**
 * What one adapter did with a single real-time trip update.
 * <p>
 * The three cases are deliberately kept apart. Producing nothing is a legitimate outcome — real
 * feeds carry updates referencing unknown trips or violating the timetable invariants, and both
 * adapters are expected to reject those — but it is not the same as producing the same thing, and a
 * deliberate rejection is not the same as an adapter that threw. Collapsing them into a nullable
 * record is what let a whole poll of crashed shadow updates be reported as a 100 % match.
 * <p>
 * Used exclusively by the shadow comparison mode.
 */
public sealed interface AdapterOutcome {
  /**
   * The adapter accepted the update and produced the record it would write to the timetable.
   */
  record Published(RealTimeTripUpdate update) implements AdapterOutcome {}

  /**
   * The adapter deliberately rejected the update, reporting the given error type. Both adapters
   * rejecting the same input for the same reason is expected and common; rejecting it for
   * different reasons is a divergence.
   */
  record Rejected(UpdateErrorType reason) implements AdapterOutcome {}

  /**
   * The adapter threw something it does not model as a rejection, or produced neither a record nor
   * an error. This is a defect in the adapter rather than a property of the input, so it counts as
   * an error even when the other adapter also produced nothing.
   */
  record Crashed(String detail) implements AdapterOutcome {}

  /**
   * The outcome of a primary (legacy) handler run over a single trip. The handler writes its record
   * to the recording buffer and reports success or failure through the update result, so the two
   * together say what it did.
   */
  static AdapterOutcome ofPrimary(UpdateResult result, @Nullable RealTimeTripUpdate recorded) {
    if (recorded != null) {
      return new Published(recorded);
    }
    if (!result.errors().isEmpty()) {
      return new Rejected(result.errors().getFirst().errorType());
    }
    // Every legacy success path writes its record through TripUpdateApplier, so a run that reports
    // neither a record nor an error has silently dropped the update.
    return new Crashed("reported neither a real-time trip update nor an error");
  }

  /**
   * A short human-readable description, used in the log line and in the mismatch report header.
   */
  default String describe() {
    return switch (this) {
      case Published published -> "published pattern " + published.update().pattern().getId();
      case Rejected rejected -> "rejected: " + rejected.reason();
      case Crashed crashed -> "crashed: " + crashed.detail();
    };
  }
}
