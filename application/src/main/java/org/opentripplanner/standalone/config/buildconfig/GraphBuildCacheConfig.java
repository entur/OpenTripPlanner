package org.opentripplanner.standalone.config.buildconfig;

import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_10;

import java.net.URI;
import java.util.EnumSet;
import java.util.Set;
import javax.annotation.Nullable;
import org.opentripplanner.graph_builder.module.cache.CacheTask;
import org.opentripplanner.graph_builder.module.cache.GraphBuildCacheParameters;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;

/**
 * Configuration for the graph-build file cache ({@code cache} section of
 * {@code build-config.json}).
 */
public class GraphBuildCacheConfig {

  public final boolean enabled;

  /**
   * Root directory for cache files. {@code null} means "use the OTP base directory", resolved at
   * runtime via {@link org.opentripplanner.graph_builder.GraphBuilderDataSources#getCacheDirectory(URI)}.
   */
  @Nullable
  private final URI path;

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
          OTP can cache the results of expensive graph-build computations between builds. The two
          cached tasks are:

          - **ELEVATION** – elevation profiles sampled from the DEM for each street edge.
          - **VISIBILITY** – pre-computed visibility graphs for walkable OSM areas (e.g. squares
            and parks).

          Both tasks can take a significant portion of total graph-build time. Enabling the cache
          skips their computation on subsequent builds and can cut build time considerably when
          the same OSM and DEM files are reused.

          **When to enable:** any pipeline that rebuilds the graph repeatedly from the same OSM
          and elevation data (e.g. nightly GTFS-only updates).

          **Cache invalidation:** the two tasks have different staleness characteristics:

          - *ELEVATION* – the cache key is the encoded geometry of each street edge, and on save
            only edges present in the current build are written. OSM changes therefore take care of
            themselves: new or modified edges produce a cache miss and are recomputed; removed edges
            simply disappear from the next save. **Delete the elevation cache when the DEM source
            file is replaced**, because edge geometries are unchanged but the sampled height values
            would be stale.
          - *VISIBILITY* – the cache key is a hash of both the OSM entity IDs and all polygon
            coordinates of an area group. Any geometry change therefore produces a cache miss and
            triggers a recompute automatically. Deleted areas leave orphaned entries that are never
            looked up (harmless bloat). **The visibility cache never needs to be deleted manually**;
            clearing it only removes accumulated bloat from deleted areas.

          Cache files are named `<task>-cache-<version>.obj` (for example `elevation-cache-1.obj`).
          When OTP bumps the internal serialization version the old file is ignored automatically
          and a new one is written, so old versioned files can be deleted safely at any time.

          Caching is **disabled by default**. Set `enabled: true` to activate it.
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
        List of cache tasks to enable. Valid values:

        - `ELEVATION` – caches the elevation profile of every street edge. OSM changes are handled
          automatically (the cache key is the edge geometry; on save only the current build's edges
          are written). Delete the cache when the DEM source file is replaced.
        - `VISIBILITY` – caches pre-computed visibility graphs for walkable OSM areas. The cache
          key includes the full polygon geometry, so OSM changes automatically cause a cache miss
          for affected areas. This cache never needs to be deleted manually.

        When not set, all tasks are enabled. Omit a task from the list to skip its cache while
        keeping the others active.
        """
      )
      .asEnumSet(CacheTask.class, EnumSet.allOf(CacheTask.class));

    return new GraphBuildCacheConfig(enabled, path, tasks);
  }

  @Nullable
  public URI path() {
    return path;
  }

  public GraphBuildCacheParameters toParameters() {
    return new GraphBuildCacheParameters(enabled, tasks);
  }
}
