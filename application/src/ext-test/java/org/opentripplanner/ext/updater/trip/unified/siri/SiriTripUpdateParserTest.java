package org.opentripplanner.ext.updater.trip.unified.siri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.LocalTimeParser;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.model.command.AbsoluteTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.AddTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.CancelTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.ModifyTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.ParsedStopTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.ParsedTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.ReviseTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.StopResolutionStrategy;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;
import org.opentripplanner.updater.trip.siri.SiriEtBuilder;

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

    var command = assertInstanceOf(ReviseTrip.class, parser.parse(journey));

    assertNull(command.tripReference().tripId());
    assertEquals(
      new FeedScopedId(FEED_ID, "dated-trip1"),
      command.tripReference().tripOnServiceDateId()
    );
    assertNull(command.serviceDate());
    assertEquals(1, command.stopTimeUpdates().size());

    var stopUpdate = command.stopTimeUpdates().get(0);
    assertEquals(new FeedScopedId(FEED_ID, "stop1"), stopUpdate.stopReference().stopId());
    assertEquals(
      StopResolutionStrategy.SCHEDULED_STOP_POINT_FIRST,
      stopUpdate.stopReference().resolutionStrategy()
    );
    assertNotNull(stopUpdate.departureUpdate());
    assertTrue(
      stopUpdate.departureUpdate() instanceof
        org.opentripplanner.ext.updater.trip.unified.model.command.DeferredTimeUpdate,
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

    assertInstanceOf(CancelTrip.class, parser.parse(journey));
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

    var command = assertInstanceOf(AddTrip.class, parser.parse(journey));

    assertNotNull(command.tripCreationInfo());
    assertEquals(
      new FeedScopedId(FEED_ID, "NSB:ServiceJourney:newtrip1-2024-01-15"),
      command.tripCreationInfo().tripId()
    );
    assertEquals(new FeedScopedId(FEED_ID, "route1"), command.tripCreationInfo().routeId());
    assertEquals(new FeedScopedId(FEED_ID, "operator1"), command.tripCreationInfo().operatorId());
    assertEquals(2, command.stopTimeUpdates().size());
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

    var command = assertInstanceOf(ModifyTrip.class, parser.parse(journey));

    assertEquals(3, command.stopTimeUpdates().size());

    var extraCallUpdate = command.stopTimeUpdates().get(1);
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

    var command = assertInstanceOf(ReviseTrip.class, parser.parse(journey));

    assertEquals(3, command.stopTimeUpdates().size());
    var cancelledStop = command.stopTimeUpdates().get(1);
    assertEquals(ParsedStopTimeUpdate.StopUpdateStatus.CANCELLED, cancelledStop.status());
  }

  @Test
  void parseWithRecordedCalls() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("trip1")
      .withRecordedCalls(calls -> calls.call("stop1").departAimedActual("08:00", "08:01"))
      .withEstimatedCalls(calls -> calls.call("stop2").arriveAimedExpected("08:30", "08:32"))
      .buildEstimatedVehicleJourney();

    var command = assertInstanceOf(ReviseTrip.class, parser.parse(journey));

    assertEquals(2, command.stopTimeUpdates().size());

    var recordedStop = command.stopTimeUpdates().get(0);
    assertTrue(recordedStop.hasArrived());
    assertTrue(recordedStop.hasDeparted());
    assertNotNull(recordedStop.departureUpdate());

    var estimatedStop = command.stopTimeUpdates().get(1);
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

    var command = assertInstanceOf(ReviseTrip.class, parser.parse(journey));

    var stopUpdate = command.stopTimeUpdates().get(0);
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

    var command = assertInstanceOf(ReviseTrip.class, parser.parse(journey));

    var stopUpdate = command.stopTimeUpdates().get(0);
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

    assertThrows(UpdateException.class, () -> parser.parse(journey));
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

    assertInstanceOf(CancelTrip.class, parser.parse(journey));
  }

  @Test
  void parseEmptyStopPointRef() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("trip1")
      .withEstimatedCalls(calls -> calls.call("").withAimedDepartureTime("08:00"))
      .buildEstimatedVehicleJourney();

    assertThrows(UpdateException.class, () -> parser.parse(journey));
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

    var command = assertInstanceOf(ReviseTrip.class, parser.parse(journey));

    var stopUpdate = command.stopTimeUpdates().get(0);
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

    var command = assertInstanceOf(ReviseTrip.class, parser.parse(journey));

    var stopUpdate = command.stopTimeUpdates().get(0);

    assertNotNull(asAbsolute(stopUpdate.arrivalUpdate()).aimedTime());
    assertNotNull(asAbsolute(stopUpdate.departureUpdate()).aimedTime());
  }

  @Test
  void parseSiriDefaultOptions() {
    var journey = new SiriEtBuilder(timeParser)
      .withDatedVehicleJourneyRef("trip1")
      .withEstimatedCalls(calls ->
        calls.call("stop1").withAimedDepartureTime("08:00").withExpectedDepartureTime("08:00")
      )
      .buildEstimatedVehicleJourney();

    var command = assertInstanceOf(ReviseTrip.class, parser.parse(journey));

    assertEquals(
      ForwardsDelayPropagationType.NONE,
      command.formatPolicy().delayPropagation().forwards()
    );
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

    var command = parser.parse(journey);

    assertEquals(new FeedScopedId(FEED_ID, "trip1"), command.tripReference().tripId());
    assertNull(command.tripReference().tripOnServiceDateId());
    assertEquals(TEST_DATE, command.serviceDate());
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

    var command = assertInstanceOf(ReviseTrip.class, parser.parse(journey));

    assertEquals(3, command.stopTimeUpdates().size());
    assertEquals(
      new FeedScopedId(FEED_ID, "stop1"),
      command.stopTimeUpdates().get(0).stopReference().stopId()
    );
    assertEquals(
      new FeedScopedId(FEED_ID, "stop2"),
      command.stopTimeUpdates().get(1).stopReference().stopId()
    );
    assertEquals(
      new FeedScopedId(FEED_ID, "stop3"),
      command.stopTimeUpdates().get(2).stopReference().stopId()
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

    var command = parser.parse(journey);

    assertEquals("DATASOURCE", command.dataSource());
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

    var command = assertInstanceOf(ReviseTrip.class, parser.parse(journey));

    assertEquals(2, command.stopTimeUpdates().size());

    var firstStop = command.stopTimeUpdates().get(0);
    assertNotNull(firstStop.arrivalUpdate(), "First stop should have arrival update via fallback");
    assertNotNull(firstStop.departureUpdate());

    assertEquals(
      asAbsolute(firstStop.departureUpdate()).time(),
      asAbsolute(firstStop.arrivalUpdate()).time(),
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

    var command = assertInstanceOf(ReviseTrip.class, parser.parse(journey));

    assertEquals(2, command.stopTimeUpdates().size());

    var lastStop = command.stopTimeUpdates().get(1);
    assertNotNull(lastStop.arrivalUpdate());
    assertNotNull(
      lastStop.departureUpdate(),
      "Last stop should have departure update via fallback"
    );

    assertEquals(
      asAbsolute(lastStop.arrivalUpdate()).time(),
      asAbsolute(lastStop.departureUpdate()).time(),
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

    var command = assertInstanceOf(ReviseTrip.class, parser.parse(journey));

    assertEquals(3, command.stopTimeUpdates().size());

    var middleStop = command.stopTimeUpdates().get(1);
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

    var command = assertInstanceOf(ReviseTrip.class, parser.parse(journey));

    assertEquals(1, command.stopTimeUpdates().size());

    var singleStop = command.stopTimeUpdates().get(0);
    assertNotNull(singleStop.arrivalUpdate(), "Single stop should have arrival via fallback");
    assertNotNull(singleStop.departureUpdate());

    assertEquals(
      asAbsolute(singleStop.departureUpdate()).time(),
      asAbsolute(singleStop.arrivalUpdate()).time(),
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

    var command = assertInstanceOf(ReviseTrip.class, parser.parse(journey));

    assertEquals(2, command.stopTimeUpdates().size());

    var firstStop = command.stopTimeUpdates().get(0);
    var lastStop = command.stopTimeUpdates().get(1);

    assertTrue(firstStop.hasArrived(), "First stop should be marked as arrived (recorded call)");
    assertTrue(firstStop.hasDeparted(), "First stop should be marked as departed (recorded call)");
    assertNotNull(firstStop.departureUpdate());

    assertFalse(
      lastStop.hasArrived(),
      "Last stop should not be marked as arrived (only has expected time)"
    );
    assertNotNull(lastStop.arrivalUpdate());
  }

  private static AbsoluteTimeUpdate asAbsolute(@Nullable ParsedTimeUpdate parsedTimeUpdate) {
    return assertInstanceOf(AbsoluteTimeUpdate.class, parsedTimeUpdate);
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

    var command = assertInstanceOf(AddTrip.class, parser.parse(journey));

    assertNotNull(command.tripCreationInfo());

    var replacedTrips = command.tripCreationInfo().replacedTrips();
    assertEquals(1, replacedTrips.size(), "Should have one replaced trip from VehicleJourneyRef");
    assertEquals(new FeedScopedId(FEED_ID, "replaced-trip-id"), replacedTrips.get(0));
  }
}
