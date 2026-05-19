package org.opentripplanner.graph_builder.module.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esotericsoftware.kryo.io.Output;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentripplanner.datastore.api.FileType;
import org.opentripplanner.datastore.file.DirectoryDataSource;
import org.opentripplanner.routing.graph.kryosupport.KryoBuilder;

class GraphBuildCacheManagerTest {

  @TempDir
  File tempDir;

  private GraphBuildCacheManager newManager() {
    return new GraphBuildCacheManager(
      new GraphBuildCacheParameters(true, EnumSet.allOf(CacheTask.class)),
      new DirectoryDataSource(tempDir, FileType.UNKNOWN)
    );
  }

  @Test
  void loadReturnsNullOnCacheMiss() {
    var subject = newManager();
    assertNull(subject.load(CacheTask.ELEVATION));
    assertNull(subject.load(CacheTask.VISIBILITY));
    subject.close();
  }

  @Test
  void saveLoadRoundTrip() {
    Map<String, Double> original = new HashMap<>();
    original.put("edge-A", 42.5);
    original.put("edge-B", -3.0);

    var writer = newManager();
    writer.save(CacheTask.ELEVATION, original);
    // drain async write before reading
    writer.close();

    var reader = newManager();
    Map<String, Double> loaded = reader.load(CacheTask.ELEVATION);
    reader.close();

    assertNotNull(loaded);
    assertEquals(original, loaded);
  }

  @SuppressWarnings({ "resource", "DataFlowIssue" })
  @Test
  void loadReturnsNullOnVersionMismatch() {
    // Write a file with a mismatched version ID directly — simulates a stale cache from an
    // older OTP build. The manager must reject it and return null rather than using stale data.
    var entry = new DirectoryDataSource(tempDir, FileType.UNKNOWN).entry(
      CacheTask.ELEVATION.cacheFileName()
    );
    try (var output = new Output(entry.asOutputStream())) {
      KryoBuilder.create().writeClassAndObject(
        output,
        new CacheSerializationObject<>(999, "stale-data")
      );
    }

    var subject = newManager();
    assertNull(subject.load(CacheTask.ELEVATION));
    subject.close();
  }

  @Test
  void loadReturnsNullOnCorruptFile() throws IOException {
    var cacheFile = new File(tempDir, CacheTask.ELEVATION.cacheFileName());
    try (var out = new FileOutputStream(cacheFile)) {
      out.write(new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05 });
    }

    var subject = newManager();
    assertNull(subject.load(CacheTask.ELEVATION));
    subject.close();
  }

  @Test
  void closeWaitsForPendingWrite() {
    var subject = newManager();
    subject.save(CacheTask.VISIBILITY, Map.of("x", 1.0));
    subject.close();

    var cacheFile = new File(tempDir, CacheTask.VISIBILITY.cacheFileName());
    assertTrue(cacheFile.exists());
    assertTrue(cacheFile.length() > 0);
  }
}
