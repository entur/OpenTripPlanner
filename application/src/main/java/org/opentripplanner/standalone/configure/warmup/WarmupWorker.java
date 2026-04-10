package org.opentripplanner.standalone.configure.warmup;

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
  private static final int MAX_QUERIES = 20;

  private final WarmupConfig config;
  private final WarmupQueryExecutor queryExecutor;
  private final Supplier<GraphUpdaterStatus> updaterStatusProvider;

  WarmupWorker(
    WarmupConfig config,
    OtpServerRequestContext serverContext,
    Supplier<GraphUpdaterStatus> updaterStatusProvider
  ) {
    this.config = config;
    this.updaterStatusProvider = updaterStatusProvider;
    this.queryExecutor = switch (config.api()) {
      case TRANSMODEL -> new TransmodelWarmupQueryExecutor(serverContext);
      case GTFS -> new GtfsWarmupQueryExecutor(serverContext);
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
    var serverContext = serverContextProvider.get();
    var schema = switch (config.api()) {
      case TRANSMODEL -> serverContext.transmodelSchema();
      case GTFS -> serverContext.gtfsSchema();
    };
    if (schema == null) {
      LOG.warn(
        "Application warmup configured for {} API, but the schema is not available. " +
          "Is the corresponding API feature enabled?",
        config.api()
      );
      return;
    }
    var worker = new WarmupWorker(config, serverContext, updaterStatusProvider);
    var thread = new Thread(worker, "app-warmup");
    thread.setDaemon(true);
    thread.start();
    LOG.info("Application warmup thread started.");
  }

  @Override
  public void run() {
    LOG.info(
      "Application warmup started. Sending {} GraphQL trip queries from {} to {}.",
      config.api(),
      config.from(),
      config.to()
    );

    var startTime = Instant.now();
    int queryCount = 0;
    int failureCount = 0;

    try {
      while (queryCount < MAX_QUERIES) {
        if (isHealthy()) {
          LOG.info(
            "Application warmup complete: {} queries ({} failures) in {} ms. All updaters primed.",
            queryCount,
            failureCount,
            Duration.between(startTime, Instant.now()).toMillis()
          );
          return;
        }

        queryCount++;
        boolean arriveBy = queryCount % 2 == 0;
        int modeIndex = queryCount % queryExecutor.modeCombinationCount();
        if (!executeQuery(queryCount, arriveBy, modeIndex)) {
          failureCount++;
        }
      }

      LOG.info(
        "Application warmup reached maximum of {} queries ({} failures) in {} ms" +
          " before all updaters were primed.",
        MAX_QUERIES,
        failureCount,
        Duration.between(startTime, Instant.now()).toMillis()
      );
    } catch (Throwable e) {
      LOG.error("Application warmup terminated by error after {} queries.", queryCount, e);
    }
  }

  /** @return true if the query succeeded without errors, false otherwise. */
  private boolean executeQuery(int queryCount, boolean arriveBy, int modeIndex) {
    var queryStart = Instant.now();
    try {
      boolean success = queryExecutor.execute(config.from(), config.to(), arriveBy, modeIndex);
      var elapsed = Duration.between(queryStart, Instant.now());
      LOG.info("Warmup query #{} completed in {} ms.", queryCount, elapsed.toMillis());
      return success;
    } catch (Exception e) {
      var elapsed = Duration.between(queryStart, Instant.now());
      LOG.info(
        "Warmup query #{} failed in {} ms: {}",
        queryCount,
        elapsed.toMillis(),
        e.getMessage()
      );
      LOG.debug("Warmup query #{} exception detail", queryCount, e);
      return false;
    }
  }

  private boolean isHealthy() {
    var status = updaterStatusProvider.get();
    return status == null || status.listUnprimedUpdaters().isEmpty();
  }
}
