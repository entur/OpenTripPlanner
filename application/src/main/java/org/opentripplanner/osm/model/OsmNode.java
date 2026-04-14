package org.opentripplanner.osm.model;

import static org.opentripplanner.street.model.StreetTraversalPermission.ALL;
import static org.opentripplanner.street.model.StreetTraversalPermission.NONE;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import org.locationtech.jts.geom.Coordinate;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.osm.OsmProvider;

public class OsmNode extends OsmEntity {

  public final double lat;
  public final double lon;

  private OsmNode(
    long id,
    double lat,
    double lon,
    Map<String, String> tags,
    I18NString creativeName,
    OsmProvider osmProvider
  ) {
    super(id, tags, creativeName, osmProvider);
    this.lat = lat;
    this.lon = lon;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Builder toBuilder() {
    Builder builder = new Builder();
    builder.id = this.id;
    builder.lat = this.lat;
    builder.lon = this.lon;
    builder.tags = new HashMap<>(this.getTags());
    builder.creativeName = this.creativeName;
    builder.osmProvider = this.getOsmProvider();
    return builder;
  }

  public static class Builder {

    private long id;
    private double lat;
    private double lon;
    private Map<String, String> tags = new HashMap<>();
    private I18NString creativeName;
    private OsmProvider osmProvider;

    public Builder withId(long id) {
      this.id = id;
      return this;
    }

    public Builder withLat(double lat) {
      this.lat = lat;
      return this;
    }

    public Builder withLon(double lon) {
      this.lon = lon;
      return this;
    }

    public Builder addTag(String key, String value) {
      if (key != null && value != null) {
        this.tags.put(key.toLowerCase(), value);
      }
      return this;
    }

    public Builder addTag(OsmTag tag) {
      if (tag != null) {
        this.tags.put(tag.getK().toLowerCase(), tag.getV());
      }
      return this;
    }

    public Builder withCreativeName(I18NString creativeName) {
      this.creativeName = creativeName;
      return this;
    }

    public Builder withOsmProvider(OsmProvider osmProvider) {
      this.osmProvider = osmProvider;
      return this;
    }

    public OsmNode build() {
      return new OsmNode(id, lat, lon, tags, creativeName, osmProvider);
    }
  }

  public String toString() {
    return "osm node " + id;
  }

  public Coordinate getCoordinate() {
    return new Coordinate(this.lon, this.lat);
  }

  public boolean hasHighwayTrafficLight() {
    return hasTag("highway") && "traffic_signals".equals(getTag("highway"));
  }

  public boolean hasCrossingTrafficLight() {
    return hasTag("crossing") && "traffic_signals".equals(getTag("crossing"));
  }

  /**
   * Checks if this node blocks traversal in any way
   *
   * @return true if it does
   */
  public boolean isBarrier() {
    return overridePermissions(ALL) != ALL;
  }

  /**
   * Checks if this node is a subway station entrance.
   *
   * @return true if it is
   */
  public boolean isSubwayEntrance() {
    return hasTag("railway") && "subway_entrance".equals(getTag("railway"));
  }

  /** checks for units (m/ft) in an OSM ele tag value, and returns the value in meters */
  public OptionalDouble parseEleTag() {
    var ele = getTag("ele");
    if (ele == null) {
      return OptionalDouble.empty();
    }
    ele = ele.toLowerCase();
    double unit = 1;
    if (ele.endsWith("m")) {
      ele = ele.replaceFirst("\\s*m", "");
    } else if (ele.endsWith("ft")) {
      ele = ele.replaceFirst("\\s*ft", "");
      unit = 0.3048;
    }
    try {
      return OptionalDouble.of(Double.parseDouble(ele) * unit);
    } catch (NumberFormatException e) {
      return OptionalDouble.empty();
    }
  }

  @Override
  public String url() {
    return String.format("https://www.openstreetmap.org/node/%d", getId());
  }

  /**
   * Check if this node represents a tagged barrier crossing if placed on an intersection
   * of a highway and a barrier way.
   *
   * @return true if it has a barrier tag, or if it explicitly overrides permissions.
   */
  public boolean isTaggedBarrierCrossing() {
    return (
      hasTag("barrier") ||
      hasTag("access") ||
      hasTag("entrance") ||
      overridePermissions(ALL) != ALL ||
      overridePermissions(NONE) != NONE
    );
  }
}
