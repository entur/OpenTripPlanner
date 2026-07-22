package org.opentripplanner.updater;

import java.util.concurrent.Future;
import java.util.function.Function;
import org.opentripplanner.framework.transaction.UpdateManager;
import org.opentripplanner.framework.transaction.api.RepositoryHandle;
import org.opentripplanner.framework.transaction.api.WriteContext;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.transit.repository.MutableTimetableSnapshot;
import org.opentripplanner.transit.repository.ReadOnlyTimetableSnapshot;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.updater.spi.WriteToGraphCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serialises the graph write operations of one write domain by delegating to that domain's
 * {@link UpdateManager}, which owns the single-threaded executor. Each write task receives a
 * {@link RealTimeUpdateContext} appropriate for the domain: transit tasks get access to the
 * mutable timetable snapshot, street tasks get access to the street model only.
 * <p>
 * This class will eventually be removed once all updaters submit directly to {@link UpdateManager}.
 */
public class GraphWriterService implements WriteToGraphCallback {

  private static final Logger LOG = LoggerFactory.getLogger(GraphWriterService.class);

  private final UpdateManager updateManager;
  private final Function<WriteContext, RealTimeUpdateContext> contextFactory;

  private GraphWriterService(
    UpdateManager updateManager,
    Function<WriteContext, RealTimeUpdateContext> contextFactory
  ) {
    this.updateManager = updateManager;
    this.contextFactory = contextFactory;
  }

  /**
   * Create the bridge for the transit write domain. Each task checks out the mutable timetable
   * snapshot for the current transaction.
   */
  public static GraphWriterService forTransitDomain(
    UpdateManager updateManager,
    RepositoryHandle<ReadOnlyTimetableSnapshot, MutableTimetableSnapshot> timetableHandle,
    Graph graph,
    TimetableRepository timetableRepository
  ) {
    return new GraphWriterService(updateManager, ctx ->
      new DefaultRealTimeUpdateContext(graph, timetableRepository, ctx.repository(timetableHandle))
    );
  }

  /**
   * Create the bridge for the street write domain. Tasks get access to the street model only —
   * the mutable timetable snapshot belongs to the transit domain's writer thread and must not be
   * touched from the street writer thread.
   */
  public static GraphWriterService forStreetDomain(UpdateManager updateManager, Graph graph) {
    var context = new StreetRealTimeUpdateContext(graph);
    return new GraphWriterService(updateManager, ctx -> context);
  }

  @Override
  public Future<Void> execute(GraphWriterRunnable runnable) {
    return updateManager.submit(ctx -> {
      var context = contextFactory.apply(ctx);
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
