package org.opentripplanner.transit.configure;

import java.time.LocalDate;
import javax.annotation.Nullable;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RaptorTransitData;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.mappers.RealTimeRaptorTransitDataUpdater;
import org.opentripplanner.standalone.config.ConfigModel;
import org.opentripplanner.transit.model.timetable.TimetableSnapshot;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration(proxyBeanMethods = false)
public class TransitModule {

  /**
   * The transit service was an unscoped {@code @Binds} in Dagger (created fresh per injection),
   * and it captures the currently-published {@link TimetableSnapshot} at construction time. It must
   * therefore be {@code prototype}-scoped so each consumer (e.g. the per-request
   * {@code OtpServerRequestContext}) receives a service reflecting the latest real-time snapshot,
   * not a stale snapshot frozen at first construction.
   */
  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  TransitService transitService(
    TimetableRepository timetableRepository,
    @Nullable TimetableSnapshot timetableSnapshot
  ) {
    return new DefaultTransitService(timetableRepository, timetableSnapshot);
  }

  @Bean
  public TimetableSnapshotManager timetableSnapshotManager(
    RealTimeRaptorTransitDataUpdater realtimeRaptorTransitDataUpdater,
    ConfigModel config,
    TimetableRepository timetableRepository
  ) {
    return new TimetableSnapshotManager(
      realtimeRaptorTransitDataUpdater,
      config.routerConfig().updaterConfig().timetableSnapshotParameters(),
      () -> LocalDate.now(timetableRepository.getTimeZone())
    );
  }

  /**
   * Create a single instance of the transit layer updater which holds the incremental caches for
   * the updates that need to applied to the {@link RaptorTransitData}.
   */
  @Bean
  public RealTimeRaptorTransitDataUpdater realtimeRaptorTransitDataUpdater(
    TimetableRepository timetableRepository
  ) {
    return new RealTimeRaptorTransitDataUpdater(timetableRepository);
  }

  /**
   * Provides the currently published, immutable {@link TimetableSnapshot}.
   */
  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public TimetableSnapshot timetableSnapshot(TimetableSnapshotManager manager) {
    return manager.getTimetableSnapshot();
  }
}
