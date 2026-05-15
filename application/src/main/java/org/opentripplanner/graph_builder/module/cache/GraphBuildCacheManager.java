package org.opentripplanner.graph_builder.module.cache;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import org.opentripplanner.routing.graph.kryosupport.KryoBuilder;
import org.opentripplanner.standalone.config.buildconfig.GraphBuildCacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages persistent file-based caches for expensive graph-build computations.
 * <p>
 * Each {@link CacheTask} has its own file named
 * {@code <task>-cache-<serializationVersionId>.obj}, stored in the configured directory.
 * The per-task version ID is embedded in the filename so that stale files from a previous format
 * version are ignored automatically without needing to read their contents.
 * <p>
 * Only the cache for the task currently being executed is held in memory, reducing peak memory
 * usage during large graph builds.
 */
public class GraphBuildCacheManager {

  private static final Logger LOG = LoggerFactory.getLogger(GraphBuildCacheManager.class);

  private final GraphBuildCacheConfig config;
  private final File cacheDirectory;
  private final Kryo kryo;

  public GraphBuildCacheManager(GraphBuildCacheConfig config, File baseDirectory) {
    this.config = config;
    this.cacheDirectory = config.path != null ? new File(config.path) : baseDirectory;
    this.kryo = KryoBuilder.create();
  }

  public boolean isEnabled(CacheTask task) {
    return config.isEnabled(task);
  }

  /**
   * Load the cache for the given task. Returns {@code null} on a miss (file absent, unreadable,
   * or version mismatch).
   */
  @SuppressWarnings("unchecked")
  public <T> T load(CacheTask task) {
    File file = cacheFile(task);
    if (!file.exists()) {
      LOG.info("No {} cache file found at '{}'.", task, file);
      return null;
    }
    LOG.info("Loading {} cache from '{}'.", task, file);
    try (Input input = new Input(new FileInputStream(file))) {
      var wrapper = (CacheSerializationObject<T>) kryo.readClassAndObject(input);
      if (wrapper.serializationVersionId != task.serializationVersionId) {
        LOG.warn(
          "Ignoring {} cache at '{}': version mismatch (file={}, code={}).",
          task,
          file,
          wrapper.serializationVersionId,
          task.serializationVersionId
        );
        return null;
      }
      LOG.info("Loaded {} cache from '{}'.", task, file);
      return wrapper.data;
    } catch (KryoException | IOException | ClassCastException e) {
      LOG.warn(
        "Failed to load {} cache from '{}': {}. Starting with empty cache.",
        task,
        file,
        e.getMessage()
      );
      return null;
    }
  }

  /**
   * Save the cache for the given task, overwriting any existing file.
   */
  public <T> void save(CacheTask task, T data) {
    File file = cacheFile(task);
    LOG.info("Saving {} cache to '{}'.", task, file);
    try (Output output = new Output(new FileOutputStream(file))) {
      kryo.writeClassAndObject(
        output,
        new CacheSerializationObject<>(task.serializationVersionId, data)
      );
    } catch (KryoException | IOException e) {
      LOG.warn("Failed to save {} cache to '{}': {}.", task, file, e.getMessage());
    }
  }

  private File cacheFile(CacheTask task) {
    return new File(cacheDirectory, task.cacheFileName());
  }
}
