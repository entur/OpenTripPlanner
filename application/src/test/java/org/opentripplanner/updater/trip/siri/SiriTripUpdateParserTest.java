package org.opentripplanner.updater.trip.siri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.LocalTimeParser;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.updater.trip.TripUpdateParserContext;
import org.opentripplanner.updater.trip.gtfs.ForwardsDelayPropagationType;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.TripUpdateType;

/**
 * Tests for SiriTripUpdateParser.
 */
class SiriTripUpdateParserTest {

  private static final String FEED_ID = "TEST";
  private static final LocalDate TEST_DATE = LocalDate.of(2024, 1, 15);
  private static final ZoneId TIME_ZONE = ZoneId.of("Europe/Oslo");

  private SiriTripUpdateParser parser;
  private TripUpdateParserContext context;
  private LocalTimeParser timeParser;

  @BeforeEach
  void setUp() {
    parser = new SiriTripUpdateParser();
    context = new TripUpdateParserContext(FEED_ID, TIME_ZONE, () -> TEST_DATE);
    timeParser = new LocalTimeParser(TIME_ZONE, TEST_DATE);
  }

  @Test
  void parseUpdateExistingTrip() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("trip1")
      .withLineRef("route1")
      .withEstimatedCalls(calls ->
        calls.call("stop1").withAimedDepartureTime("08:00").withExpectedDepartureTime("08:05")
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey, context);

    assertTrue(result.isSuccess());
    var parsed = result.successValue();

    assertEquals(TripUpdateType.UPDATE_EXISTING, parsed.updateType());
    assertEquals(new FeedScopedId(FEED_ID, "trip1"), parsed.tripReference().tripId());
    assertEquals(TEST_DATE, parsed.serviceDate());
    assertEquals(1, parsed.stopTimeUpdates().size());

    var stopUpdate = parsed.stopTimeUpdates().get(0);
    assertEquals("stop1", stopUpdate.stopReference().stopPointRef());
    assertNotNull(stopUpdate.departureUpdate());
    assertNotNull(stopUpdate.departureUpdate().absoluteTimeSecondsSinceMidnight());
  }

  @Test
  void parseCancelledTrip() {
    var journey = new SiriEtBuilder(timeParser)
      .withFramedVehicleJourneyRef(ref ->
        ref.withDatedVehicleJourneyRef("trip1").withDataFrameRef(TEST_DATE.toString())
      )
      .withCancellation(true)
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey, context);

    assertTrue(result.isSuccess());
    var parsed = result.successValue();

    assertEquals(TripUpdateType.CANCEL_TRIP, parsed.updateType());
    assertTrue(parsed.isCancellation());
    assertTrue(parsed.stopTimeUpdates().isEmpty());
  }

  @Test
  void parseExtraJourneyAsNewTrip() {
    // Parser no longer checks if trip exists - just parses the extra journey
    var journey = new SiriEtBuilder(timeParser)
      .withIsExtraJourney(true)
      .withEstimatedVehicleJourneyCode("NSB:ServiceJourney:newtrip1-2024-01-15")
      .withOperatorRef("operator1")
      .withLineRef("route1")
      .withEstimatedCalls(calls ->
        calls
          .call("stop1")
          .withAimedDepartureTime("08:00")
          .withExpectedDepartureTime("08:00")
          .next()
          .call("stop2")
          .withAimedArrivalTime("08:30")
          .withExpectedArrivalTime("08:30")
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey, context);

    assertTrue(result.isSuccess());
    var parsed = result.successValue();

    assertEquals(TripUpdateType.ADD_NEW_TRIP, parsed.updateType());
    assertTrue(parsed.isNewTrip());
    assertNotNull(parsed.tripCreationInfo());
    assertEquals(
      new FeedScopedId(FEED_ID, "NSB:ServiceJourney:newtrip1-2024-01-15"),
      parsed.tripCreationInfo().tripId()
    );
    assertEquals(new FeedScopedId(FEED_ID, "route1"), parsed.tripCreationInfo().routeId());
    assertEquals(new FeedScopedId(FEED_ID, "operator1"), parsed.tripCreationInfo().operatorId());
    assertEquals(2, parsed.stopTimeUpdates().size());
  }

  @Test
  void parseExtraCalls() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("trip1")
      .withEstimatedCalls(calls ->
        calls
          .call("stop1")
          .withAimedDepartureTime("08:00")
          .withExpectedDepartureTime("08:00")
          .next()
          .call("stop_extra")
          .withIsExtraCall(true)
          .withExpectedArrivalTime("08:15")
          .withExpectedDepartureTime("08:16")
          .next()
          .call("stop2")
          .withAimedArrivalTime("08:30")
          .withExpectedArrivalTime("08:30")
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey, context);

    assertTrue(result.isSuccess());
    var parsed = result.successValue();

    assertEquals(TripUpdateType.ADD_EXTRA_CALLS, parsed.updateType());
    assertEquals(3, parsed.stopTimeUpdates().size());

    var extraCallUpdate = parsed.stopTimeUpdates().get(1);
    assertEquals("stop_extra", extraCallUpdate.stopReference().stopPointRef());
    assertTrue(extraCallUpdate.isExtraCall());
    assertEquals(ParsedStopTimeUpdate.StopUpdateStatus.ADDED, extraCallUpdate.status());
  }

  @Test
  void parseCancelledStop() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("trip1")
      .withEstimatedCalls(calls ->
        calls
          .call("stop1")
          .withAimedDepartureTime("08:00")
          .withExpectedDepartureTime("08:00")
          .next()
          .call("stop2")
          .withCancellation(true)
          .withAimedArrivalTime("08:30")
          .next()
          .call("stop3")
          .withAimedArrivalTime("09:00")
          .withExpectedArrivalTime("09:00")
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey, context);

    assertTrue(result.isSuccess());
    var parsed = result.successValue();

    assertEquals(3, parsed.stopTimeUpdates().size());
    var cancelledStop = parsed.stopTimeUpdates().get(1);
    assertEquals(ParsedStopTimeUpdate.StopUpdateStatus.CANCELLED, cancelledStop.status());
  }

  @Test
  void parseWithRecordedCalls() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("trip1")
      .withRecordedCalls(calls ->
        calls.call("stop1").withAimedDepartureTime("08:00").withActualDepartureTime("08:01")
      )
      .withEstimatedCalls(calls ->
        calls.call("stop2").withAimedArrivalTime("08:30").withExpectedArrivalTime("08:32")
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey, context);

    assertTrue(result.isSuccess());
    var parsed = result.successValue();

    assertEquals(2, parsed.stopTimeUpdates().size());

    // First stop should be marked as recorded (actual time available)
    var recordedStop = parsed.stopTimeUpdates().get(0);
    assertTrue(recordedStop.recorded());
    assertNotNull(recordedStop.departureUpdate());

    // Second stop should not be marked as recorded
    var estimatedStop = parsed.stopTimeUpdates().get(1);
    assertFalse(estimatedStop.recorded());
  }

  @Test
  void parsePredictionInaccurate() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("trip1")
      .withEstimatedCalls(calls ->
        calls
          .call("stop1")
          .withAimedDepartureTime("08:00")
          .withExpectedDepartureTime("08:00")
          .withPredictionInaccurate(true)
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey, context);

    assertTrue(result.isSuccess());
    var parsed = result.successValue();

    var stopUpdate = parsed.stopTimeUpdates().get(0);
    assertTrue(stopUpdate.predictionInaccurate());
  }

  @Test
  void parseDestinationDisplay() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("trip1")
      .withEstimatedCalls(calls ->
        calls
          .call("stop1")
          .withAimedDepartureTime("08:00")
          .withExpectedDepartureTime("08:00")
          .withDestinationDisplay("Downtown")
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey, context);

    assertTrue(result.isSuccess());
    var parsed = result.successValue();

    var stopUpdate = parsed.stopTimeUpdates().get(0);
    assertNotNull(stopUpdate.stopHeadsign());
    assertEquals("Downtown", stopUpdate.stopHeadsign().toString());
  }

  @Test
  void parseNotMonitored() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("trip1")
      .withMonitored(false)
      .withEstimatedCalls(calls ->
        calls.call("stop1").withAimedDepartureTime("08:00").withExpectedDepartureTime("08:00")
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey, context);

    assertTrue(result.isFailure());
  }

  @Test
  void parseNotMonitoredButCancelled() {
    // Cancelled journeys can be not monitored
    var journey = new SiriEtBuilder(timeParser)
      .withFramedVehicleJourneyRef(ref ->
        ref.withDatedVehicleJourneyRef("trip1").withDataFrameRef(TEST_DATE.toString())
      )
      .withMonitored(false)
      .withCancellation(true)
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey, context);

    assertTrue(result.isSuccess());
    assertTrue(result.successValue().isCancellation());
  }

  @Test
  void parseEmptyStopPointRef() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("trip1")
      .withEstimatedCalls(calls -> calls.call("").withAimedDepartureTime("08:00"))
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey, context);

    assertTrue(result.isFailure());
  }

  @Test
  void parseWithOccupancy() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("trip1")
      .withEstimatedCalls(calls ->
        calls
          .call("stop1")
          .withAimedDepartureTime("08:00")
          .withExpectedDepartureTime("08:00")
          .withOccupancy(uk.org.siri.siri21.OccupancyEnumeration.SEATS_AVAILABLE)
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey, context);

    assertTrue(result.isSuccess());
    var parsed = result.successValue();

    var stopUpdate = parsed.stopTimeUpdates().get(0);
    assertNotNull(stopUpdate.occupancy());
  }

  @Test
  void parseAbsoluteTimes() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("trip1")
      .withEstimatedCalls(calls ->
        calls
          .call("stop1")
          .withAimedArrivalTime("08:00")
          .withExpectedArrivalTime("08:05")
          .withAimedDepartureTime("08:01")
          .withExpectedDepartureTime("08:06")
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey, context);

    assertTrue(result.isSuccess());
    var parsed = result.successValue();

    var stopUpdate = parsed.stopTimeUpdates().get(0);

    // SIRI provides absolute times, not delays
    assertNotNull(stopUpdate.arrivalUpdate());
    assertNotNull(stopUpdate.arrivalUpdate().absoluteTimeSecondsSinceMidnight());
    assertNotNull(stopUpdate.arrivalUpdate().scheduledTimeSecondsSinceMidnight());
    assertNull(stopUpdate.arrivalUpdate().delaySeconds());

    assertNotNull(stopUpdate.departureUpdate());
    assertNotNull(stopUpdate.departureUpdate().absoluteTimeSecondsSinceMidnight());
    assertNotNull(stopUpdate.departureUpdate().scheduledTimeSecondsSinceMidnight());
    assertNull(stopUpdate.departureUpdate().delaySeconds());
  }

  @Test
  void parseSiriDefaultOptions() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("trip1")
      .withEstimatedCalls(calls ->
        calls.call("stop1").withAimedDepartureTime("08:00").withExpectedDepartureTime("08:00")
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey, context);

    assertTrue(result.isSuccess());
    var parsed = result.successValue();

    // SIRI doesn't need delay propagation since it provides explicit times
    assertEquals(ForwardsDelayPropagationType.NONE, parsed.options().forwardsPropagation());
  }

  @Test
  void parseWithFramedVehicleJourneyRef() {
    var journey = new SiriEtBuilder(timeParser)
      .withFramedVehicleJourneyRef(ref ->
        ref.withDatedVehicleJourneyRef("trip1").withDataFrameRef("2024-01-15")
      )
      .withEstimatedCalls(calls ->
        calls.call("stop1").withAimedDepartureTime("08:00").withExpectedDepartureTime("08:00")
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey, context);

    assertTrue(result.isSuccess());
    var parsed = result.successValue();

    assertEquals(new FeedScopedId(FEED_ID, "trip1"), parsed.tripReference().tripId());
    assertEquals(TEST_DATE, parsed.serviceDate());
  }

  @Test
  void parseMultipleStops() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("trip1")
      .withEstimatedCalls(calls ->
        calls
          .call("stop1")
          .withAimedDepartureTime("08:00")
          .withExpectedDepartureTime("08:02")
          .next()
          .call("stop2")
          .withAimedArrivalTime("08:15")
          .withExpectedArrivalTime("08:18")
          .withAimedDepartureTime("08:16")
          .withExpectedDepartureTime("08:19")
          .next()
          .call("stop3")
          .withAimedArrivalTime("08:30")
          .withExpectedArrivalTime("08:35")
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey, context);

    assertTrue(result.isSuccess());
    var parsed = result.successValue();

    assertEquals(3, parsed.stopTimeUpdates().size());
    assertEquals("stop1", parsed.stopTimeUpdates().get(0).stopReference().stopPointRef());
    assertEquals("stop2", parsed.stopTimeUpdates().get(1).stopReference().stopPointRef());
    assertEquals("stop3", parsed.stopTimeUpdates().get(2).stopReference().stopPointRef());
  }

  @Test
  void parseWithDataSource() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("trip1")
      .withEstimatedCalls(calls ->
        calls.call("stop1").withAimedDepartureTime("08:00").withExpectedDepartureTime("08:00")
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey, context);

    assertTrue(result.isSuccess());
    var parsed = result.successValue();

    assertEquals("DATASOURCE", parsed.dataSource());
  }
}
