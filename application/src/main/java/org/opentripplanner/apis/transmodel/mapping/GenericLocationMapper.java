package org.opentripplanner.apis.transmodel.mapping;

import java.util.Map;
import org.opentripplanner.api.model.transit.FeedScopedIdMapper;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.model.GenericLocation;

class GenericLocationMapper {

  private final FeedScopedIdMapper idMapper;

  GenericLocationMapper(FeedScopedIdMapper idMapper) {
    this.idMapper = idMapper;
  }

  /**
   * Maps a GraphQL Location input type to a GenericLocation
   */
  GenericLocation toGenericLocation(Map<String, Object> m) {
    Map<String, Object> coordinates = (Map<String, Object>) m.get("coordinates");
    Double lat = null;
    Double lon = null;
    if (coordinates != null) {
      lat = (Double) coordinates.get("latitude");
      lon = (Double) coordinates.get("longitude");
    }

    String placeRef = (String) m.get("place");
    FeedScopedId stopId = idMapper.parseNullSafe(placeRef).orElse(null);
    String name = (String) m.get("name");
    name = name == null ? "" : name;

    if (stopId != null && lat != null && lon != null) {
      return GenericLocation.fromStopIdWithFallback(stopId, lat, lon, name);
    } else if (stopId != null) {
      return GenericLocation.fromStopId(stopId, name);
    } else if (lat != null && lon != null) {
      return GenericLocation.fromCoordinate(lat, lon, name);
    } else {
      return GenericLocation.fromUnspecified(name);
    }
  }
}
