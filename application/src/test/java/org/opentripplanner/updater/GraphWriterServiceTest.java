package org.opentripplanner.updater;

import static com.google.common.truth.Truth.assertThat;
import static org.opentripplanner.core.model.id.FeedScopedIdForTestFactory.id;

import com.google.common.collect.ArrayListMultimap;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.framework.transaction.RepositoryRegistry;
import org.opentripplanner.framework.transaction.api.RepositoryHandle;
import org.opentripplanner.framework.transaction.internal.TransactionFactory;
import org.opentripplanner.model.calendar.CalendarServiceData;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RaptorTransitDataTestFactory;
import org.opentripplanner.service.realtimevehicles.RealtimeVehicleRepository;
import org.opentripplanner.service.realtimevehicles.RealtimeVehicleRepositorySnapshot;
import org.opentripplanner.service.realtimevehicles.internal.DefaultRealtimeVehicleRepository;
import org.opentripplanner.service.realtimevehicles.internal.RealtimeVehicleRepositoryLifecycle;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.transit.model._data.TimetableRepositoryForTest;
import org.opentripplanner.transit.model.calendar.DefaultTripCalendars;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.ScheduledTripTimes;
import org.opentripplanner.transit.model.timetable.TimetableSnapshot;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.repository.MutableTimetableSnapshot;
import org.opentripplanner.transit.repository.ReadOnlyTimetableSnapshot;
import org.opentripplanner.transit.repository.TimetableSnapshotLifecycle;
import org.opentripplanner.transit.service.TimetableRepository;

/**
 * Tests the transit-data views that {@link GraphWriterService} hands to update tasks through the
 * {@link RealTimeUpdateContext}: the uncommitted view ({@code transitService()}) sees changes in
 * the write buffer, the committed view ({@code committedTransitService()}) sees only the last
 * committed snapshot, and resolving the committed view does not mark the timetable repository as
 * modified in the transaction.
 */
class GraphWriterServiceTest {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, Month.MAY, 30);
  private static final FeedScopedId SERVICE_ID = id("CAL_1");
  private static final int SERVICE_CODE = 0;

  private static final TimetableRepositoryForTest TEST_MODEL = TimetableRepositoryForTest.of();

  private static final Route ROUTE = TimetableRepositoryForTest.route("route1").build();
  private static final Trip TRIP = TimetableRepositoryForTest.trip("trip1")
    .withServiceId(SERVICE_ID)
    .build();
  private static final RegularStop STOP_1 = TEST_MODEL.stop("S1").build();
  private static final RegularStop STOP_2 = TEST_MODEL.stop("S2").build();
  private static final RegularStop STOP_3 = TEST_MODEL.stop("S3").build();

  private static final ScheduledTripTimes SCHEDULED_TRIP_TIMES = ScheduledTripTimes.of()
    .withArrivalTimes("00:00 00:01")
    .withServiceCode(SERVICE_CODE)
    .withTrip(TRIP)
    .build();

  private static final TripPattern SCHEDULED_PATTERN = TimetableRepositoryForTest.tripPattern(
    "sched",
    ROUTE
  )
    .withStopPattern(TimetableRepositoryForTest.stopPattern(STOP_1, STOP_2))
    .withScheduledTimeTableBuilder(ttb -> ttb.addTripTimes(SCHEDULED_TRIP_TIMES))
    .build();

  private static final TripPattern MODIFIED_PATTERN = TimetableRepositoryForTest.tripPattern(
    "modified",
    ROUTE
  )
    .withStopPattern(TimetableRepositoryForTest.stopPattern(STOP_1, STOP_3))
    .withRealTimeStopPatternModified()
    .build();

  private RepositoryRegistry registry;
  private RepositoryHandle<ReadOnlyTimetableSnapshot, MutableTimetableSnapshot> timetableHandle;
  private RepositoryHandle<
    RealtimeVehicleRepositorySnapshot,
    RealtimeVehicleRepository
  > vehicleHandle;
  private GraphWriterService service;

  @BeforeEach
  void setUp() {
    var siteRepository = TEST_MODEL.siteRepositoryBuilder()
      .withRegularStop(STOP_1)
      .withRegularStop(STOP_2)
      .withRegularStop(STOP_3)
      .build();
    var timetableRepository = new TimetableRepository(siteRepository);
    timetableRepository.addTripPattern(SCHEDULED_PATTERN.getId(), SCHEDULED_PATTERN);
    timetableRepository.getServiceCodes().put(SERVICE_ID, SERVICE_CODE);
    var calendarServiceData = new CalendarServiceData();
    calendarServiceData.putServiceDatesForServiceId(SERVICE_ID, List.of(SERVICE_DATE));
    timetableRepository.updateCalendarServiceData(calendarServiceData);
    timetableRepository.index();

    var buffer = new TimetableSnapshot(
      RaptorTransitDataTestFactory.empty(),
      new DefaultTripCalendars()
    );
    registry = TransactionFactory.createRepositoryRegistry();
    timetableHandle = registry.registerRepository(
      buffer,
      new TimetableSnapshotLifecycle(buffer, false, () -> SERVICE_DATE)
    );
    vehicleHandle = registry.registerRepository(
      new DefaultRealtimeVehicleRepository(),
      new RealtimeVehicleRepositoryLifecycle()
    );
    var updateManager = TransactionFactory.createUpdateManagerWithAtomicCommits(
      "test",
      registry,
      Executors.defaultThreadFactory()
    );
    service = new GraphWriterService(
      updateManager,
      registry,
      timetableHandle,
      vehicleHandle,
      new Graph(),
      timetableRepository
    );
  }

  @AfterEach
  void tearDown() {
    service.stop();
  }

  @Test
  void committedTransitServiceDoesNotSeeUncommittedChanges() throws Exception {
    var uncommittedView = new AtomicReference<TripPattern>();
    var committedView = new AtomicReference<TripPattern>();

    service
      .execute(context -> {
        context.mutableSnapshot().update(modifiedPatternUpdate());
        uncommittedView.set(context.transitService().findPattern(TRIP, SERVICE_DATE));
        committedView.set(context.committedTransitService().findPattern(TRIP, SERVICE_DATE));
      })
      .get();

    assertThat(uncommittedView.get()).isSameInstanceAs(MODIFIED_PATTERN);
    assertThat(committedView.get()).isSameInstanceAs(SCHEDULED_PATTERN);
  }

  @Test
  void committedTransitServiceSeesChangesOfCommittedTransactions() throws Exception {
    service.execute(context -> context.mutableSnapshot().update(modifiedPatternUpdate())).get();

    var committedView = new AtomicReference<TripPattern>();
    service
      .execute(context ->
        committedView.set(context.committedTransitService().findPattern(TRIP, SERVICE_DATE))
      )
      .get();

    assertThat(committedView.get()).isSameInstanceAs(MODIFIED_PATTERN);
  }

  @Test
  void vehicleOnlyTaskDoesNotFreezeTheTimetableSnapshot() throws Exception {
    var timetableSnapshotBefore = timetableHandle.repositorySnapshot(registry.scope());
    var vehicleSnapshotBefore = vehicleHandle.repositorySnapshot(registry.scope());

    service
      .execute(context -> {
        // Reading the committed view must not mark the timetable repository as modified.
        context.committedTransitService().findPattern(TRIP, SERVICE_DATE);
        context
          .realtimeVehicleRepository()
          .setRealtimeVehiclesForFeed("F", ArrayListMultimap.create());
      })
      .get();

    var timetableSnapshotAfter = timetableHandle.repositorySnapshot(registry.scope());
    var vehicleSnapshotAfter = vehicleHandle.repositorySnapshot(registry.scope());

    assertThat(timetableSnapshotAfter).isSameInstanceAs(timetableSnapshotBefore);
    assertThat(vehicleSnapshotAfter).isNotSameInstanceAs(vehicleSnapshotBefore);
  }

  private static RealTimeTripUpdate modifiedPatternUpdate() {
    var realTimeTripTimes = SCHEDULED_TRIP_TIMES.createRealTimeFromScheduledTimes().build();
    return RealTimeTripUpdate.of(MODIFIED_PATTERN, realTimeTripTimes, SERVICE_DATE).build();
  }
}
