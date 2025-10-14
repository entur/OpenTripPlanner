package org.opentripplanner.routing.linking;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.street.model.vertex.TransitStopVertex;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 * Holds vertices (mainly temporary) that are meant to be used within the scope of a single route
 * request that can contain access/egress/direct/transfer routing. The temporary vertices will be
 * disposed by {@link TemporaryVerticesContainer} after the search
 * is over.
 */
public class LinkingContext {

  private final Map<GenericLocation, Set<Vertex>> verticesByLocation;
  private final Set<TransitStopVertex> fromStopVertices;
  private final Set<TransitStopVertex> toStopVertices;

  public LinkingContext(
    Map<GenericLocation, Set<Vertex>> verticesByLocation,
    Set<TransitStopVertex> fromStopVertices,
    Set<TransitStopVertex> toStopVertices
  ) {
    this.fromStopVertices = fromStopVertices;
    this.toStopVertices = toStopVertices;
    this.verticesByLocation = verticesByLocation;
  }

  /**
   * If from location (origin) has a stop id defined, this will include vertices related to it. If
   * the stop is a station, the child stops will be included.
   */
  public Set<TransitStopVertex> fromStopVertices() {
    return fromStopVertices;
  }

  /**
   * If to location (destination) has a stop id defined, this will include vertices related to it.
   * If the stop is a station, the child stops will be included.
   */
  public Set<TransitStopVertex> toStopVertices() {
    return toStopVertices;
  }

  /**
   * Vertices that are used for either origin, destination or for via locations. Only the visit via
   * locations that have a coordinate specified will have vertices available. Stop vertices are not
   * included via locations.
   */
  public Set<Vertex> findVertices(GenericLocation location) {
    return verticesByLocation.getOrDefault(location, Set.of());
  }

  @Override
  public String toString() {
    var sortedVerticesByLocation = verticesByLocation
      .entrySet()
      .stream()
      .sorted(Map.Entry.comparingByKey(Comparator.comparing(GenericLocation::toString)))
      .toList();
    return ToStringBuilder.of(LinkingContext.class)
      .addCol("verticesByLocation", sortedVerticesByLocation)
      .addCol("fromStops", fromStopVertices)
      .addCol("toStops", toStopVertices)
      .toString();
  }
}
