package org.opentripplanner.updater;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.opentripplanner.updater.spi.WriteToGraphCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the single-threaded scheduler that serialises all graph write operations, preventing
 * concurrent mutation of real-time data. Implements {@link WriteToGraphCallback} so that
 * {@link org.opentripplanner.updater.spi.GraphUpdater} instances can submit write tasks without
 * holding a reference to the broader {@link GraphUpdaterManager}.
 * <p>
 * This class will be replaced by the new {@link org.opentripplanner.framework.transaction.UpdateManager}
 * framework, which provides the same serialised write semantics with a transactional
 * copy-on-write model.
 */
public class GraphWriterService implements WriteToGraphCallback {

  private static final Logger LOG = LoggerFactory.getLogger(GraphWriterService.class);

  /**
   * Single writer thread. All graph write tasks are submitted here in FIFO order, ensuring no two
   * writes execute concurrently.
   */
  private final ScheduledExecutorService scheduler;

  private final RealTimeUpdateContext realtimeUpdateContext;

  public GraphWriterService(RealTimeUpdateContext realtimeUpdateContext) {
    this.realtimeUpdateContext = realtimeUpdateContext;
    var threadFactory = new ThreadFactoryBuilder().setNameFormat("graph-writer").build();
    this.scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);
  }

  @Override
  public Future<?> execute(GraphWriterRunnable runnable) {
    return scheduler.submit(() -> {
      try {
        runnable.run(realtimeUpdateContext);
      } catch (Exception e) {
        LOG.error("Error while running graph writer {}:", runnable.getClass().getName(), e);
      }
    });
  }

  /**
   * Expose the scheduler so that periodic tasks (e.g. timetable snapshot flush) can be
   * scheduled on the same writer thread.
   */
  public ScheduledExecutorService getScheduler() {
    return scheduler;
  }

  public void stop() {
    scheduler.shutdownNow();
    try {
      boolean ok = scheduler.awaitTermination(30, TimeUnit.SECONDS);
      if (!ok) {
        LOG.warn("Timeout waiting for graph writer scheduler to finish.");
      }
    } catch (InterruptedException e) {
      LOG.warn("Interrupted while waiting for graph writer scheduler to finish.");
    }
  }
}
