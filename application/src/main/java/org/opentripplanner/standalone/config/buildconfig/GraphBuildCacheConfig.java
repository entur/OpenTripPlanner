package org.opentripplanner.standalone.config.buildconfig;

import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_10;

import java.net.URI;
import java.util.EnumSet;
import java.util.Set;
import javax.annotation.Nullable;
import org.opentripplanner.graph_builder.module.cache.CacheTask;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;

/**
 * Configuration for the graph-build file cache ({@code cache} section of
 * {@code build-config.json}).
 */
public class GraphBuildCacheConfig {

  /** Disabled cache — used as a safe no-op default when no config is available (e.g. tests). */
  public static final GraphBuildCacheConfig DEFAULT = new GraphBuildCacheConfig(
    false,
    null,
    EnumSet.allOf(CacheTask.class)
  );

  public final boolean enabled;

  /**
   * Root directory for cache files. {@code null} means "use the OTP base directory", resolved at
   * runtime via {@link org.opentripplanner.graph_builder.GraphBuilderDataSources#getCacheDir}.
   */
  @Nullable
  public final URI path;

  /** Which tasks are enabled. Defaults to all known tasks when not set in config. */
  public final Set<CacheTask> tasks;

  private GraphBuildCacheConfig(boolean enabled, @Nullable URI path, Set<CacheTask> tasks) {
    this.enabled = enabled;
    this.path = path;
    this.tasks = Set.copyOf(tasks);
  }

  public static GraphBuildCacheConfig fromConfig(NodeAdapter root) {
    return fromSubConfig(
      root
        .of("cache")
        .since(V2_10)
        .summary("Configuration for the graph-build file cache.")
        .description(
          """
          OTP can cache the results of expensive graph-build computations (elevation lookups,
          OSM area visibility graphs) between builds. Caching is disabled by default; set
          `enabled: true` to turn it on. Cache files are stored next to the other build outputs
          in the OTP base directory by default.
          """
        )
        .asObject()
    );
  }

  public static GraphBuildCacheConfig fromSubConfig(NodeAdapter c) {
    var enabled = c
      .of("enabled")
      .since(V2_10)
      .summary("Master switch for the graph-build cache.")
      .description("When `false` no cache files are read or written during graph builds.")
      .asBoolean(false);

    var path = c
      .of("path")
      .since(V2_10)
      .summary("Root directory for cache files.")
      .description(
        """
        Path to the directory where cache files are stored. Defaults to the OTP base directory
        (the directory containing `build-config.json`) when not set.
        """
      )
      .asUri(null);

    var tasks = c
      .of("tasks")
      .since(V2_10)
      .summary("Which cache tasks to enable.")
      .description(
        """
        List of cache tasks to enable. Valid values are `ELEVATION` and `VISIBILITY`. When not
        set, all tasks are enabled. To disable a specific task while keeping others, list only
        the tasks you want.
        """
      )
      .asEnumSet(CacheTask.class, EnumSet.allOf(CacheTask.class));

    return new GraphBuildCacheConfig(enabled, path, tasks);
  }

  public boolean isEnabled(CacheTask task) {
    return enabled && tasks.contains(task);
  }
}
