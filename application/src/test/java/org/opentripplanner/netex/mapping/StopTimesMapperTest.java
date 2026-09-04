package org.opentripplanner.netex.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.entur.netex2.model.StopPointInJourneyPattern;
import org.entur.netex2.model.TimetabledPassingTime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentripplanner.graph_builder.issue.api.DataImportIssue;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.graph_builder.issue.service.DefaultDataImportIssueStore;
import org.opentripplanner.model.StopTime;
import org.opentripplanner.netex.index.hierarchy.HierarchicalMap;
import org.opentripplanner.netex.index.hierarchy.HierarchicalMapById;
import org.opentripplanner.transit.model._data.TransitRepositoryForTest;
import org.opentripplanner.transit.model.framework.DefaultEntityById;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.site.Station;
import org.opentripplanner.transit.model.timetable.Trip;

public class StopTimesMapperTest {

  private static final BigInteger ZERO = BigInteger.valueOf(0);
  private static final BigInteger ONE = BigInteger.valueOf(1);
  private static final BigInteger TWO = BigInteger.valueOf(2);

  private static final LocalTime QUARTER_PAST_FIVE = LocalTime.of(5, 15);

  private static final String STOP_POINT_3 = "RUT:StopPointInJourneyPattern:3";
  private static final String MEETING_POINT = "NBG:VehicleMeetingPoint:1";
  public static final Trip TRIP = TransitRepositoryForTest.trip("T1").build();

  @Test
  public void testCalculateOtpTime() {
    assertEquals(18900, StopTimesMapper.calculateOtpTime(QUARTER_PAST_FIVE, ZERO));
    assertEquals(105300, StopTimesMapper.calculateOtpTime(QUARTER_PAST_FIVE, ONE));
    assertEquals(191700, StopTimesMapper.calculateOtpTime(QUARTER_PAST_FIVE, TWO));
  }

  @Test
  public void testMapStopTimes() {
    NetexTestDataSample sample = new NetexTestDataSample();

    StopTimesMapper stopTimesMapper = new StopTimesMapper(
      DataImportIssueStore.NOOP,
      MappingSupport.ID_FACTORY,
      sample.getStopsById(),
      new DefaultEntityById<>(),
      new DefaultEntityById<>(),
      new DefaultEntityById<>(),
      sample.getDestinationDisplayById(),
      new HierarchicalMap<>(),
      sample.getQuayIdByStopPointRef(),
      new HierarchicalMap<>(),
      new HierarchicalMap<>(),
      new HierarchicalMapById<>(),
      new HierarchicalMap<>()
    );

    StopTimesMapperResult result = stopTimesMapper.mapToStopTimes(
      sample.getJourneyPattern(),
      TRIP,
      sample.getTimetabledPassingTimes(),
      null
    );

    List<StopTime> stopTimes = result.stopTimes;

    assertEquals(4, stopTimes.size());

    assertStop(stopTimes.get(0), "NSR:Quay:1", 18000, "Bergen", List.of("Stavanger"));
    assertStop(stopTimes.get(1), "NSR:Quay:2", 18240, "Bergen", List.of("Stavanger"));
    assertStop(stopTimes.get(2), "NSR:Quay:3", 18600, "Stavanger", List.of("Bergen"));
    assertStop(stopTimes.get(3), "NSR:Quay:4", 18900, "Stavanger", List.of("Bergen"));

    Map<String, StopTime> map = result.stopTimeByNetexId;

    assertEquals(stopTimes.get(0), map.get("TTPT-1"));
    assertEquals(stopTimes.get(1), map.get("TTPT-2"));
    assertEquals(stopTimes.get(2), map.get("TTPT-3"));
    assertEquals(stopTimes.get(3), map.get("TTPT-4"));
  }

  /**
   * If a PassengerStopAssignment refers to a StopPlace instead of a Quay, the stop should be
   * resolved to one of the StopPlace's (Station's) child stops - the one with the smallest id,
   * see {@link StopTimesMapper}.
   */
  @Test
  public void testMapStopTimesWithPassengerStopAssignmentToStopPlace() {
    NetexTestDataSample sample = new NetexTestDataSample();
    var testModel = TransitRepositoryForTest.of();

    Station station = testModel.station("NSR:StopPlace:1").build();
    RegularStop expectedChildStop = testModel
      .stop("NSR:Quay:98")
      .withParentStation(station)
      .build();
    testModel.stop("NSR:Quay:99").withParentStation(station).build();

    DefaultEntityById<Station> stationsById = new DefaultEntityById<>();
    stationsById.add(station);

    HierarchicalMap<String, String> stopPlaceIdByStopPointRef = new HierarchicalMap<>();
    sample
      .getJourneyPattern()
      .getPointsInSequence()
      .getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern()
      .stream()
      .map(StopPointInJourneyPattern.class::cast)
      .forEach(stopPoint ->
        stopPlaceIdByStopPointRef.add(
          stopPoint.getScheduledStopPointRef().getValue().getRef(),
          station.getId().getId()
        )
      );

    StopTimesMapper stopTimesMapper = new StopTimesMapper(
      DataImportIssueStore.NOOP,
      MappingSupport.ID_FACTORY,
      sample.getStopsById(),
      stationsById,
      new DefaultEntityById<>(),
      new DefaultEntityById<>(),
      sample.getDestinationDisplayById(),
      stopPlaceIdByStopPointRef,
      new HierarchicalMap<>(),
      new HierarchicalMap<>(),
      new HierarchicalMap<>(),
      new HierarchicalMapById<>(),
      new HierarchicalMap<>()
    );

    StopTimesMapperResult result = stopTimesMapper.mapToStopTimes(
      sample.getJourneyPattern(),
      TRIP,
      sample.getTimetabledPassingTimes(),
      null
    );

    assertNotNull(result, "result must not be null");

    List<StopTime> stopTimes = result.stopTimes;
    assertEquals(4, stopTimes.size());

    for (StopTime stopTime : stopTimes) {
      assertEquals(expectedChildStop, stopTime.getStop());
    }
  }

  /**
   * Test StopTime.timepoint mapping from NeTEx. Should be true if
   * StopPointInJourneyPattern.isIsWaitPoint is true and corresponding
   * TimetabledPassingTime.waitingTime is defined
   * <p>
   * The sample has 4 StopPointInJourneyPattern points with corresponding TimetabledPassingTime
   * objects. The two last StopPointInJourneyPattern has withIsWaitPoint and undefined, so they
   * should not set StopTime.timepoint = 1. The second TimetabledPassingTime.WaitingTime is
   * undefined so it should also not set StopTime.timepoint = 1. Only the first StopTime has the
   * correct criteria to map StopTime.timepoint = 1.
   */
  @Test
  public void testWaitPointMapping() {
    var netexSample = new NetexTestDataSample();

    var points = netexSample
      .getJourneyPattern()
      .getPointsInSequence()
      .getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern()
      .stream()
      .map(StopPointInJourneyPattern.class::cast)
      .collect(Collectors.toList());

    assertEquals(4, points.size(), "Expected StopPointInJourneyPattern.size to be 4");

    points.get(0).withIsWaitPoint(true);
    points.get(1).withIsWaitPoint(true);
    points.get(2).withIsWaitPoint(false);
    points.get(3).withIsWaitPoint(null);

    var passingTimes = netexSample.getTimetabledPassingTimes();
    assertEquals(
      points.size(),
      passingTimes.size(),
      "Expected TimetabledPassingTimes size equal StopPointInJourneyPattern size"
    );

    // Utility function to find TimetabledPassingTime by StopPointInJourneyPattern.Id
    Function<String, TimetabledPassingTime> findPassingTime = pointId ->
      passingTimes
        .stream()
        .filter(t -> pointId.equals(t.getPointInJourneyPatternRef().getValue().getRef()))
        .findAny()
        .orElseThrow();

    var firstPassingTime = findPassingTime.apply(points.get(0).getId());
    var thirdPassingTime = findPassingTime.apply(points.get(2).getId());
    var fourthPassingTime = findPassingTime.apply(points.get(3).getId());

    // Make TimeTabledPassingTime valid waitPoint
    firstPassingTime.setWaitingTime(Duration.ofSeconds(0));
    thirdPassingTime.setWaitingTime(Duration.ofSeconds(10));
    fourthPassingTime.setWaitingTime(Duration.ofSeconds(-5));

    StopTimesMapper stopTimesMapper = new StopTimesMapper(
      DataImportIssueStore.NOOP,
      MappingSupport.ID_FACTORY,
      netexSample.getStopsById(),
      new DefaultEntityById<>(),
      new DefaultEntityById<>(),
      new DefaultEntityById<>(),
      netexSample.getDestinationDisplayById(),
      new HierarchicalMap<>(),
      netexSample.getQuayIdByStopPointRef(),
      new HierarchicalMap<>(),
      new HierarchicalMap<>(),
      new HierarchicalMapById<>(),
      new HierarchicalMap<>()
    );

    StopTimesMapperResult result = stopTimesMapper.mapToStopTimes(
      netexSample.getJourneyPattern(),
      TRIP,
      netexSample.getTimetabledPassingTimes(),
      null
    );

    assertNotNull(result, "result must not be null");

    var stopTimes = result.stopTimes;

    assertEquals(4, stopTimes.size(), "Exptected 4 StopTime objects");

    Assertions.assertAll(
      () -> assertEquals(1, stopTimes.get(0).getTimepoint(), "StopTime expected to be waitPoint"),
      () ->
        assertNotEquals(
          1,
          stopTimes.get(1).getTimepoint(),
          "StopTime expected to not be waitPoint"
        ),
      () ->
        assertNotEquals(
          1,
          stopTimes.get(2).getTimepoint(),
          "StopTime expected to not be waitPoint"
        ),
      () ->
        assertNotEquals(1, stopTimes.get(3).getTimepoint(), "StopTime expected to not be waitPoint")
    );
  }

  /**
   * A scheduled stop point projected onto a VehicleMeetingPoint resolves to the stop that
   * {@link VehicleMeetingPointMapper} created for it - the trip is mapped as usual, and the
   * meeting point is indistinguishable from a Quay at this level.
   */
  @Test
  void tripIsMappedWhenStopPointIsProjectedToVehicleMeetingPoint() {
    var sample = new NetexTestDataSample();
    sample
      .getStopsById()
      .add(TransitRepositoryForTest.of().stop(MEETING_POINT, 63.437207, 10.419224).build());

    var issueStore = new DefaultDataImportIssueStore();
    var result = mapWithMeetingPoint(sample, issueStore);

    assertNotNull(result);
    assertEquals(4, result.stopTimes.size());
    assertEquals(MEETING_POINT, result.stopTimes.get(2).getStop().getId().getId());
    assertEquals(List.of(), issueTypes(issueStore));
  }

  /**
   * The same setup, but with no stop for the meeting point - the state left behind when
   * {@link VehicleMeetingPointMapper} drops a VehicleMeetingPoint that has no coordinates. The
   * projection still resolves the stop point to an id, so the stop is looked up and comes back
   * empty. The whole trip is then dropped, which is what we want: a carpool trip is only usable if
   * every meeting point on it is.
   */
  @Test
  void tripIsDroppedWhenVehicleMeetingPointHasNoStop() {
    var sample = new NetexTestDataSample();
    // Deliberately no stop added for MEETING_POINT.

    var issueStore = new DefaultDataImportIssueStore();
    var result = mapWithMeetingPoint(sample, issueStore);

    assertNull(result, "A trip with an unresolved meeting point must not be mapped");
    assertEquals(
      List.of("StopPointInJourneyPatternMissingStopLocation", "JourneyPatternStopNotFound"),
      issueTypes(issueStore)
    );
  }

  /**
   * Maps the sample journey pattern with the third stop point projected onto a
   * VehicleMeetingPoint instead of assigned to a Quay.
   */
  private static StopTimesMapperResult mapWithMeetingPoint(
    NetexTestDataSample sample,
    DataImportIssueStore issueStore
  ) {
    var quays = new HierarchicalMap<String, String>();
    var source = sample.getQuayIdByStopPointRef();
    source
      .localKeys()
      .stream()
      .filter(key -> !STOP_POINT_3.equals(key))
      .forEach(key -> quays.add(key, source.lookup(key)));

    var meetingPoints = new HierarchicalMap<String, String>();
    meetingPoints.add(STOP_POINT_3, MEETING_POINT);

    var mapper = new StopTimesMapper(
      issueStore,
      MappingSupport.ID_FACTORY,
      sample.getStopsById(),
      new DefaultEntityById<>(),
      new DefaultEntityById<>(),
      new DefaultEntityById<>(),
      sample.getDestinationDisplayById(),
      new HierarchicalMap<>(),
      quays,
      new HierarchicalMap<>(),
      meetingPoints,
      new HierarchicalMapById<>(),
      new HierarchicalMap<>()
    );

    return mapper.mapToStopTimes(
      sample.getJourneyPattern(),
      TRIP,
      sample.getTimetabledPassingTimes(),
      null
    );
  }

  private static List<String> issueTypes(DefaultDataImportIssueStore issueStore) {
    return issueStore.listIssues().stream().map(DataImportIssue::getType).toList();
  }

  private void assertStop(
    StopTime stopTime,
    String stopId,
    long departureTime,
    String headsign,
    List<String> via
  ) {
    assertEquals(stopId, stopTime.getStop().getId().getId());
    assertEquals(departureTime, stopTime.getDepartureTime());
    assertEquals(headsign, stopTime.getStopHeadsign().toString());

    List<String> stopTimeVia = stopTime.getHeadsignVias();
    assertEquals(via, stopTimeVia);
  }
}
