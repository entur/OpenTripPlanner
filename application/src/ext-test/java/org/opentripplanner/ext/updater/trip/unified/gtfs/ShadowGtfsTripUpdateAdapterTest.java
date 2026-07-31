package org.opentripplanner.ext.updater.trip.unified.gtfs;

import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.DUPLICATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;
import static org.opentripplanner.updater.trip.UpdateIncrementality.FULL_DATASET;

import com.google.transit.realtime.GtfsRealtime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.framework.Deduplicator;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.UpdateIncrementality;
import org.opentripplanner.updater.trip.gtfs.GtfsRealTimeTripUpdateAdapter;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;

/**
 * Turning shadow comparison on must not change what the primary (legacy) adapter writes to the
 * timetable — it is a diagnostic mode, not a second behaviour.
 * <p>
 * The shadow adapter drives the primary handler one trip at a time so that both paths observe the
 * same buffer state for each trip. That makes the incrementality it hands the primary load-bearing:
 * the primary derives real behaviour from it (which trips it accepts, and which branch it takes for
 * a cancellation), so the caller's value has to be passed through unchanged, while the batch-level
 * buffer clear still has to happen exactly once for the whole message.
 */
class ShadowGtfsTripUpdateAdapterTest implements RealtimeTestConstants {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 6, 22);
  private static final LocalTime DUPLICATED_START = LocalTime.of(13, 30);
  private static final String DUPLICATED_ID =
    TRIP_1_ID + ":duplicated:" + SERVICE_DATE + "T" + DUPLICATED_START;

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of(SERVICE_DATE);
  private final RegularStop stopA = envBuilder.stop(STOP_A_ID);
  private final RegularStop stopB = envBuilder.stop(STOP_B_ID);
  private final RegularStop stopC = envBuilder.stop(STOP_C_ID);

  private final TransitTestEnvironment env = envBuilder
    .addTrip(
      TripInput.of(TRIP_1_ID)
        .withServiceDates(SERVICE_DATE, SERVICE_DATE.plusDays(2))
        .addStop(stopA, "12:00")
        .addStop(stopB, "12:10")
        .addStop(stopC, "12:20")
    )
    .addTrip(
      TripInput.of(TRIP_2_ID)
        .withServiceDates(SERVICE_DATE, SERVICE_DATE.plusDays(2))
        .addStop(stopA, "14:00")
        .addStop(stopB, "14:10")
        .addStop(stopC, "14:20")
    )
    .build();

  private final GtfsRtTestHelper gtfsRt = GtfsRtTestHelper.of(env);

  /**
   * DUPLICATED is rejected outright on a differential feed
   * ({@code NOT_IMPLEMENTED_DIFFERENTIAL_DUPLICATED}), so substituting DIFFERENTIAL for the
   * caller's FULL_DATASET would silently drop every duplicated trip while shadow mode is on.
   */
  @Test
  void duplicatedTripIsAppliedInShadowMode() {
    var tripUpdate = gtfsRt
      .tripUpdate(TRIP_1_ID, DUPLICATED)
      .withStartDate(SERVICE_DATE)
      .withStartTime(DUPLICATED_START)
      .build();

    assertSuccess(applyThroughShadowAdapter(List.of(tripUpdate), FULL_DATASET));

    assertEquals(
      "A U | A 13:30 13:30 | B 13:40 13:40 | C 13:50 13:50",
      env.tripData(DUPLICATED_ID, SERVICE_DATE).showTimetable()
    );
  }

  /**
   * The primary handler clears the buffer whenever it is told the update is a full dataset, and the
   * shadow adapter invokes it once per trip. Without collapsing those repeats, trip 2 would wipe
   * the update applied for trip 1.
   */
  @Test
  void everyTripOfAFullDatasetBatchSurvives() {
    var first = gtfsRt
      .tripUpdateScheduled(TRIP_1_ID, SERVICE_DATE)
      .addDelayedStopTime(1, 60)
      .build();
    var second = gtfsRt
      .tripUpdateScheduled(TRIP_2_ID, SERVICE_DATE)
      .addDelayedStopTime(1, 120)
      .build();

    assertSuccess(applyThroughShadowAdapter(List.of(first, second), FULL_DATASET));

    // The delay on B (and its forward propagation to C) is what proves the update for this trip was
    // not wiped when the primary was invoked again for the next trip of the same batch.
    assertEquals(
      "U | A [ND] 12:00 12:00 | B 12:11 12:11 | C 12:21 12:21",
      env.tripData(TRIP_1_ID, SERVICE_DATE).showTimetable()
    );
    assertEquals(
      "U | A [ND] 14:00 14:00 | B 14:12 14:12 | C 14:22 14:22",
      env.tripData(TRIP_2_ID, SERVICE_DATE).showTimetable()
    );
  }

  /**
   * Apply the updates through the shadow adapter, i.e. with {@code shadowComparison: true}. No
   * report directory is configured, so nothing is written to disk.
   */
  private UpdateResult applyThroughShadowAdapter(
    List<GtfsRealtime.TripUpdate> updates,
    UpdateIncrementality incrementality
  ) {
    var deduplicator = new Deduplicator();
    var shadowAdapter = new ShadowGtfsTripUpdateAdapter(
      new GtfsRealTimeTripUpdateAdapter(env.transitRepository(), deduplicator, () -> SERVICE_DATE),
      env.transitRepository(),
      deduplicator,
      ForwardsDelayPropagationType.DEFAULT,
      BackwardsDelayPropagationType.REQUIRED_NO_DATA,
      false,
      env.feedId(),
      () -> SERVICE_DATE,
      null
    );

    var resultRef = new AtomicReference<UpdateResult>();
    try {
      env
        .updateManager()
        .submit(ctx ->
          resultRef.set(
            shadowAdapter
              .forUpdate(ctx.repository(env.timetableHandle()))
              .applyTripUpdates(
                null,
                ForwardsDelayPropagationType.DEFAULT,
                BackwardsDelayPropagationType.REQUIRED_NO_DATA,
                incrementality,
                updates,
                env.feedId()
              )
          )
        )
        .get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return resultRef.get();
  }
}
