package org.opentripplanner.updater;

import java.util.concurrent.Future;
import org.opentripplanner.framework.transaction.RepositoryRegistry;
import org.opentripplanner.framework.transaction.UpdateManager;
import org.opentripplanner.framework.transaction.api.RepositoryHandle;
import org.opentripplanner.service.realtimevehicles.RealtimeVehicleRepository;
import org.opentripplanner.service.realtimevehicles.RealtimeVehicleRepositorySnapshot;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.transit.repository.MutableTimetableSnapshot;
import org.opentripplanner.transit.repository.ReadOnlyTimetableSnapshot;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.updater.spi.WriteToGraphCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serialises all graph write operations by delegating to {@link UpdateManager}, which owns the
 * single-threaded executor. Each write task receives a freshly-constructed
 * {@link DefaultRealTimeUpdateContext} that resolves the mutable timetable snapshot, the last
 * committed timetable snapshot and the mutable realtime-vehicle repository on demand.
 * <p>
 * This class will eventually be removed once all updaters submit directly to {@link UpdateManager}.
 */
public class GraphWriterService implements WriteToGraphCallback {

  private static final Logger LOG = LoggerFactory.getLogger(GraphWriterService.class);

  private final UpdateManager updateManager;
  private final RepositoryRegistry repositoryRegistry;
  private final RepositoryHandle<
    ReadOnlyTimetableSnapshot,
    MutableTimetableSnapshot
  > timetableHandle;
  private final RepositoryHandle<
    RealtimeVehicleRepositorySnapshot,
    RealtimeVehicleRepository
  > realtimeVehicleHandle;
  private final Graph graph;
  private final TimetableRepository timetableRepository;

  public GraphWriterService(
    UpdateManager updateManager,
    RepositoryRegistry repositoryRegistry,
    RepositoryHandle<ReadOnlyTimetableSnapshot, MutableTimetableSnapshot> timetableHandle,
    RepositoryHandle<
      RealtimeVehicleRepositorySnapshot,
      RealtimeVehicleRepository
    > realtimeVehicleHandle,
    Graph graph,
    TimetableRepository timetableRepository
  ) {
    this.updateManager = updateManager;
    this.repositoryRegistry = repositoryRegistry;
    this.timetableHandle = timetableHandle;
    this.realtimeVehicleHandle = realtimeVehicleHandle;
    this.graph = graph;
    this.timetableRepository = timetableRepository;
  }

  @Override
  public Future<Void> execute(GraphWriterRunnable runnable) {
    return updateManager.submit(ctx -> {
      // All repositories are resolved lazily: only tasks that actually write to the timetable
      // or the realtime vehicles mark the corresponding repository as modified in the
      // transaction. The committed snapshot is resolved through a fresh scope, which captures
      // the state of the last commit without marking the timetable repository as modified.
      var context = new DefaultRealTimeUpdateContext(
        graph,
        timetableRepository,
        () -> ctx.repository(timetableHandle),
        () -> timetableHandle.repositorySnapshot(repositoryRegistry.scope()),
        () -> ctx.repository(realtimeVehicleHandle)
      );
      try {
        runnable.run(context);
      } catch (Exception e) {
        LOG.error("Error while running graph writer {}:", runnable.getClass().getName(), e);
      }
    });
  }

  public void stop() {
    updateManager.shutdown();
  }
}
