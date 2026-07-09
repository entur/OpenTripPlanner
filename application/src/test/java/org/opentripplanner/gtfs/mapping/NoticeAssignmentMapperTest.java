package org.opentripplanner.gtfs.mapping;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.onebusaway.gtfs.model.AgencyAndIdFactory.obaId;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.onebusaway.gtfs.impl.GtfsRelationalDaoImpl;
import org.onebusaway.gtfs.model.NoticeAssignment;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.TripSegment;
import org.opentripplanner.graph_builder.issue.api.DataImportIssue;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.graph_builder.issue.service.DefaultDataImportIssueStore;
import org.opentripplanner.transit.model._data.TimetableRepositoryForTest;

class NoticeAssignmentMapperTest {

  private static final String FEED_ID = TimetableRepositoryForTest.FEED_ID;
  private static final IdFactory ID_FACTORY = new IdFactory(FEED_ID);

  private static final String NOTICE_ID = "N1";
  private static final String NOTICE_TEXT = "Platform change";

  private static final GtfsTestData DATA = new GtfsTestData();

  private static final org.onebusaway.gtfs.model.Notice GTFS_NOTICE;

  static {
    GTFS_NOTICE = new org.onebusaway.gtfs.model.Notice();
    GTFS_NOTICE.setId(obaId(NOTICE_ID));
    GTFS_NOTICE.setDisplayText(NOTICE_TEXT);
  }

  private static RouteMapper createRouteMapper() {
    return new RouteMapper(
      ID_FACTORY,
      new AgencyMapper(ID_FACTORY),
      new RouteNetworkAssignmentMapper(ID_FACTORY),
      DataImportIssueStore.NOOP,
      new TranslationHelper()
    );
  }

  private static TripMapper createTripMapper(RouteMapper routeMapper) {
    return new TripMapper(
      ID_FACTORY,
      routeMapper,
      new DirectionMapper(DataImportIssueStore.NOOP),
      new TranslationHelper()
    );
  }

  private static NoticeAssignmentMapper createMapper(
    DataImportIssueStore issueStore,
    NoticeMapper noticeMapper,
    TripMapper tripMapper,
    RouteMapper routeMapper,
    TripSegmentMapper tripSegmentMapper
  ) {
    return new NoticeAssignmentMapper(
      ID_FACTORY,
      issueStore,
      noticeMapper,
      tripMapper,
      routeMapper,
      tripSegmentMapper
    );
  }

  private static NoticeAssignmentMapper createMapper(
    DataImportIssueStore issueStore,
    NoticeMapper noticeMapper,
    TripMapper tripMapper,
    RouteMapper routeMapper
  ) {
    return createMapper(
      issueStore,
      noticeMapper,
      tripMapper,
      routeMapper,
      new TripSegmentMapper(ID_FACTORY)
    );
  }

  @Test
  void mapNoticeAssignmentOnRoute() {
    var routeMapper = createRouteMapper();
    var route = routeMapper.map(DATA.route);

    var noticeMapper = new NoticeMapper(ID_FACTORY);
    noticeMapper.map(GTFS_NOTICE);

    var mapper = createMapper(
      DataImportIssueStore.NOOP,
      noticeMapper,
      createTripMapper(routeMapper),
      routeMapper
    );

    var assignment = new NoticeAssignment();
    assignment.setNoticeId(obaId(NOTICE_ID));
    assignment.setTableName(NoticeAssignment.TableName.routes);
    assignment.setRecordId(obaId("R1"));

    var result = mapper.map(List.of(assignment));

    assertEquals(1, result.size());
    var notice = result.get(route).iterator().next();
    assertEquals(NOTICE_ID, notice.getId().getId());
    assertEquals(NOTICE_TEXT, notice.text());
  }

  @Test
  void mapNoticeAssignmentOnTrip() {
    var routeMapper = createRouteMapper();
    var tripMapper = createTripMapper(routeMapper);
    var trip = tripMapper.map(DATA.trip);

    var noticeMapper = new NoticeMapper(ID_FACTORY);
    noticeMapper.map(GTFS_NOTICE);

    var mapper = createMapper(DataImportIssueStore.NOOP, noticeMapper, tripMapper, routeMapper);

    var assignment = new NoticeAssignment();
    assignment.setNoticeId(obaId(NOTICE_ID));
    assignment.setTableName(NoticeAssignment.TableName.trips);
    assignment.setRecordId(obaId("T1"));

    var result = mapper.map(List.of(assignment));

    assertEquals(1, result.size());
    var notice = result.get(trip).iterator().next();
    assertEquals(NOTICE_ID, notice.getId().getId());
  }

  @Test
  void mapNoticeAssignmentOnTripSegment() {
    var routeMapper = createRouteMapper();
    var tripMapper = createTripMapper(routeMapper);

    var noticeMapper = new NoticeMapper(ID_FACTORY);
    noticeMapper.map(GTFS_NOTICE);

    var segment = new TripSegment();
    segment.setId(obaId("SEG1"));
    segment.setTripId(DATA.trip.getId());
    segment.setFromStopSequence(2);
    segment.setToStopSequence(4);

    var dao = new GtfsRelationalDaoImpl();
    dao.saveEntity(DATA.trip);
    for (int seq = 1; seq <= 5; seq++) {
      var stopTime = new StopTime();
      stopTime.setId(seq);
      stopTime.setTrip(DATA.trip);
      stopTime.setStop(DATA.stop);
      stopTime.setStopSequence(seq);
      dao.saveEntity(stopTime);
    }

    var tripSegmentMapper = new TripSegmentMapper(ID_FACTORY);
    tripSegmentMapper.map(List.of(segment), dao);

    var mapper = createMapper(
      DataImportIssueStore.NOOP,
      noticeMapper,
      tripMapper,
      routeMapper,
      tripSegmentMapper
    );

    var assignment = new NoticeAssignment();
    assignment.setNoticeId(obaId(NOTICE_ID));
    assignment.setTableName(NoticeAssignment.TableName.trip_segments);
    assignment.setRecordId(obaId("SEG1"));

    var result = mapper.map(List.of(assignment));

    // Stop sequences 2, 3 and 4 are within the segment's [from, to] range
    assertEquals(3, result.size());
    var stopTimeKeyIds = result
      .keySet()
      .stream()
      .map(e -> e.getId().getId())
      .sorted()
      .toList();
    assertThat(stopTimeKeyIds).containsExactly("T1_#2", "T1_#3", "T1_#4");
  }

  @Test
  void missingNoticeLogsIssue() {
    var issues = new DefaultDataImportIssueStore();
    var noticeMapper = new NoticeMapper(ID_FACTORY);
    var routeMapper = createRouteMapper();

    var mapper = createMapper(issues, noticeMapper, createTripMapper(routeMapper), routeMapper);

    var assignment = new NoticeAssignment();
    assignment.setNoticeId(obaId("NONEXISTENT"));
    assignment.setTableName(NoticeAssignment.TableName.routes);
    assignment.setRecordId(obaId("R1"));

    var result = mapper.map(List.of(assignment));

    assertThat(result).isEmpty();
    assertThat(issues.listIssues().stream().map(DataImportIssue::getType)).containsExactly(
      "NoticeAssignmentWithoutNotice"
    );
  }

  @Test
  void missingRouteEntityLogsIssue() {
    var issues = new DefaultDataImportIssueStore();
    var noticeMapper = new NoticeMapper(ID_FACTORY);
    noticeMapper.map(GTFS_NOTICE);
    var routeMapper = createRouteMapper();

    var mapper = createMapper(issues, noticeMapper, createTripMapper(routeMapper), routeMapper);

    var assignment = new NoticeAssignment();
    assignment.setNoticeId(obaId(NOTICE_ID));
    assignment.setTableName(NoticeAssignment.TableName.routes);
    assignment.setRecordId(obaId("NONEXISTENT"));

    var result = mapper.map(List.of(assignment));

    assertThat(result).isEmpty();
    assertThat(issues.listIssues().stream().map(DataImportIssue::getType).toList()).containsExactly(
      "NoticeAssignmentWithUnknownEntity"
    );
  }

  @Test
  void missingTripEntityLogsIssue() {
    var issues = new DefaultDataImportIssueStore();
    var noticeMapper = new NoticeMapper(ID_FACTORY);
    noticeMapper.map(GTFS_NOTICE);
    var routeMapper = createRouteMapper();

    var mapper = createMapper(issues, noticeMapper, createTripMapper(routeMapper), routeMapper);

    var assignment = new NoticeAssignment();
    assignment.setNoticeId(obaId(NOTICE_ID));
    assignment.setTableName(NoticeAssignment.TableName.trips);
    assignment.setRecordId(obaId("NONEXISTENT"));

    var result = mapper.map(List.of(assignment));

    assertThat(result).isEmpty();
    assertThat(issues.listIssues().stream().map(DataImportIssue::getType).toList()).containsExactly(
      "NoticeAssignmentWithUnknownEntity"
    );
  }

  @Test
  void multipleAssignmentsMappedTogether() {
    var routeMapper = createRouteMapper();
    var tripMapper = createTripMapper(routeMapper);

    var route = routeMapper.map(DATA.route);
    var trip = tripMapper.map(DATA.trip);

    var noticeMapper = new NoticeMapper(ID_FACTORY);
    noticeMapper.map(GTFS_NOTICE);

    var mapper = createMapper(DataImportIssueStore.NOOP, noticeMapper, tripMapper, routeMapper);

    var routeAssignment = new NoticeAssignment();
    routeAssignment.setNoticeId(obaId(NOTICE_ID));
    routeAssignment.setTableName(NoticeAssignment.TableName.routes);
    routeAssignment.setRecordId(obaId("R1"));

    var tripAssignment = new NoticeAssignment();
    tripAssignment.setNoticeId(obaId(NOTICE_ID));
    tripAssignment.setTableName(NoticeAssignment.TableName.trips);
    tripAssignment.setRecordId(obaId("T1"));

    var result = mapper.map(List.of(routeAssignment, tripAssignment));

    assertEquals(2, result.size());
    assertEquals(1, result.get(route).size());
    assertEquals(1, result.get(trip).size());
  }
}
