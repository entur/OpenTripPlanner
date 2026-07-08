package org.opentripplanner.transit.configure;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import java.time.LocalDate;
import org.opentripplanner.framework.transaction.RepositoryRegistry;
import org.opentripplanner.framework.transaction.TimetableSnapshotParameters;
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

@Module
public abstract class TransitModule {

  @Binds
  @HttpRequestScoped
  abstract TransitService bind(DefaultTransitService service);

  @Provides
  @Singleton
  public static TimetableSnapshotParameters timetableSnapshotParameters(ConfigModel config) {
    return config.routerConfig().updaterConfig().timetableSnapshotParameters();
  }

  /**
   * Provides the currently published, immutable timetable snapshot for injection points outside the
   * HTTP request context (e.g. {@link org.opentripplanner.service.realtimevehicles.internal.DefaultRealtimeVehicleService}).
   * For request-scoped access, {@link org.opentripplanner.standalone.configure.ConstructApplicationModule}
   * uses a pinned {@link org.opentripplanner.framework.transaction.api.TransactionScope} instead.
   */
  @Provides
  public static ReadOnlyTimetableSnapshot timetableSnapshot(
    RepositoryRegistry repositoryRegistry,
    RepositoryHandle<ReadOnlyTimetableSnapshot, MutableTimetableSnapshot> timetableRepositoryHandle
  ) {
    return timetableRepositoryHandle.repositorySnapshot(repositoryRegistry.scope());
  }

  @Provides
  @Singleton
  public static RepositoryHandle<
    ReadOnlyTimetableSnapshot,
    MutableTimetableSnapshot
  > timetableRepositoryHandle(
    TimetableSnapshotParameters parameters,
    TimetableRepository timetableRepository,
    RepositoryRegistry repositoryRegistry,
    RaptorTransitData scheduledRaptorTransitData,
    DefaultTripCalendars tripCalendars
  ) {
    var timetableSnapshot = new TimetableSnapshot(scheduledRaptorTransitData, tripCalendars);
    var timetableSnapshotLifecycle = new TimetableSnapshotLifecycle(
      timetableSnapshot,
      parameters.purgeExpiredData(),
      () -> LocalDate.now(timetableRepository.getTimeZone())
    );
    return repositoryRegistry.registerRepositorySnapshot(
      timetableSnapshot,
      timetableSnapshotLifecycle
    );
  }
}
