package org.opentripplanner.osm.model;

import java.util.Map;

/**
 * Test version of OsmEntity for use in tests.
 * All tags must be passed into the constructor.
 */
public class OsmEntityForTest extends OsmEntity {

  public OsmEntityForTest() {
    this(Map.of());
  }

  public OsmEntityForTest(Map<String, String> tags) {
    super(0, tags, null, null);
  }

  public OsmEntityForTest(String key, String value) {
    this(Map.of(key, value));
  }

  @SafeVarargs
  public OsmEntityForTest(Map.Entry<String, String>... entries) {
    this(Map.ofEntries(entries));
  }
}
