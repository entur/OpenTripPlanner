package org.opentripplanner.osm.model;

import org.opentripplanner.street.geometry.WgsCoordinate;

public class NodeBuilder {

  public static OsmNode node(long id, WgsCoordinate wgsCoordinate) {
    return OsmNode.builder()
      .withId(id)
      .withLat(wgsCoordinate.latitude())
      .withLon(wgsCoordinate.longitude())
      .build();
  }
}
