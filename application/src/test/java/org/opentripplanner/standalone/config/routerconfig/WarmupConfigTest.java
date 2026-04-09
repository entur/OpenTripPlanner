package org.opentripplanner.standalone.config.routerconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.opentripplanner.standalone.config.framework.json.JsonSupport.jsonNodeForTest;

import org.junit.jupiter.api.Test;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;

class WarmupConfigTest {

  @Test
  void absentSectionReturnsNull() {
    var root = createNodeAdapter("{}");
    var config = WarmupConfig.mapWarmupConfig("warmup", root);
    assertNull(config);
  }

  @Test
  void parsesCoordinates() {
    var root = createNodeAdapter(
      """
      {
        warmup: {
          from: { lat: 59.9139, lon: 10.7522 },
          to: { lat: 59.95, lon: 10.76 }
        }
      }
      """
    );
    var config = WarmupConfig.mapWarmupConfig("warmup", root);

    assertNotNull(config);
    assertEquals(59.9139, config.from().latitude());
    assertEquals(10.7522, config.from().longitude());
    assertEquals(59.95, config.to().latitude());
    assertEquals(10.76, config.to().longitude());
  }

  @Test
  void defaultApiIsTransmodel() {
    var root = createNodeAdapter(
      """
      {
        warmup: {
          from: { lat: 59.91, lon: 10.75 },
          to: { lat: 59.95, lon: 10.76 }
        }
      }
      """
    );
    var config = WarmupConfig.mapWarmupConfig("warmup", root);
    assertNotNull(config);
    assertEquals(WarmupConfig.Api.TRANSMODEL, config.api());
  }

  @Test
  void parsesGtfsApi() {
    var root = createNodeAdapter(
      """
      {
        warmup: {
          api: "gtfs",
          from: { lat: 59.91, lon: 10.75 },
          to: { lat: 59.95, lon: 10.76 }
        }
      }
      """
    );
    var config = WarmupConfig.mapWarmupConfig("warmup", root);
    assertNotNull(config);
    assertEquals(WarmupConfig.Api.GTFS, config.api());
  }

  private static NodeAdapter createNodeAdapter(String jsonText) {
    return new NodeAdapter(jsonNodeForTest(jsonText), "Test");
  }
}
