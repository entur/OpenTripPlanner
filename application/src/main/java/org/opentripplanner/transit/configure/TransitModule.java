package org.opentripplanner.transit.configure;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import java.time.LocalDate;
import org.opentripplanner.standalone.api.HttpRequestScoped;
import org.opentripplanner.standalone.config.ConfigModel;
import org.opentripplanner.transit.model.timetable.TimetableSnapshot;
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
    TimetableRepository timetableRepository
  ) {
    return new TimetableSnapshotManager(
      config.routerConfig().updaterConfig().timetableSnapshotParameters(),
      () -> LocalDate.now(timetableRepository.getTimeZone())
    );
  }

  /**
   * Provides the currently published, immutable {@link TimetableSnapshot}.
   */
  @Provides
  public static TimetableSnapshot timetableSnapshot(TimetableSnapshotManager manager) {
    return manager.getTimetableSnapshot();
  }
}
