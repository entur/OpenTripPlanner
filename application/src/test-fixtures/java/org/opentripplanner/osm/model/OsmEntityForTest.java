package org.opentripplanner.osm.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Mutable test version of OsmEntity for use in tests.
 * Allows adding tags after construction for convenience in test setup.
 */
public class OsmEntityForTest extends OsmEntity {

  private final Map<String, String> mutableTags = new HashMap<>();

  public OsmEntityForTest() {
    super(0, Map.of(), null, null);
  }

  @Override
  public Map<String, String> getTags() {
    return mutableTags;
  }

  /**
   * Adds a tag (test-only mutable method).
   */
  public OsmEntityForTest addTag(String key, String value) {
    if (key != null && value != null) {
      mutableTags.put(key.toLowerCase(), value);
    }
    return this;
  }
}
