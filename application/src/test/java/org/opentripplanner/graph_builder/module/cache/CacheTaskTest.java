package org.opentripplanner.graph_builder.module.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CacheTaskTest {

  @Test
  void cacheFileName_followsNamingConvention() {
    assertEquals("elevation-cache-1.obj", CacheTask.ELEVATION.cacheFileName());
    assertEquals("visibility-cache-2.obj", CacheTask.VISIBILITY.cacheFileName());
  }
}
