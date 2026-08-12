package org.opentripplanner.updater.trip.siri.moduletests.extrajourney;

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
 * An extra journey has no scheduled trip to fall back on, so both its aimed and its real-time times
 * come from the message and must be resolved against the same origin. This pins that on both
 * transitions in {@code Europe/Paris}: the 23-hour day in spring and the 25-hour day in autumn.
 * Resolving either side against calendar midnight instead moves those times by an hour, in opposite
 * directions on the two dates, while the control date is unaffected — which is why the same
 * timetable is asserted on all three dates.
 */
class DstServiceDateTest implements RealtimeTestConstants {

  private static final String ADDED_TRIP_ID = "newJourney";
  private static final String OPERATOR_ID = "operatorId";
  private static final String ROUTE_ID = "routeId";

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

  /** An extra journey keeps the times the message reports on a daylight-saving transition date. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("serviceDates")
  @UnifiedUpdaterOnly(
    "The legacy implementation resolves an extra journey's aimed times against the start of " +
      "service but its real-time times against calendar midnight, so on a transition date it " +
      "reports an hour of delay the feed never sent - one hour early in spring and one hour late " +
      "in autumn."
  )
  void extraJourneyOnDstServiceDate(String name, LocalDate serviceDate) {
    var envBuilder = TransitTestEnvironment.of(serviceDate);
    var stopA = envBuilder.stop(STOP_A_ID);
    var stopB = envBuilder.stop(STOP_B_ID);
    var operator = envBuilder.operator(OPERATOR_ID);
    var route = envBuilder.route(ROUTE_ID, operator);
    // a scheduled trip is what puts the service date in the calendar
    var env = envBuilder
      .addTrip(
        TripInput.of(TRIP_1_ID).withRoute(route).addStop(stopA, "12:00").addStop(stopB, "12:10")
      )
      .build();
    var siri = SiriTestHelper.of(env);

    var updates = siri
      .etBuilder()
      .withEstimatedVehicleJourneyCode(ADDED_TRIP_ID)
      .withIsExtraJourney(true)
      .withOperatorRef(OPERATOR_ID)
      .withLineRef(ROUTE_ID)
      .withRecordedCalls(builder -> builder.call(stopA).departAimedActual("10:01", "10:02"))
      .withEstimatedCalls(builder -> builder.call(stopB).arriveAimedExpected("10:03", "10:04"))
      .buildEstimatedTimetableDeliveries();

    assertSuccess(siri.applyEstimatedTimetable(updates));

    assertEquals(
      "A U | A [R] 10:02 10:02 | B 10:04 10:04",
      env.tripData(ADDED_TRIP_ID).showTimetable()
    );
    assertEquals(
      "S | A 10:01 10:01 | B 10:03 10:03",
      env.tripData(ADDED_TRIP_ID).showScheduledTimetable()
    );
  }
}
