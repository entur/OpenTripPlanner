package org.opentripplanner.standalone.server.warmup;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.standalone.config.routerconfig.WarmupConfig;
import org.opentripplanner.updater.GraphUpdaterStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs GraphQL trip queries in a background thread during OTP startup to warm up the
 * application before production traffic arrives.
 * <p>
 * The worker sends sequential queries through the configured GraphQL API (TransModel or GTFS),
 * exercising the full stack: GraphQL parsing, data fetchers, routing (Raptor + A*), itinerary
 * filtering, and response serialization. This warms up JIT compilation, GraphQL schema caches,
 * routing data structures, and other lazily initialized components. It alternates between
 * depart-at / arrive-by and cycles through access/egress modes (walk, bike, car-to-park).
 * <p>
 * It starts after Raptor transit data is created and stops when the health probe
 * reports "UP" (all updaters primed).
 */
public class WarmupWorker implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(WarmupWorker.class);

  private final WarmupConfig config;
  private final WarmupQueryExecutor queryExecutor;
  private final Supplier<OtpServerRequestContext> serverContextProvider;
  private final Supplier<GraphUpdaterStatus> updaterStatusProvider;

  WarmupWorker(
    WarmupConfig config,
    Supplier<OtpServerRequestContext> serverContextProvider,
    Supplier<GraphUpdaterStatus> updaterStatusProvider
  ) {
    this.config = config;
    this.serverContextProvider = serverContextProvider;
    this.updaterStatusProvider = updaterStatusProvider;
    this.queryExecutor = switch (config.api()) {
      case TRANSMODEL -> new TransmodelWarmupQueryExecutor();
      case GTFS -> new GtfsWarmupQueryExecutor();
    };
  }

  /**
   * Start the application warmup thread if configured and applicable.
   * <p>
   * No warmup is started if the config is null (section absent in router-config.json)
   * or if no updaters are configured (health probe would immediately return "UP").
   */
  public static void start(
    @Nullable WarmupConfig config,
    Supplier<OtpServerRequestContext> serverContextProvider,
    Supplier<GraphUpdaterStatus> updaterStatusProvider
  ) {
    if (config == null) {
      return;
    }
    if (updaterStatusProvider.get() == null) {
      LOG.info("Application warmup configured but no updaters found. Skipping warmup.");
      return;
    }
    var worker = new WarmupWorker(config, serverContextProvider, updaterStatusProvider);
    var thread = new Thread(worker, "app-warmup");
    thread.setDaemon(true);
    thread.start();
  }

  @Override
  public void run() {
    LOG.info(
      "Application warmup started. Sending {} GraphQL trip queries from {} to {}.",
      config.api(),
      config.from(),
      config.to()
    );

    int queryCount = 0;

    while (!Thread.currentThread().isInterrupted()) {
      if (isHealthy()) {
        LOG.info("Application warmup complete after {} queries. All updaters primed.", queryCount);
        return;
      }

      queryCount++;
      boolean arriveBy = queryCount % 2 == 0;
      int modeIndex = queryCount % queryExecutor.modeCombinationCount();
      var queryStart = Instant.now();

      try {
        queryExecutor.execute(
          serverContextProvider.get(),
          config.from(),
          config.to(),
          arriveBy,
          modeIndex
        );
        var elapsed = Duration.between(queryStart, Instant.now());
        LOG.info("Warmup query #{} completed in {} ms.", queryCount, elapsed.toMillis());
      } catch (Exception e) {
        var elapsed = Duration.between(queryStart, Instant.now());
        LOG.info(
          "Warmup query #{} failed in {} ms: {}",
          queryCount,
          elapsed.toMillis(),
          e.getMessage()
        );
      }
    }

    LOG.info("Application warmup interrupted after {} queries.", queryCount);
  }

  private boolean isHealthy() {
    var status = updaterStatusProvider.get();
    return status == null || status.listUnprimedUpdaters().isEmpty();
  }
}
