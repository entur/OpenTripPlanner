package org.opentripplanner.updater.trip.gtfs.moduletests.delay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;

/**
 * Tests updating and reverting the stops/platforms for existing trips.
 */
class AssignedStopIdsTest implements RealtimeTestConstants {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 1, 1);
  private static final LocalDate SERVICE_DATE_PLUS = SERVICE_DATE.plusDays(1);
  private static final ZoneId TIME_ZONE = ZoneId.of("Europe/Paris");
  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of(
    SERVICE_DATE,
    TIME_ZONE
  );
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stop(STOP_B_ID);
  private final RegularStop STOP_C = ENV_BUILDER.stop(STOP_C_ID);

  // these stops need to be created for use in assigned stops
  private final RegularStop STOP_D = ENV_BUILDER.stop(STOP_D_ID);
  private final RegularStop STOP_E = ENV_BUILDER.stop(STOP_E_ID);

  private final TripInput TRIP_1_INPUT = TripInput.of(TRIP_1_ID)
    .withServiceDates(SERVICE_DATE, SERVICE_DATE_PLUS)
    .addStop(STOP_A, "10:00:00", "10:00:00")
    .addStop(STOP_B, "10:01:00", "10:01:00")
    .addStop(STOP_C, "10:02:00", "10:02:00");

  /** A trip that also calls at the stop the assignment tests assign, so it can be mismatched. */
  private final TripInput TRIP_1_CALLING_AT_D_INPUT = TripInput.of(TRIP_1_ID)
    .withServiceDates(SERVICE_DATE, SERVICE_DATE_PLUS)
    .addStop(STOP_A, "10:00:00", "10:00:00")
    .addStop(STOP_B, "10:01:00", "10:01:00")
    .addStop(STOP_C, "10:02:00", "10:02:00")
    .addStop(STOP_D, "10:03:00", "10:03:00");

  private final TripInput TRIP_2_INPUT = TripInput.of(TRIP_2_ID)
    .withServiceDates(SERVICE_DATE, SERVICE_DATE_PLUS)
    .addStop(STOP_A, "11:00:00", "11:00:00")
    .addStop(STOP_B, "11:01:00", "11:01:00")
    .addStop(STOP_C, "11:02:00", "11:02:00");

  @Test
  void assignedThenRevertedStopIds() {
    var env = ENV_BUILDER.addTrip(TRIP_1_INPUT).build();

    assertFalse(env.tripData(TRIP_1_ID).tripPattern().isStopPatternModifiedInRealTime());
    assertEquals(List.of("F:Pattern1[S]"), env.raptorData().summarizePatterns());

    var rt = GtfsRtTestHelper.of(env);
    var tripUpdate1 = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addAssignedStopTime(0, "09:50:00", STOP_D_ID)
      .addStopTime(1, "10:01:00")
      .addStopTime(2, "10:02:00")
      .build();

    assertSuccess(rt.applyTripUpdate(tripUpdate1));
    assertEquals(
      "U | D 9:50 9:50 | B 10:01 10:01 | C 10:02 10:02",
      env.tripData(TRIP_1_ID).showTimetable()
    );
    assertTrue(env.tripData(TRIP_1_ID).tripPattern().isStopPatternModifiedInRealTime());
    assertEquals(List.of("F:Route1::001:RT[U]"), env.raptorData().summarizePatterns());

    var tripUpdate2 = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addAssignedStopTime(0, "09:55:00", STOP_E_ID)
      .addStopTime(1, "10:01:00")
      .addStopTime(2, "10:02:00")
      .build();

    assertSuccess(rt.applyTripUpdate(tripUpdate2));
    assertEquals(
      "U | E 9:55 9:55 | B 10:01 10:01 | C 10:02 10:02",
      env.tripData(TRIP_1_ID).showTimetable()
    );
    assertTrue(env.tripData(TRIP_1_ID).tripPattern().isStopPatternModifiedInRealTime());
    assertEquals(List.of("F:Route1::002:RT[U]"), env.raptorData().summarizePatterns());

    var tripUpdate3 = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addAssignedStopTime(0, "10:01:00", STOP_A_ID)
      .addStopTime(1, "10:02:00")
      .addStopTime(2, "10:03:00")
      .build();

    assertSuccess(rt.applyTripUpdate(tripUpdate3));
    assertEquals(
      "U | A 10:01 10:01 | B 10:02 10:02 | C 10:03 10:03",
      env.tripData(TRIP_1_ID).showTimetable()
    );

    assertFalse(env.tripData(TRIP_1_ID).tripPattern().isStopPatternModifiedInRealTime());
    assertEquals(List.of("F:Pattern1[U]"), env.raptorData().summarizePatterns());
  }

  /**
   * A call that identifies itself by stop id says which scheduled call the update is about; an
   * assigned stop id says which stop the vehicle will use instead. Mixing the two up makes the
   * update land on the assigned stop's position, which here would be the trip's last call.
   */
  @Test
  void assignedStopIdWithoutStopSequenceAppliesTimesAtTheReportedStop() {
    var env = ENV_BUILDER.addTrip(TRIP_1_CALLING_AT_D_INPUT).build();

    var rt = GtfsRtTestHelper.of(env);
    var tripUpdate = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addStopTime(STOP_A_ID, "10:00:00")
      // the call at B is served at D instead
      .addAssignedStopTime(STOP_B_ID, "10:05:00", STOP_D_ID)
      .addStopTime(STOP_C_ID, "10:06:00")
      .addStopTime(STOP_D_ID, "10:07:00")
      .build();

    assertSuccess(rt.applyTripUpdate(tripUpdate));
    assertEquals(
      "U | A 10:00 10:00 | D 10:05 10:05 | C 10:06 10:06 | D 10:07 10:07",
      env.tripData(TRIP_1_ID).showTimetable()
    );
    assertTrue(env.tripData(TRIP_1_ID).tripPattern().isStopPatternModifiedInRealTime());
  }

  /**
   * The assigned stop does not have to be one the trip calls at - it is a replacement, not a way of
   * finding the call.
   */
  @Test
  void assignedStopOutsideThePatternIsStillAReplacement() {
    var env = ENV_BUILDER.addTrip(TRIP_1_INPUT).build();

    var rt = GtfsRtTestHelper.of(env);
    var tripUpdate = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addStopTime(STOP_A_ID, "10:00:00")
      .addAssignedStopTime(STOP_B_ID, "10:05:00", STOP_E_ID)
      .addStopTime(STOP_C_ID, "10:06:00")
      .build();

    assertSuccess(rt.applyTripUpdate(tripUpdate));
    assertEquals(
      "U | A 10:00 10:00 | E 10:05 10:05 | C 10:06 10:06",
      env.tripData(TRIP_1_ID).showTimetable()
    );
    assertTrue(env.tripData(TRIP_1_ID).tripPattern().isStopPatternModifiedInRealTime());
  }

  /**
   * An assignment naming a stop the transit model does not know costs the trip its pattern change,
   * not its real-time times.
   */
  @Test
  void unresolvableAssignedStopIdKeepsTheTimesAndDropsThePatternChange() {
    var env = ENV_BUILDER.addTrip(TRIP_1_INPUT).build();

    var rt = GtfsRtTestHelper.of(env);
    var tripUpdate = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addAssignedStopTime(0, "10:05:00", "no-such-stop")
      .build();

    assertSuccess(rt.applyTripUpdate(tripUpdate));
    assertEquals(
      "U | A 10:05 10:05 | B 10:06 10:06 | C 10:07 10:07",
      env.tripData(TRIP_1_ID).showTimetable()
    );
    assertFalse(env.tripData(TRIP_1_ID).tripPattern().isStopPatternModifiedInRealTime());
  }

  @Test
  void reuseRealtimeTripPatterns() {
    var env = ENV_BUILDER.addTrip(TRIP_1_INPUT).addTrip(TRIP_2_INPUT).build();

    assertFalse(env.tripData(TRIP_1_ID).tripPattern().isStopPatternModifiedInRealTime());
    assertFalse(env.tripData(TRIP_2_ID).tripPattern().isStopPatternModifiedInRealTime());
    assertEquals(List.of("F:Pattern1[S,S]"), env.raptorData().summarizePatterns());

    var rt = GtfsRtTestHelper.of(env);
    var tripUpdate1 = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addAssignedStopTime(0, "10:01", STOP_E_ID)
      .build();

    var tripUpdate2 = rt
      .tripUpdateScheduled(TRIP_2_ID)
      .addAssignedStopTime(0, "11:01", STOP_E_ID)
      .build();

    assertSuccess(rt.applyTripUpdate(tripUpdate1));
    assertEquals(
      "U | E 10:01 10:01 | B 10:02 10:02 | C 10:03 10:03",
      env.tripData(TRIP_1_ID).showTimetable()
    );
    assertEquals(
      "S | A 11:00 11:00 | B 11:01 11:01 | C 11:02 11:02",
      env.tripData(TRIP_2_ID).showTimetable()
    );
    assertTrue(env.tripData(TRIP_1_ID).tripPattern().isStopPatternModifiedInRealTime());
    assertFalse(env.tripData(TRIP_2_ID).tripPattern().isStopPatternModifiedInRealTime());
    assertEquals(
      List.of("F:Pattern1[S]", "F:Route1::001:RT[U]"),
      env.raptorData().summarizePatterns()
    );

    assertSuccess(rt.applyTripUpdates(List.of(tripUpdate1, tripUpdate2)));
    assertEquals(
      "U | E 10:01 10:01 | B 10:02 10:02 | C 10:03 10:03",
      env.tripData(TRIP_1_ID).showTimetable()
    );
    assertEquals(
      "U | E 11:01 11:01 | B 11:02 11:02 | C 11:03 11:03",
      env.tripData(TRIP_2_ID).showTimetable()
    );
    assertTrue(env.tripData(TRIP_1_ID).tripPattern().isStopPatternModifiedInRealTime());
    assertTrue(env.tripData(TRIP_2_ID).tripPattern().isStopPatternModifiedInRealTime());
    assertEquals(List.of("F:Route1::001:RT[U,U]"), env.raptorData().summarizePatterns());

    assertSuccess(rt.applyTripUpdate(tripUpdate2));
    assertEquals(
      "S | A 10:00 10:00 | B 10:01 10:01 | C 10:02 10:02",
      env.tripData(TRIP_1_ID).showTimetable()
    );
    assertEquals(
      "U | E 11:01 11:01 | B 11:02 11:02 | C 11:03 11:03",
      env.tripData(TRIP_2_ID).showTimetable()
    );
    assertFalse(env.tripData(TRIP_1_ID).tripPattern().isStopPatternModifiedInRealTime());
    assertTrue(env.tripData(TRIP_2_ID).tripPattern().isStopPatternModifiedInRealTime());
    assertEquals(
      List.of("F:Pattern1[S]", "F:Route1::001:RT[U]"),
      env.raptorData().summarizePatterns()
    );

    assertSuccess(
      rt.applyTripUpdates(
        List.of(
          rt.tripUpdateScheduled(TRIP_1_ID).addDelayedStopTime(0, 0).build(),
          rt.tripUpdateScheduled(TRIP_2_ID).addDelayedStopTime(0, 0).build()
        )
      )
    );
    assertFalse(env.tripData(TRIP_1_ID).tripPattern().isStopPatternModifiedInRealTime());
    assertFalse(env.tripData(TRIP_2_ID).tripPattern().isStopPatternModifiedInRealTime());
    assertEquals(List.of("F:Pattern1[U,U]"), env.raptorData().summarizePatterns());
  }

  @Test
  void reuseRealtimeTripPatternsOnDifferentServiceDates() {
    var env = ENV_BUILDER.addTrip(TRIP_1_INPUT).addTrip(TRIP_2_INPUT).build();

    assertFalse(
      env.tripData(TRIP_1_ID, SERVICE_DATE).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertFalse(
      env.tripData(TRIP_1_ID, SERVICE_DATE_PLUS).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertFalse(
      env.tripData(TRIP_2_ID, SERVICE_DATE).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertFalse(
      env.tripData(TRIP_2_ID, SERVICE_DATE_PLUS).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertEquals(List.of("F:Pattern1[S,S]"), env.raptorData(SERVICE_DATE).summarizePatterns());
    assertEquals(List.of("F:Pattern1[S,S]"), env.raptorData(SERVICE_DATE_PLUS).summarizePatterns());

    var rt = GtfsRtTestHelper.of(env);
    var tripUpdate11 = rt
      .tripUpdateScheduled(TRIP_1_ID, SERVICE_DATE)
      .addAssignedStopTime(0, "10:01", STOP_E_ID)
      .build();
    var tripUpdate12 = rt
      .tripUpdateScheduled(TRIP_2_ID, SERVICE_DATE)
      .addAssignedStopTime(0, "11:01", STOP_E_ID)
      .build();

    var tripUpdate21 = rt
      .tripUpdateScheduled(TRIP_1_ID, SERVICE_DATE_PLUS)
      .addAssignedStopTime(0, "10:01", STOP_E_ID)
      .build();
    var tripUpdate22 = rt
      .tripUpdateScheduled(TRIP_2_ID, SERVICE_DATE_PLUS)
      .addAssignedStopTime(0, "11:01", STOP_E_ID)
      .build();

    assertSuccess(rt.applyTripUpdates(List.of(tripUpdate11, tripUpdate12)));
    assertEquals(
      "U | E 10:01 10:01 | B 10:02 10:02 | C 10:03 10:03",
      env.tripData(TRIP_1_ID, SERVICE_DATE).showTimetable()
    );
    assertEquals(
      "U | E 11:01 11:01 | B 11:02 11:02 | C 11:03 11:03",
      env.tripData(TRIP_2_ID, SERVICE_DATE).showTimetable()
    );
    assertEquals(
      "S | A 10:00 10:00 | B 10:01 10:01 | C 10:02 10:02",
      env.tripData(TRIP_1_ID, SERVICE_DATE_PLUS).showTimetable()
    );
    assertEquals(
      "S | A 11:00 11:00 | B 11:01 11:01 | C 11:02 11:02",
      env.tripData(TRIP_2_ID, SERVICE_DATE_PLUS).showTimetable()
    );
    assertTrue(
      env.tripData(TRIP_1_ID, SERVICE_DATE).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertTrue(
      env.tripData(TRIP_2_ID, SERVICE_DATE).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertFalse(
      env.tripData(TRIP_1_ID, SERVICE_DATE_PLUS).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertFalse(
      env.tripData(TRIP_2_ID, SERVICE_DATE_PLUS).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertEquals(
      List.of("F:Route1::001:RT[U,U]"),
      env.raptorData(SERVICE_DATE).summarizePatterns()
    );
    assertEquals(List.of("F:Pattern1[S,S]"), env.raptorData(SERVICE_DATE_PLUS).summarizePatterns());

    assertSuccess(
      rt.applyTripUpdates(List.of(tripUpdate11, tripUpdate12, tripUpdate21, tripUpdate22))
    );
    assertEquals(
      "U | E 10:01 10:01 | B 10:02 10:02 | C 10:03 10:03",
      env.tripData(TRIP_1_ID, SERVICE_DATE).showTimetable()
    );
    assertEquals(
      "U | E 11:01 11:01 | B 11:02 11:02 | C 11:03 11:03",
      env.tripData(TRIP_2_ID, SERVICE_DATE).showTimetable()
    );
    assertEquals(
      "U | E 10:01 10:01 | B 10:02 10:02 | C 10:03 10:03",
      env.tripData(TRIP_1_ID, SERVICE_DATE_PLUS).showTimetable()
    );
    assertEquals(
      "U | E 11:01 11:01 | B 11:02 11:02 | C 11:03 11:03",
      env.tripData(TRIP_2_ID, SERVICE_DATE_PLUS).showTimetable()
    );
    assertTrue(
      env.tripData(TRIP_1_ID, SERVICE_DATE).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertTrue(
      env.tripData(TRIP_2_ID, SERVICE_DATE).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertTrue(
      env.tripData(TRIP_1_ID, SERVICE_DATE_PLUS).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertTrue(
      env.tripData(TRIP_2_ID, SERVICE_DATE_PLUS).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertEquals(
      List.of("F:Route1::001:RT[U,U]"),
      env.raptorData(SERVICE_DATE).summarizePatterns()
    );
    assertEquals(
      List.of("F:Route1::001:RT[U,U]"),
      env.raptorData(SERVICE_DATE_PLUS).summarizePatterns()
    );

    assertSuccess(rt.applyTripUpdates(List.of(tripUpdate21, tripUpdate22)));
    assertEquals(
      "S | A 10:00 10:00 | B 10:01 10:01 | C 10:02 10:02",
      env.tripData(TRIP_1_ID, SERVICE_DATE).showTimetable()
    );
    assertEquals(
      "S | A 11:00 11:00 | B 11:01 11:01 | C 11:02 11:02",
      env.tripData(TRIP_2_ID, SERVICE_DATE).showTimetable()
    );
    assertEquals(
      "U | E 10:01 10:01 | B 10:02 10:02 | C 10:03 10:03",
      env.tripData(TRIP_1_ID, SERVICE_DATE_PLUS).showTimetable()
    );
    assertEquals(
      "U | E 11:01 11:01 | B 11:02 11:02 | C 11:03 11:03",
      env.tripData(TRIP_2_ID, SERVICE_DATE_PLUS).showTimetable()
    );
    assertFalse(
      env.tripData(TRIP_1_ID, SERVICE_DATE).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertFalse(
      env.tripData(TRIP_2_ID, SERVICE_DATE).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertTrue(
      env.tripData(TRIP_1_ID, SERVICE_DATE_PLUS).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertTrue(
      env.tripData(TRIP_2_ID, SERVICE_DATE_PLUS).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertEquals(List.of("F:Pattern1[S,S]"), env.raptorData(SERVICE_DATE).summarizePatterns());
    assertEquals(
      List.of("F:Route1::001:RT[U,U]"),
      env.raptorData(SERVICE_DATE_PLUS).summarizePatterns()
    );

    assertSuccess(
      rt.applyTripUpdates(
        List.of(
          rt.tripUpdateScheduled(TRIP_1_ID, SERVICE_DATE).addDelayedStopTime(0, 0).build(),
          rt.tripUpdateScheduled(TRIP_2_ID, SERVICE_DATE).addDelayedStopTime(0, 0).build(),
          rt.tripUpdateScheduled(TRIP_1_ID, SERVICE_DATE_PLUS).addDelayedStopTime(0, 0).build(),
          rt.tripUpdateScheduled(TRIP_2_ID, SERVICE_DATE_PLUS).addDelayedStopTime(0, 0).build()
        )
      )
    );
    assertFalse(
      env.tripData(TRIP_1_ID, SERVICE_DATE).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertFalse(
      env.tripData(TRIP_2_ID, SERVICE_DATE).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertFalse(
      env.tripData(TRIP_1_ID, SERVICE_DATE_PLUS).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertFalse(
      env.tripData(TRIP_2_ID, SERVICE_DATE_PLUS).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertEquals(List.of("F:Pattern1[U,U]"), env.raptorData(SERVICE_DATE).summarizePatterns());
    assertEquals(List.of("F:Pattern1[U,U]"), env.raptorData(SERVICE_DATE_PLUS).summarizePatterns());
  }

  @Test
  void reuseScheduledTripPatterns() {
    var env = ENV_BUILDER.addTrip(TRIP_1_INPUT).addTrip(TRIP_2_INPUT).build();

    assertFalse(env.tripData(TRIP_1_ID).tripPattern().isStopPatternModifiedInRealTime());
    assertFalse(env.tripData(TRIP_2_ID).tripPattern().isStopPatternModifiedInRealTime());
    assertEquals(List.of("F:Pattern1[S,S]"), env.raptorData().summarizePatterns());

    var rt = GtfsRtTestHelper.of(env);
    var tripUpdate1 = rt.tripUpdateScheduled(TRIP_1_ID).addDelayedStopTime(0, 60).build();

    var tripUpdate2 = rt.tripUpdateScheduled(TRIP_2_ID).addDelayedStopTime(0, 60).build();

    assertSuccess(rt.applyTripUpdate(tripUpdate1));
    assertEquals(
      "U | A 10:01 10:01 | B 10:02 10:02 | C 10:03 10:03",
      env.tripData(TRIP_1_ID).showTimetable()
    );
    assertEquals(
      "S | A 11:00 11:00 | B 11:01 11:01 | C 11:02 11:02",
      env.tripData(TRIP_2_ID).showTimetable()
    );
    assertEquals(List.of("F:Pattern1[U,S]"), env.raptorData().summarizePatterns());

    assertSuccess(rt.applyTripUpdates(List.of(tripUpdate1, tripUpdate2)));
    assertEquals(
      "U | A 10:01 10:01 | B 10:02 10:02 | C 10:03 10:03",
      env.tripData(TRIP_1_ID).showTimetable()
    );
    assertEquals(
      "U | A 11:01 11:01 | B 11:02 11:02 | C 11:03 11:03",
      env.tripData(TRIP_2_ID).showTimetable()
    );
    assertEquals(List.of("F:Pattern1[U,U]"), env.raptorData().summarizePatterns());

    assertSuccess(rt.applyTripUpdate(tripUpdate2));
    assertEquals(
      "S | A 10:00 10:00 | B 10:01 10:01 | C 10:02 10:02",
      env.tripData(TRIP_1_ID).showTimetable()
    );
    assertEquals(
      "U | A 11:01 11:01 | B 11:02 11:02 | C 11:03 11:03",
      env.tripData(TRIP_2_ID).showTimetable()
    );
    assertEquals(List.of("F:Pattern1[S,U]"), env.raptorData().summarizePatterns());
  }

  @Test
  void reuseScheduledTripPatternsOnDifferentServiceDates() {
    var env = ENV_BUILDER.addTrip(TRIP_1_INPUT).build();

    assertFalse(
      env.tripData(TRIP_1_ID, SERVICE_DATE).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertFalse(
      env.tripData(TRIP_1_ID, SERVICE_DATE_PLUS).tripPattern().isStopPatternModifiedInRealTime()
    );
    assertEquals(List.of("F:Pattern1[S]"), env.raptorData(SERVICE_DATE).summarizePatterns());
    assertEquals(List.of("F:Pattern1[S]"), env.raptorData(SERVICE_DATE_PLUS).summarizePatterns());

    var rt = GtfsRtTestHelper.of(env);
    var tripUpdate1 = rt
      .tripUpdateScheduled(TRIP_1_ID, SERVICE_DATE)
      .addDelayedStopTime(0, 60)
      .build();

    var tripUpdate2 = rt
      .tripUpdateScheduled(TRIP_1_ID, SERVICE_DATE_PLUS)
      .addDelayedStopTime(0, 60)
      .build();

    assertSuccess(rt.applyTripUpdate(tripUpdate1));
    assertEquals(
      "U | A 10:01 10:01 | B 10:02 10:02 | C 10:03 10:03",
      env.tripData(TRIP_1_ID, SERVICE_DATE).showTimetable()
    );
    assertEquals(
      "S | A 10:00 10:00 | B 10:01 10:01 | C 10:02 10:02",
      env.tripData(TRIP_1_ID, SERVICE_DATE_PLUS).showTimetable()
    );
    assertEquals(List.of("F:Pattern1[U]"), env.raptorData(SERVICE_DATE).summarizePatterns());
    assertEquals(List.of("F:Pattern1[S]"), env.raptorData(SERVICE_DATE_PLUS).summarizePatterns());

    assertSuccess(rt.applyTripUpdates(List.of(tripUpdate1, tripUpdate2)));
    assertEquals(
      "U | A 10:01 10:01 | B 10:02 10:02 | C 10:03 10:03",
      env.tripData(TRIP_1_ID, SERVICE_DATE).showTimetable()
    );
    assertEquals(
      "U | A 10:01 10:01 | B 10:02 10:02 | C 10:03 10:03",
      env.tripData(TRIP_1_ID, SERVICE_DATE_PLUS).showTimetable()
    );
    assertEquals(List.of("F:Pattern1[U]"), env.raptorData(SERVICE_DATE).summarizePatterns());
    assertEquals(List.of("F:Pattern1[U]"), env.raptorData(SERVICE_DATE_PLUS).summarizePatterns());

    // the update is a full dataset, so SERVICE_DATE should revert to scheduled
    assertSuccess(rt.applyTripUpdate(tripUpdate2));
    assertEquals(
      "S | A 10:00 10:00 | B 10:01 10:01 | C 10:02 10:02",
      env.tripData(TRIP_1_ID, SERVICE_DATE).showTimetable()
    );
    assertEquals(
      "U | A 10:01 10:01 | B 10:02 10:02 | C 10:03 10:03",
      env.tripData(TRIP_1_ID, SERVICE_DATE_PLUS).showTimetable()
    );
    assertEquals(List.of("F:Pattern1[S]"), env.raptorData(SERVICE_DATE).summarizePatterns());
    assertEquals(List.of("F:Pattern1[U]"), env.raptorData(SERVICE_DATE_PLUS).summarizePatterns());
  }
}
