package org.opentripplanner.updater.trip.siri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.LocalTimeParser;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.updater.trip.gtfs.ForwardsDelayPropagationType;
import org.opentripplanner.updater.trip.model.ParsedAddNewTrip;
import org.opentripplanner.updater.trip.model.ParsedCancelTrip;
import org.opentripplanner.updater.trip.model.ParsedModifyTrip;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedUpdateExisting;
import org.opentripplanner.updater.trip.model.StopResolutionStrategy;
import org.opentripplanner.updater.trip.model.TimeUpdate;

/**
 * Tests for SiriTripUpdateParser.
 */
class SiriTripUpdateParserTest {

  private static final String FEED_ID = "TEST";
  private static final LocalDate TEST_DATE = LocalDate.of(2024, 1, 15);
  private static final ZoneId TIME_ZONE = ZoneId.of("Europe/Oslo");

  private SiriTripUpdateParser parser;
  private LocalTimeParser timeParser;

  @BeforeEach
  void setUp() {
    parser = new SiriTripUpdateParser(FEED_ID, TIME_ZONE);
    timeParser = new LocalTimeParser(TIME_ZONE, TEST_DATE);
  }

  @Test
  void parseUpdateExistingTripWithDatedVehicleJourneyRef() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("dated-trip1")
      .withLineRef("route1")
      .withEstimatedCalls(calls ->
        calls.call("stop1").withAimedDepartureTime("08:00").withExpectedDepartureTime("08:05")
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedUpdateExisting.class, result.successValue());

    assertNull(parsed.tripReference().tripId());
    assertEquals(
      new FeedScopedId(FEED_ID, "dated-trip1"),
      parsed.tripReference().tripOnServiceDateId()
    );
    assertNull(parsed.serviceDate());
    assertEquals(1, parsed.stopTimeUpdates().size());

    var stopUpdate = parsed.stopTimeUpdates().get(0);
    assertEquals(new FeedScopedId(FEED_ID, "stop1"), stopUpdate.stopReference().stopId());
    assertEquals(
      StopResolutionStrategy.SCHEDULED_STOP_POINT_FIRST,
      stopUpdate.stopReference().resolutionStrategy()
    );
    assertNotNull(stopUpdate.departureUpdate());
    assertTrue(
      stopUpdate.departureUpdate() instanceof
        org.opentripplanner.updater.trip.model.DeferredTimeUpdate,
      "Time update should be DeferredTimeUpdate when service date is null"
    );
  }

  @Test
  void parseCancelledTrip() {
    var journey = new SiriEtBuilder(timeParser)
      .withFramedVehicleJourneyRef(ref ->
        ref.withDatedVehicleJourneyRef("trip1").withDataFrameRef(TEST_DATE.toString())
      )
      .withCancellation(true)
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey);

    assertTrue(result.isSuccess());
    assertInstanceOf(ParsedCancelTrip.class, result.successValue());
  }

  @Test
  void parseExtraJourneyAsNewTrip() {
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

    var result = parser.parse(journey);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedAddNewTrip.class, result.successValue());

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

    var result = parser.parse(journey);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedModifyTrip.class, result.successValue());

    assertEquals(3, parsed.stopTimeUpdates().size());

    var extraCallUpdate = parsed.stopTimeUpdates().get(1);
    assertEquals(new FeedScopedId(FEED_ID, "stop_extra"), extraCallUpdate.stopReference().stopId());
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

    var result = parser.parse(journey);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedUpdateExisting.class, result.successValue());

    assertEquals(3, parsed.stopTimeUpdates().size());
    var cancelledStop = parsed.stopTimeUpdates().get(1);
    assertEquals(ParsedStopTimeUpdate.StopUpdateStatus.CANCELLED, cancelledStop.status());
  }

  @Test
  void parseWithRecordedCalls() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("trip1")
      .withRecordedCalls(calls -> calls.call("stop1").departAimedActual("08:00", "08:01"))
      .withEstimatedCalls(calls -> calls.call("stop2").arriveAimedExpected("08:30", "08:32"))
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedUpdateExisting.class, result.successValue());

    assertEquals(2, parsed.stopTimeUpdates().size());

    var recordedStop = parsed.stopTimeUpdates().get(0);
    assertTrue(recordedStop.hasArrived());
    assertTrue(recordedStop.hasDeparted());
    assertNotNull(recordedStop.departureUpdate());

    var estimatedStop = parsed.stopTimeUpdates().get(1);
    assertFalse(estimatedStop.hasArrived());
    assertFalse(estimatedStop.hasDeparted());
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

    var result = parser.parse(journey);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedUpdateExisting.class, result.successValue());

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

    var result = parser.parse(journey);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedUpdateExisting.class, result.successValue());

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

    var result = parser.parse(journey);

    assertTrue(result.isFailure());
  }

  @Test
  void parseNotMonitoredButCancelled() {
    var journey = new SiriEtBuilder(timeParser)
      .withFramedVehicleJourneyRef(ref ->
        ref.withDatedVehicleJourneyRef("trip1").withDataFrameRef(TEST_DATE.toString())
      )
      .withMonitored(false)
      .withCancellation(true)
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey);

    assertTrue(result.isSuccess());
    assertInstanceOf(ParsedCancelTrip.class, result.successValue());
  }

  @Test
  void parseEmptyStopPointRef() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("trip1")
      .withEstimatedCalls(calls -> calls.call("").withAimedDepartureTime("08:00"))
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey);

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

    var result = parser.parse(journey);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedUpdateExisting.class, result.successValue());

    var stopUpdate = parsed.stopTimeUpdates().get(0);
    assertNotNull(stopUpdate.occupancy());
  }

  @Test
  void parseAbsoluteTimes() {
    var journey = new SiriEtBuilder(timeParser)
      .withFramedVehicleJourneyRef(ref ->
        ref.withDatedVehicleJourneyRef("trip1").withDataFrameRef(TEST_DATE.toString())
      )
      .withEstimatedCalls(calls ->
        calls
          .call("stop1")
          .withAimedArrivalTime("08:00")
          .withExpectedArrivalTime("08:05")
          .withAimedDepartureTime("08:01")
          .withExpectedDepartureTime("08:06")
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedUpdateExisting.class, result.successValue());

    var stopUpdate = parsed.stopTimeUpdates().get(0);

    assertNotNull(stopUpdate.arrivalUpdate());
    assertNotNull(asTimeUpdate(stopUpdate.arrivalUpdate()).absoluteTimeSecondsSinceMidnight());
    assertNotNull(asTimeUpdate(stopUpdate.arrivalUpdate()).scheduledTimeSecondsSinceMidnight());
    assertNull(asTimeUpdate(stopUpdate.arrivalUpdate()).delaySeconds());

    assertNotNull(stopUpdate.departureUpdate());
    assertNotNull(asTimeUpdate(stopUpdate.departureUpdate()).absoluteTimeSecondsSinceMidnight());
    assertNotNull(asTimeUpdate(stopUpdate.departureUpdate()).scheduledTimeSecondsSinceMidnight());
    assertNull(asTimeUpdate(stopUpdate.departureUpdate()).delaySeconds());
  }

  @Test
  void parseSiriDefaultOptions() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("trip1")
      .withEstimatedCalls(calls ->
        calls.call("stop1").withAimedDepartureTime("08:00").withExpectedDepartureTime("08:00")
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedUpdateExisting.class, result.successValue());

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

    var result = parser.parse(journey);

    assertTrue(result.isSuccess());
    var parsed = result.successValue();

    assertEquals(new FeedScopedId(FEED_ID, "trip1"), parsed.tripReference().tripId());
    assertNull(parsed.tripReference().tripOnServiceDateId());
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

    var result = parser.parse(journey);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedUpdateExisting.class, result.successValue());

    assertEquals(3, parsed.stopTimeUpdates().size());
    assertEquals(
      new FeedScopedId(FEED_ID, "stop1"),
      parsed.stopTimeUpdates().get(0).stopReference().stopId()
    );
    assertEquals(
      new FeedScopedId(FEED_ID, "stop2"),
      parsed.stopTimeUpdates().get(1).stopReference().stopId()
    );
    assertEquals(
      new FeedScopedId(FEED_ID, "stop3"),
      parsed.stopTimeUpdates().get(2).stopReference().stopId()
    );
  }

  @Test
  void parseWithDataSource() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("trip1")
      .withEstimatedCalls(calls ->
        calls.call("stop1").withAimedDepartureTime("08:00").withExpectedDepartureTime("08:00")
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey);

    assertTrue(result.isSuccess());
    var parsed = result.successValue();

    assertEquals("DATASOURCE", parsed.dataSource());
  }

  @Test
  void parseFirstStopMissingArrival_UsesTimeResolver() {
    var journey = new SiriEtBuilder(timeParser)
      .withFramedVehicleJourneyRef(ref ->
        ref.withDatedVehicleJourneyRef("trip1").withDataFrameRef(TEST_DATE.toString())
      )
      .withEstimatedCalls(calls ->
        calls
          .call("stop1")
          .withAimedDepartureTime("08:00")
          .withExpectedDepartureTime("08:05")
          .next()
          .call("stop2")
          .withAimedArrivalTime("08:30")
          .withExpectedArrivalTime("08:32")
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedUpdateExisting.class, result.successValue());

    assertEquals(2, parsed.stopTimeUpdates().size());

    var firstStop = parsed.stopTimeUpdates().get(0);
    assertNotNull(firstStop.arrivalUpdate(), "First stop should have arrival update via fallback");
    assertNotNull(firstStop.departureUpdate());

    assertEquals(
      asTimeUpdate(firstStop.departureUpdate()).absoluteTimeSecondsSinceMidnight(),
      asTimeUpdate(firstStop.arrivalUpdate()).absoluteTimeSecondsSinceMidnight(),
      "First stop arrival should fallback to departure time"
    );
  }

  @Test
  void parseLastStopMissingDeparture_UsesTimeResolver() {
    var journey = new SiriEtBuilder(timeParser)
      .withFramedVehicleJourneyRef(ref ->
        ref.withDatedVehicleJourneyRef("trip1").withDataFrameRef(TEST_DATE.toString())
      )
      .withEstimatedCalls(calls ->
        calls
          .call("stop1")
          .withAimedDepartureTime("08:00")
          .withExpectedDepartureTime("08:05")
          .next()
          .call("stop2")
          .withAimedArrivalTime("08:30")
          .withExpectedArrivalTime("08:32")
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedUpdateExisting.class, result.successValue());

    assertEquals(2, parsed.stopTimeUpdates().size());

    var lastStop = parsed.stopTimeUpdates().get(1);
    assertNotNull(lastStop.arrivalUpdate());
    assertNotNull(
      lastStop.departureUpdate(),
      "Last stop should have departure update via fallback"
    );

    assertEquals(
      asTimeUpdate(lastStop.arrivalUpdate()).absoluteTimeSecondsSinceMidnight(),
      asTimeUpdate(lastStop.departureUpdate()).absoluteTimeSecondsSinceMidnight(),
      "Last stop departure should fallback to arrival time"
    );
  }

  @Test
  void parseMiddleStopMissingTimes_NoFallback() {
    var journey = new SiriEtBuilder(timeParser)
      .withFramedVehicleJourneyRef(ref ->
        ref.withDatedVehicleJourneyRef("trip1").withDataFrameRef(TEST_DATE.toString())
      )
      .withEstimatedCalls(calls ->
        calls
          .call("stop1")
          .withAimedDepartureTime("08:00")
          .withExpectedDepartureTime("08:05")
          .next()
          .call("stop2")
          .withAimedArrivalTime("08:15")
          .withExpectedArrivalTime("08:17")
          .next()
          .call("stop3")
          .withAimedArrivalTime("08:30")
          .withExpectedArrivalTime("08:32")
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedUpdateExisting.class, result.successValue());

    assertEquals(3, parsed.stopTimeUpdates().size());

    var middleStop = parsed.stopTimeUpdates().get(1);
    assertNotNull(middleStop.arrivalUpdate());
    assertNull(
      middleStop.departureUpdate(),
      "Middle stop should not fallback departure to arrival"
    );
  }

  @Test
  void parseSingleStopTrip_BothFallbacksApply() {
    var journey = new SiriEtBuilder(timeParser)
      .withFramedVehicleJourneyRef(ref ->
        ref.withDatedVehicleJourneyRef("trip1").withDataFrameRef(TEST_DATE.toString())
      )
      .withEstimatedCalls(calls ->
        calls.call("stop1").withAimedDepartureTime("08:00").withExpectedDepartureTime("08:05")
      )
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedUpdateExisting.class, result.successValue());

    assertEquals(1, parsed.stopTimeUpdates().size());

    var singleStop = parsed.stopTimeUpdates().get(0);
    assertNotNull(singleStop.arrivalUpdate(), "Single stop should have arrival via fallback");
    assertNotNull(singleStop.departureUpdate());

    assertEquals(
      asTimeUpdate(singleStop.departureUpdate()).absoluteTimeSecondsSinceMidnight(),
      asTimeUpdate(singleStop.arrivalUpdate()).absoluteTimeSecondsSinceMidnight(),
      "Single stop arrival should fallback to departure time"
    );
  }

  @Test
  void parseActualTimePrecedence_UsesTimeResolver() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("trip1")
      .withRecordedCalls(calls -> calls.call("stop1").departAimedActual("08:00", "08:01"))
      .withEstimatedCalls(calls -> calls.call("stop2").arriveAimedExpected("08:30", "08:35"))
      .buildEstimatedVehicleJourney();

    var result = parser.parse(journey);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedUpdateExisting.class, result.successValue());

    assertEquals(2, parsed.stopTimeUpdates().size());

    var firstStop = parsed.stopTimeUpdates().get(0);
    var lastStop = parsed.stopTimeUpdates().get(1);

    assertTrue(firstStop.hasArrived(), "First stop should be marked as arrived (recorded call)");
    assertTrue(firstStop.hasDeparted(), "First stop should be marked as departed (recorded call)");
    assertNotNull(firstStop.departureUpdate());

    assertFalse(
      lastStop.hasArrived(),
      "Last stop should not be marked as arrived (only has expected time)"
    );
    assertNotNull(lastStop.arrivalUpdate());
  }

  private static TimeUpdate asTimeUpdate(ParsedTimeUpdate parsedTimeUpdate) {
    return (TimeUpdate) parsedTimeUpdate;
  }

  @Test
  void parseExtraJourneyWithReplacementTrip() {
    var journey = new SiriEtBuilder(timeParser)
      .withIsExtraJourney(true)
      .withEstimatedVehicleJourneyCode("NSB:ServiceJourney:newtrip1-2024-01-15")
      .withVehicleJourneyRef("replaced-trip-id")
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

    var result = parser.parse(journey);

    assertTrue(result.isSuccess());
    var parsed = assertInstanceOf(ParsedAddNewTrip.class, result.successValue());

    assertNotNull(parsed.tripCreationInfo());

    var replacedTrips = parsed.tripCreationInfo().replacedTrips();
    assertEquals(1, replacedTrips.size(), "Should have one replaced trip from VehicleJourneyRef");
    assertEquals(new FeedScopedId(FEED_ID, "replaced-trip-id"), replacedTrips.get(0));
  }
}
