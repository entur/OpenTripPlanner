package org.opentripplanner.graph_builder.module.osm;

public class OsmEntityTypeMapper {

  public static org.opentripplanner.street.model.vertex.OsmEntityType map(OsmEntityType type) {
    return switch (type) {
      case NODE -> org.opentripplanner.street.model.vertex.OsmEntityType.NODE;
      case RELATION -> org.opentripplanner.street.model.vertex.OsmEntityType.RELATION;
      case WAY -> org.opentripplanner.street.model.vertex.OsmEntityType.WAY;
    };
  }
}
