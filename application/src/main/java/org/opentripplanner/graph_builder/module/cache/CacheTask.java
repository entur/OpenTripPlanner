package org.opentripplanner.graph_builder.module.cache;

/**
 * Identifies a graph-build computation that can be persisted to a file cache between builds.
 * Each task carries its own serialization version ID, maintained manually in code and independent
 * of the global OTP serialization version. Bump the ID when the cache file format changes for
 * that specific task.
 */
public enum CacheTask {
  ELEVATION(1),
  VISIBILITY(1);

  /**
   * Per-task cache format version. Increment when the serialized data structure changes so that
   * stale files are ignored automatically (the version is part of the filename).
   */
  public final int serializationVersionId;

  CacheTask(int serializationVersionId) {
    this.serializationVersionId = serializationVersionId;
  }

  public String cacheFileName() {
    return name().toLowerCase() + "-cache-" + serializationVersionId + ".obj";
  }

  public static CacheTask matchName(String name) {
    for (var it : values()) {
      if (it.cacheFileName().equals(name)) {
        return it;
      }
    }
    return null;
  }
}
