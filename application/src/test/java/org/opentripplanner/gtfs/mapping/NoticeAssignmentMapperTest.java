package org.opentripplanner.gtfs.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.transit.model._data.TimetableRepositoryForTest.route;
import static org.opentripplanner.transit.model._data.TimetableRepositoryForTest.trip;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.NoticeAssignment;
import org.opentripplanner.graph_builder.issue.api.DataImportIssue;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.graph_builder.issue.service.DefaultDataImportIssueStore;
import org.opentripplanner.transit.model._data.TimetableRepositoryForTest;
import org.opentripplanner.transit.model.framework.DefaultEntityById;

class NoticeAssignmentMapperTest {

  private static final String FEED_ID = TimetableRepositoryForTest.FEED_ID;
  private static final IdFactory ID_FACTORY = new IdFactory(FEED_ID);

  private static final String NOTICE_ID = "N1";
  private static final String NOTICE_TEXT = "Platform change";

  private static final org.onebusaway.gtfs.model.Notice GTFS_NOTICE;

  static {
    GTFS_NOTICE = new org.onebusaway.gtfs.model.Notice();
    GTFS_NOTICE.setId(new AgencyAndId(FEED_ID, NOTICE_ID));
    GTFS_NOTICE.setDisplayText(NOTICE_TEXT);
  }

  @Test
  void mapNoticeAssignmentOnRoute() {
    var route = route("R1").build();

    var routesById = new DefaultEntityById<org.opentripplanner.transit.model.network.Route>();
    routesById.add(route);

    var mapper = new NoticeAssignmentMapper(
      ID_FACTORY,
      DataImportIssueStore.NOOP,
      List.of(GTFS_NOTICE),
      routesById,
      new DefaultEntityById<>()
    );

    var assignment = new NoticeAssignment();
    assignment.setNoticeId(new AgencyAndId(FEED_ID, NOTICE_ID));
    assignment.setTableName(NoticeAssignment.TableName.routes);
    assignment.setRecordId(new AgencyAndId(FEED_ID, "R1"));

    var result = mapper.map(List.of(assignment));

    assertEquals(1, result.size());
    var notice = result.get(route).iterator().next();
    assertEquals(NOTICE_ID, notice.getId().getId());
    assertEquals(NOTICE_TEXT, notice.text());
  }

  @Test
  void mapNoticeAssignmentOnTrip() {
    var trip = trip("T1").build();

    var tripsById = new DefaultEntityById<org.opentripplanner.transit.model.timetable.Trip>();
    tripsById.add(trip);

    var mapper = new NoticeAssignmentMapper(
      ID_FACTORY,
      DataImportIssueStore.NOOP,
      List.of(GTFS_NOTICE),
      new DefaultEntityById<>(),
      tripsById
    );

    var assignment = new NoticeAssignment();
    assignment.setNoticeId(new AgencyAndId(FEED_ID, NOTICE_ID));
    assignment.setTableName(NoticeAssignment.TableName.trips);
    assignment.setRecordId(new AgencyAndId(FEED_ID, "T1"));

    var result = mapper.map(List.of(assignment));

    assertEquals(1, result.size());
    var notice = result.get(trip).iterator().next();
    assertEquals(NOTICE_ID, notice.getId().getId());
  }

  @Test
  void missingNoticeLogsIssue() {
    var issues = new DefaultDataImportIssueStore();
    var mapper = new NoticeAssignmentMapper(
      ID_FACTORY,
      issues,
      List.of(),
      new DefaultEntityById<>(),
      new DefaultEntityById<>()
    );

    var assignment = new NoticeAssignment();
    assignment.setNoticeId(new AgencyAndId(FEED_ID, "NONEXISTENT"));
    assignment.setTableName(NoticeAssignment.TableName.routes);
    assignment.setRecordId(new AgencyAndId(FEED_ID, "R1"));

    var result = mapper.map(List.of(assignment));

    assertTrue(result.isEmpty());
    assertEquals(
      List.of("NoticeAssignmentWithoutNotice"),
      issues.listIssues().stream().map(DataImportIssue::getType).toList()
    );
  }

  @Test
  void missingRouteEntityLogsIssue() {
    var issues = new DefaultDataImportIssueStore();
    var mapper = new NoticeAssignmentMapper(
      ID_FACTORY,
      issues,
      List.of(GTFS_NOTICE),
      new DefaultEntityById<>(),
      new DefaultEntityById<>()
    );

    var assignment = new NoticeAssignment();
    assignment.setNoticeId(new AgencyAndId(FEED_ID, NOTICE_ID));
    assignment.setTableName(NoticeAssignment.TableName.routes);
    assignment.setRecordId(new AgencyAndId(FEED_ID, "NONEXISTENT"));

    var result = mapper.map(List.of(assignment));

    assertTrue(result.isEmpty());
    assertEquals(
      List.of("NoticeAssignmentWithUnknownEntity"),
      issues.listIssues().stream().map(DataImportIssue::getType).toList()
    );
  }

  @Test
  void missingTripEntityLogsIssue() {
    var issues = new DefaultDataImportIssueStore();
    var mapper = new NoticeAssignmentMapper(
      ID_FACTORY,
      issues,
      List.of(GTFS_NOTICE),
      new DefaultEntityById<>(),
      new DefaultEntityById<>()
    );

    var assignment = new NoticeAssignment();
    assignment.setNoticeId(new AgencyAndId(FEED_ID, NOTICE_ID));
    assignment.setTableName(NoticeAssignment.TableName.trips);
    assignment.setRecordId(new AgencyAndId(FEED_ID, "NONEXISTENT"));

    var result = mapper.map(List.of(assignment));

    assertTrue(result.isEmpty());
    assertEquals(
      List.of("NoticeAssignmentWithUnknownEntity"),
      issues.listIssues().stream().map(DataImportIssue::getType).toList()
    );
  }

  @Test
  void multipleAssignmentsMappedTogether() {
    var route = route("R1").build();
    var trip = trip("T1").build();

    var routesById = new DefaultEntityById<org.opentripplanner.transit.model.network.Route>();
    routesById.add(route);
    var tripsById = new DefaultEntityById<org.opentripplanner.transit.model.timetable.Trip>();
    tripsById.add(trip);

    var mapper = new NoticeAssignmentMapper(
      ID_FACTORY,
      DataImportIssueStore.NOOP,
      List.of(GTFS_NOTICE),
      routesById,
      tripsById
    );

    var routeAssignment = new NoticeAssignment();
    routeAssignment.setNoticeId(new AgencyAndId(FEED_ID, NOTICE_ID));
    routeAssignment.setTableName(NoticeAssignment.TableName.routes);
    routeAssignment.setRecordId(new AgencyAndId(FEED_ID, "R1"));

    var tripAssignment = new NoticeAssignment();
    tripAssignment.setNoticeId(new AgencyAndId(FEED_ID, NOTICE_ID));
    tripAssignment.setTableName(NoticeAssignment.TableName.trips);
    tripAssignment.setRecordId(new AgencyAndId(FEED_ID, "T1"));

    var result = mapper.map(List.of(routeAssignment, tripAssignment));

    assertEquals(2, result.size());
    assertEquals(1, result.get(route).size());
    assertEquals(1, result.get(trip).size());
  }
}
