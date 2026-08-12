package org.opentripplanner.updater.trip.siri.moduletests.extracall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.UnifiedUpdaterOnly;
import org.opentripplanner.updater.trip.siri.SiriTestHelper;

/**
 * A SIRI call carries absolute times, so applying one means subtracting the start of the service
 * day. That origin is noon minus twelve hours, <em>not</em> calendar midnight — the two differ by
 * exactly the offset shift on a service date that contains a daylight-saving transition (see
 * {@code ServiceDateUtils.asStartOfService}).
 * <p>
 * An extra call rebuilds a scheduled trip around a stop the timetable does not have, so the times
 * the message reports land on a trip whose scheduled times already sit on that origin and must be
 * resolved against the same one. This pins that on both transitions in {@code Europe/Paris}: the
 * 23-hour day in spring and the 25-hour day in autumn. Resolving against calendar midnight instead
 * moves every real-time time on the trip by an hour, in opposite directions on the two dates, while
 * the control date is unaffected — which is why the same timetable is asserted on all three dates.
 */
class DstServiceDateTest implements RealtimeTestConstants {

  private static final String ROUTE_ID = "route-id";

  /** Clocks jump 02:00 → 03:00, so the calendar day is 23 hours long. */
  private static final LocalDate SPRING_FORWARD = LocalDate.of(2024, 3, 31);

  /** Clocks fall back 03:00 → 02:00, so the calendar day is 25 hours long. */
  private static final LocalDate FALL_BACK = LocalDate.of(2024, 10, 27);

  /** Control: no transition, so calendar midnight and start of service coincide. */
  private static final LocalDate NO_TRANSITION = LocalDate.of(2024, 5, 7);

  private static Stream<Arguments> serviceDates() {
    return Stream.of(
      Arguments.of("spring forward (23-hour day)", SPRING_FORWARD),
      Arguments.of("fall back (25-hour day)", FALL_BACK),
      Arguments.of("no transition", NO_TRANSITION)
    );
  }

  /** An extra call keeps the times the message reports on a daylight-saving transition date. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("serviceDates")
  @UnifiedUpdaterOnly(
    "The legacy implementation resolves the real-time times of a trip that gains an extra call " +
      "against calendar midnight while its scheduled times sit on the start of service, so on a " +
      "transition date every call of the trip moves by an hour - one hour early in spring and one " +
      "hour late in autumn."
  )
  void extraCallOnDstServiceDate(String name, LocalDate serviceDate) {
    var envBuilder = TransitTestEnvironment.of(serviceDate);
    var stopA = envBuilder.stopAtStation(STOP_A_ID, "A");
    var stopB = envBuilder.stopAtStation(STOP_B_ID, "B");
    var stopC = envBuilder.stopAtStation(STOP_C_ID, "C");
    var route = envBuilder.route(ROUTE_ID);
    var env = envBuilder
      .addTrip(
        TripInput.of(TRIP_1_ID)
          .withWithTripOnServiceDate(TRIP_1_ID)
          .withRoute(route)
          .addStop(stopA, "10:00", "10:01")
          .addStop(stopB, "10:20", "10:21")
      )
      .build();
    var siri = SiriTestHelper.of(env);

    var updates = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withLineRef(ROUTE_ID)
      .withRecordedCalls(builder -> builder.call(stopA).departAimedActual("10:01", "10:05"))
      .withEstimatedCalls(builder ->
        builder
          .call(stopC)
          .withIsExtraCall(true)
          .arriveAimedExpected("10:08", "10:10")
          .departAimedExpected("10:09", "10:15")
          .call(stopB)
          .arriveAimedExpected("10:20", "10:33")
      )
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(updates));

    assertEquals(
      "P U | A [R] 10:05 10:05 | C [EC] 10:10 10:15 | B 10:33 10:33",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }
}
