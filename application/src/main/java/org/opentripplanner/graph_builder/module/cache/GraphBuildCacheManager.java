package org.opentripplanner.graph_builder.module.cache;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.opentripplanner.datastore.api.CompositeDataSource;
import org.opentripplanner.datastore.api.DataSource;
import org.opentripplanner.routing.graph.kryosupport.KryoBuilder;
import org.opentripplanner.standalone.config.buildconfig.GraphBuildCacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages persistent caches for expensive graph-build computations, backed by a
 * {@link CompositeDataSource} so it works with both local filesystems and cloud storage (e.g. GCP).
 * <p>
 * Each {@link CacheTask} has its own entry named
 * {@code <task>-cache-<serializationVersionId>.obj} inside the composite data source.
 * The per-task version ID is embedded in the entry name so that stale entries from a previous
 * format version are ignored automatically without needing to read their contents.
 * <p>
 * Only the cache for the task currently being executed is held in memory, reducing peak memory
 * usage during large graph builds.
 */
public class GraphBuildCacheManager {

  /** Disabled no-op instance — safe to use as a default when no real cache is needed. */
  public static final GraphBuildCacheManager NOOP = new GraphBuildCacheManager(
    GraphBuildCacheConfig.DEFAULT,
    null
  );

  private static final Logger LOG = LoggerFactory.getLogger(GraphBuildCacheManager.class);

  private final GraphBuildCacheConfig config;
  private final CompositeDataSource cacheDir;
  private final Kryo kryo;

  public GraphBuildCacheManager(GraphBuildCacheConfig config, CompositeDataSource cacheDir) {
    this.config = config;
    this.cacheDir = cacheDir;
    this.kryo = KryoBuilder.create();
  }

  public boolean isEnabled(CacheTask task) {
    return config.isEnabled(task);
  }

  /**
   * Load the cache for the given task. Returns {@code null} on a miss (entry absent, unreadable,
   * or version mismatch).
   */
  @SuppressWarnings("unchecked")
  public <T> T load(CacheTask task) {
    DataSource entry = cacheDir.entry(task.cacheFileName());
    if (!entry.exists()) {
      LOG.info("No {} cache file found at '{}'.", task, entry.path());
      return null;
    }
    LOG.info("Loading {} cache from '{}'.", task, entry.path());
    try (Input input = new Input(entry.asInputStream())) {
      var wrapper = (CacheSerializationObject<T>) kryo.readClassAndObject(input);
      if (wrapper.serializationVersionId != task.serializationVersionId) {
        LOG.warn(
          "Ignoring {} cache at '{}': version mismatch (file={}, code={}).",
          task,
          entry.path(),
          wrapper.serializationVersionId,
          task.serializationVersionId
        );
        return null;
      }
      LOG.info("Loaded {} cache from '{}'.", task, entry.path());
      return wrapper.data;
    } catch (KryoException | ClassCastException e) {
      LOG.warn(
        "Failed to load {} cache from '{}': {}. Starting with empty cache.",
        task,
        entry.path(),
        e.getMessage()
      );
      return null;
    }
  }

  /**
   * Save the cache for the given task, overwriting any existing entry.
   */
  public <T> void save(CacheTask task, T data) {
    DataSource entry = cacheDir.entry(task.cacheFileName());
    LOG.info("Saving {} cache to '{}'.", task, entry.path());
    try (Output output = new Output(entry.asOutputStream())) {
      kryo.writeClassAndObject(
        output,
        new CacheSerializationObject<>(task.serializationVersionId, data)
      );
    } catch (KryoException e) {
      LOG.warn("Failed to save {} cache to '{}': {}.", task, entry.path(), e.getMessage());
    }
  }
}
