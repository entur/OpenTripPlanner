package org.opentripplanner.osm.model;

import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import java.util.HashMap;
import java.util.Map;
import org.opentripplanner.osm.OsmProvider;

public class OsmWayBuilder {

  private long id = -999;
  private final Map<String, String> tags = new HashMap<>();
  private OsmProvider osmProvider;
  private final TLongList nodes = new TLongArrayList();

  public OsmWayBuilder() {}

  public OsmWayBuilder(OsmWay way) {
    this.id = way.getId();
    this.tags.putAll(way.getTags());
    this.osmProvider = way.getOsmProvider();
    this.nodes.addAll(way.getNodeRefs());
  }

  public OsmWayBuilder withId(long id) {
    this.id = id;
    return this;
  }

  public OsmWayBuilder addTag(String key, String value) {
    if (key != null && value != null) {
      this.tags.put(key.toLowerCase(), value);
    }
    return this;
  }

  public OsmWayBuilder withOsmProvider(OsmProvider osmProvider) {
    this.osmProvider = osmProvider;
    return this;
  }

  public OsmWayBuilder addNodeRef(long nodeRef) {
    nodes.add(nodeRef);
    return this;
  }

  public OsmWayBuilder addNodeRef(long nodeRef, int index) {
    nodes.insert(index, nodeRef);
    return this;
  }

  public OsmWay build() {
    return new OsmWay(id, tags, osmProvider, nodes);
  }
}
