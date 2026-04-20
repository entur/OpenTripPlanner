package org.opentripplanner.osm.model;

import java.util.HashMap;
import java.util.Map;
import org.opentripplanner.osm.OsmProvider;

public class OsmNodeBuilder {

  private long id;
  private double lat;
  private double lon;
  private final Map<String, String> tags = new HashMap<>();
  private OsmProvider osmProvider;

  public OsmNodeBuilder(OsmNode osmNode) {
    this.id = osmNode.id;
    this.lat = osmNode.lat;
    this.lon = osmNode.lon;
    this.tags.putAll(osmNode.getTags());
    this.osmProvider = osmNode.getOsmProvider();
  }

  public OsmNodeBuilder() {}

  public OsmNodeBuilder withId(long id) {
    this.id = id;
    return this;
  }

  public OsmNodeBuilder withLatLon(double lat, double lon) {
    this.lat = lat;
    this.lon = lon;
    return this;
  }

  public OsmNodeBuilder addTag(String key, String value) {
    if (key != null && value != null) {
      this.tags.put(key.toLowerCase(), value);
    }
    return this;
  }

  public OsmNodeBuilder withOsmProvider(OsmProvider osmProvider) {
    this.osmProvider = osmProvider;
    return this;
  }

  public OsmNode build() {
    return new OsmNode(id, lat, lon, tags, osmProvider);
  }
}
