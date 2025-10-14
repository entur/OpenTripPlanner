package org.opentripplanner.graph_builder.module.osm.naming;

import java.util.List;
import java.util.Set;
import org.locationtech.jts.geom.Geometry;
import org.opentripplanner.framework.geometry.HashGridSpatialIndex;
import org.opentripplanner.graph_builder.module.osm.StreetEdgePair;
import org.opentripplanner.osm.model.OsmEntity;
import org.opentripplanner.osm.model.OsmWay;
import org.opentripplanner.street.model.edge.StreetEdge;

/**
 * Helper class for collecting {@link OsmWay}/{@link StreetEdge} pairs in a {@link HashGridSpatialIndex}.
 */
class StreetEdgeIndex {
  public record EdgeOnLevel(OsmWay way, StreetEdge edge, Set<String> levels) {}

  private final HashGridSpatialIndex<EdgeOnLevel> index = new HashGridSpatialIndex<>();

  /**
   * Adds an entry to a geospatial index.
   */
  public void add(OsmEntity way, StreetEdgePair pair) {
    add(way, pair, Integer.MAX_VALUE);
  }

  /**
   * Adds an entry to a geospatial index if its length is less than a threshold.
   */
  public void add(
    OsmEntity way,
    StreetEdgePair pair,
    int maxLengthMeters
  ) {
    // We generate two edges for each osm way: one there and one back. This spatial index only
    // needs to contain one item for each road segment with a unique geometry and name, so we
    // add only one of the two edges.
    var edge = pair.pickAny();
    if (edge.getDistanceMeters() <= maxLengthMeters) {
      index.insert(
        edge.getGeometry().getEnvelopeInternal(),
        new EdgeOnLevel((OsmWay) way, edge, way.getLevels())
      );
    }
  }

  public List<EdgeOnLevel> query(Geometry buffer) {
    return index.query(buffer.getEnvelopeInternal());
  }
}
