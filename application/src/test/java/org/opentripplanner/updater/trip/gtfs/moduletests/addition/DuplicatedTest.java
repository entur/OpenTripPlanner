package org.opentripplanner.updater.trip.gtfs.moduletests.addition;

import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.DUPLICATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.updater.spi.UpdateErrorType.NOT_IMPLEMENTED_DIFFERENTIAL_DUPLICATED;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;
import static org.opentripplanner.updater.trip.UpdateIncrementality.DIFFERENTIAL;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.GtfsRtTestHelper;
import org.opentripplanner.updater.trip.RealtimeTestConstants;

class DuplicatedTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop STOP_A = envBuilder.stop(STOP_A_ID);
  private final RegularStop STOP_B = envBuilder.stop(STOP_B_ID);
  private final RegularStop STOP_C = envBuilder.stop(STOP_C_ID);

  private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 6, 22);

  private final TransitTestEnvironment env = envBuilder
    .addStops(STOP_A_ID, STOP_B_ID, STOP_C_ID)
    .addTrip(
      TripInput.of(TRIP_1_ID)
        .withServiceDates(SERVICE_DATE)
        .addStop(STOP_A, "12:00")
        .addStop(STOP_B, "12:10")
        .addStop(STOP_C, "12:20")
    )
    .build();
  private final GtfsRtTestHelper gtfsRt = GtfsRtTestHelper.of(env);

  @Test
  void duplicated() {
    var tripUpdate = gtfsRt
      .tripUpdate(TRIP_1_ID, DUPLICATED)
      .withStartDate(SERVICE_DATE)
      .withStartTime(LocalTime.of(13, 0))
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(tripUpdate));

    var duplicatedId = TRIP_1_ID + ":duplicated:13:00";
    assertEquals("", env.tripData(duplicatedId).showTimetable());
  }

  @Test
  void invalidIncrementality() {
    var tripUpdate = gtfsRt
      .tripUpdate(ADDED_TRIP_ID, DUPLICATED)
      .withStartDate(SERVICE_DATE)
      .withStartTime(LocalTime.of(13, 0))
      .build();

    assertFailure(NOT_IMPLEMENTED_DIFFERENTIAL_DUPLICATED, gtfsRt.applyTripUpdate(tripUpdate, DIFFERENTIAL));
  }

}
