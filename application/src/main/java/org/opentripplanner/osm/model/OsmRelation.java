package org.opentripplanner.osm.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opentripplanner.osm.OsmProvider;

public class OsmRelation extends OsmEntity {

  private final List<OsmRelationMember> members;

  private OsmRelation(
    long id,
    Map<String, String> tags,
    OsmProvider osmProvider,
    List<OsmRelationMember> members
  ) {
    super(id, tags, osmProvider);
    this.members = Collections.unmodifiableList(new ArrayList<>(members));
  }

  public static Builder builder() {
    return new Builder();
  }

  public Builder toBuilder() {
    Builder builder = new Builder();
    builder.id = this.id;
    builder.tags = new HashMap<>(this.getTags());
    builder.osmProvider = this.getOsmProvider();
    builder.members = new ArrayList<>(this.members);
    return builder;
  }

  public List<OsmRelationMember> getMembers() {
    return members;
  }

  public static class Builder {

    private long id;
    private Map<String, String> tags = new HashMap<>();
    private OsmProvider osmProvider;
    private List<OsmRelationMember> members = new ArrayList<>();

    public Builder withId(long id) {
      this.id = id;
      return this;
    }

    public Builder addTag(String key, String value) {
      if (key != null && value != null) {
        this.tags.put(key.toLowerCase(), value);
      }
      return this;
    }

    public Builder withOsmProvider(OsmProvider osmProvider) {
      this.osmProvider = osmProvider;
      return this;
    }

    public Builder addMember(OsmRelationMember member) {
      members.add(member);
      return this;
    }

    public OsmRelation build() {
      return new OsmRelation(id, tags, osmProvider, members);
    }
  }

  public String toString() {
    return "osm relation " + id;
  }

  @Override
  public String url() {
    return String.format("https://www.openstreetmap.org/relation/%d", getId());
  }

  public boolean isBicycleRoute() {
    return isRoute() && isTag("route", "bicycle");
  }

  public boolean isRoute() {
    return isType("route");
  }

  public boolean isRoadRoute() {
    return isRoute() && isTag("route", "road");
  }

  public boolean isRestriction() {
    return isType("restriction");
  }

  public boolean isPublicTransport() {
    return isType("public_transport");
  }

  public boolean isMultiPolygon() {
    return isType("multipolygon");
  }

  public boolean isStopArea() {
    return isPublicTransport() && isTag("public_transport", "stop_area");
  }

  private boolean isType(String type) {
    return isTag("type", type);
  }
}
