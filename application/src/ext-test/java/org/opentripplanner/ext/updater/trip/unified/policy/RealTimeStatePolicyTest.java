package org.opentripplanner.ext.updater.trip.unified.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;

/**
 * The two real-time state rules, compared side by side. They are the states each format's legacy
 * updater leaves on the trip times of a revised trip: GTFS-RT declares every trip it sends a message
 * about updated ({@code TripTimesUpdater}), while SIRI-ET declares only a changed pattern and lets
 * the times it applies speak for themselves ({@code ModifiedTripBuilder}).
 */
class RealTimeStatePolicyTest {

  private static final String TRIP_ID = "trip1";

  private final TransitTestEnvironment env = envWithOneTrip();

  private static TransitTestEnvironment envWithOneTrip() {
    var builder = TransitTestEnvironment.of();
    var stopA = builder.stop("A");
    var stopB = builder.stop("B");
    return builder
      .addTrip(TripInput.of(TRIP_ID).addStop(stopA, "10:00").addStop(stopB, "10:30"))
      .build();
  }

  /** A builder holding the trip's aimed times and nothing else - no real-time state yet. */
  private RealTimeTripTimesBuilder untouchedTrip() {
    return env.tripData(TRIP_ID).scheduledTripTimes().createRealTimeFromScheduledTimes();
  }

  @Test
  void gtfsRtMarksATripUpdatedEvenWhenTheMessageChangedNothing() {
    var builder = untouchedTrip();

    RealTimeStatePolicy.ALWAYS_UPDATED.mark(builder, false);

    assertTrue(builder.build().hasAnyUpdates());
  }

  @Test
  void gtfsRtReportsAChangedPatternAsUpdatedRatherThanModified() {
    var builder = untouchedTrip();

    RealTimeStatePolicy.ALWAYS_UPDATED.mark(builder, true);

    var tripTimes = builder.build();
    assertTrue(tripTimes.hasAnyUpdates());
    assertFalse(tripTimes.isTripPatternModified(), "GTFS-RT never reports a trip as MODIFIED");
  }

  @Test
  void siriLeavesATripScheduledWhenTheMessageChangedNothing() {
    var builder = untouchedTrip();

    RealTimeStatePolicy.MODIFIED_ON_PATTERN_CHANGE.mark(builder, false);

    assertFalse(builder.build().hasAnyUpdates());
  }

  @Test
  void siriReportsAChangedPatternAsModified() {
    var builder = untouchedTrip();

    RealTimeStatePolicy.MODIFIED_ON_PATTERN_CHANGE.mark(builder, true);

    assertTrue(builder.build().isTripPatternModified());
  }
}
