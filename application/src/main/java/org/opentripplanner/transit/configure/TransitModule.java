package org.opentripplanner.transit.configure;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import java.time.LocalDate;
import org.opentripplanner.framework.transaction.RepositoryRegistry;
import org.opentripplanner.framework.transaction.api.RepositoryHandle;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RaptorTransitData;
import org.opentripplanner.standalone.api.HttpRequestScoped;
import org.opentripplanner.standalone.config.ConfigModel;
import org.opentripplanner.transit.model.calendar.DefaultTripCalendars;
import org.opentripplanner.transit.model.timetable.TimetableSnapshot;
import org.opentripplanner.transit.repository.MutableTimetableSnapshot;
import org.opentripplanner.transit.repository.ReadOnlyTimetableSnapshot;
import org.opentripplanner.transit.repository.TimetableSnapshotLifecycle;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;

@Module
public abstract class TransitModule {

  @Binds
  @HttpRequestScoped
  abstract TransitService bind(DefaultTransitService service);

  @Provides
  @Singleton
  public static TimetableSnapshotManager timetableSnapshotManager(
    ConfigModel config,
    TimetableRepository timetableRepository,
    RaptorTransitData scheduledRaptorTransitData,
    DefaultTripCalendars scheduledTripCalendars
  ) {
    return new TimetableSnapshotManager(
      config.routerConfig().updaterConfig().timetableSnapshotParameters(),
      () -> LocalDate.now(timetableRepository.getTimeZone()),
      scheduledRaptorTransitData,
      scheduledTripCalendars
    );
  }

  /**
   * Provides the currently published, immutable {@link TimetableSnapshot}.
   */
  @Provides
  public static TimetableSnapshot timetableSnapshot(TimetableSnapshotManager manager) {
    return manager.getTimetableSnapshot();
  }

  @Provides
  @Singleton
  public static RepositoryHandle<
    ReadOnlyTimetableSnapshot,
    MutableTimetableSnapshot
  > timetableRepositoryHandle(
    RepositoryRegistry repositoryRegistry,
    RaptorTransitData scheduledRaptorTransitData,
    DefaultTripCalendars tripCalendars
  ) {
    var timetableSnapshot = new TimetableSnapshot(scheduledRaptorTransitData, tripCalendars);
    var timetableSnapshotLifecycle = new TimetableSnapshotLifecycle(timetableSnapshot);
    return repositoryRegistry.registerRepositorySnapshot(
      timetableSnapshot,
      timetableSnapshotLifecycle
    );
  }
}
