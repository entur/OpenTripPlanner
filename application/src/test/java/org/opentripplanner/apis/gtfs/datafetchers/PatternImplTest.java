package org.opentripplanner.apis.gtfs.datafetchers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.opentripplanner.core.model.id.FeedScopedIdForTestFactory.id;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import graphql.schema.DataFetchingEnvironment;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentripplanner.apis.support.graphql.DataFetchingSupport;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.siri.SiriTestHelper;

/**
 * Verifies that {@link PatternImpl} resolves the headsign and the "original" pattern of a
 * real-time modified pattern from the timetable snapshot, instead of relying on
 * {@code TripPattern.originalTripPattern}.
 */
class PatternImplTest implements RealtimeTestConstants {

  private static final I18NString HEADSIGN = I18NString.of("To Downtown");

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop stopA = envBuilder.stop(STOP_A_ID);
  private final RegularStop stopB = envBuilder.stop(STOP_B_ID);
  private final RegularStop stopD = envBuilder.stop(STOP_D_ID);

  private final TripInput tripInput = TripInput.of(TRIP_1_ID)
    .withWithTripOnServiceDate(TRIP_1_ID)
    .withHeadsign(HEADSIGN)
    .addStop(stopA, "0:01:00", "0:01:01")
    .addStop(stopB, "0:01:10", "0:01:11")
    .addStop(stopD, "0:01:20", "0:01:21");

  private final PatternImpl subject = new PatternImpl();

  @Test
  void originalTripPatternResolvesScheduledPatternForModifiedRealTimePattern() throws Exception {
    var env = buildEnvironmentWithModifiedPattern();
    var transitService = env.transitService();
    var trip = transitService.getTrip(id(TRIP_1_ID));
    var scheduledPattern = transitService.findPattern(trip);
    var realTimePattern = transitService.findPattern(trip, env.defaultServiceDate());

    var original = subject
      .originalTripPattern()
      .get(dataFetchingEnvironment(realTimePattern, transitService));

    assertSame(scheduledPattern, original);
  }

  @Test
  void originalTripPatternIsNullForScheduledPattern() throws Exception {
    var env = buildEnvironmentWithModifiedPattern();
    var transitService = env.transitService();
    var trip = transitService.getTrip(id(TRIP_1_ID));
    var scheduledPattern = transitService.findPattern(trip);

    var original = subject
      .originalTripPattern()
      .get(dataFetchingEnvironment(scheduledPattern, transitService));

    assertNull(original);
  }

  @Test
  void headsignFallsBackToRealTimeTripForModifiedRealTimePattern() throws Exception {
    var env = buildEnvironmentWithModifiedPattern();
    var transitService = env.transitService();
    var trip = transitService.getTrip(id(TRIP_1_ID));
    var realTimePattern = transitService.findPattern(trip, env.defaultServiceDate());

    // The real-time pattern has no scheduled trip times of its own.
    assertNull(realTimePattern.getTripHeadsign());

    var headsign = subject.headsign().get(dataFetchingEnvironment(realTimePattern, transitService));

    assertEquals(HEADSIGN.toString(), headsign);
  }

  @Test
  void headsignIsReadDirectlyFromScheduledPattern() throws Exception {
    var env = buildEnvironmentWithModifiedPattern();
    var transitService = env.transitService();
    var trip = transitService.getTrip(id(TRIP_1_ID));
    var scheduledPattern = transitService.findPattern(trip);

    var headsign = subject
      .headsign()
      .get(dataFetchingEnvironment(scheduledPattern, transitService));

    assertEquals(HEADSIGN.toString(), headsign);
  }

  /**
   * Build a transit network with a single scheduled trip and cancel one of its stops through a
   * SIRI update, which creates a real-time pattern with a modified stop sequence.
   */
  private TransitTestEnvironment buildEnvironmentWithModifiedPattern() {
    var env = envBuilder.addTrip(tripInput).build();
    var siri = SiriTestHelper.of(env);
    var update = siri
      .etBuilder()
      .withDatedVehicleJourneyRef(TRIP_1_ID)
      .withEstimatedCalls(builder ->
        builder
          .call(stopA)
          .departAimedExpected("00:01:01", "00:01:01")
          .call(stopB)
          .withIsCancellation(true)
          .call(stopD)
          .arriveAimedExpected("00:01:20", "00:01:20")
      )
      .buildEstimatedTimetableDeliveries();
    assertSuccess(siri.applyEstimatedTimetable(update));
    return env;
  }

  private static DataFetchingEnvironment dataFetchingEnvironment(
    TripPattern pattern,
    TransitService transitService
  ) {
    return DataFetchingSupport.dataFetchingEnvironment(pattern, Map.of(), transitService);
  }
}
