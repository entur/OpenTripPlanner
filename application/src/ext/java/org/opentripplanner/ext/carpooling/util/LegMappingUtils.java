package org.opentripplanner.ext.carpooling.util;

import java.time.ZonedDateTime;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.astar.model.GraphPath;
import org.opentripplanner.model.plan.Place;
import org.opentripplanner.model.plan.leg.StreetLeg;
import org.opentripplanner.street.geometry.GeometryUtils;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.StreetVertex;
import org.opentripplanner.street.model.vertex.TemporaryStreetLocation;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.TraverseMode;
import org.opentripplanner.street.search.state.State;

/**
 * Leg- and place-building helpers shared by the itinerary mappers that wrap a street-routed ride
 * (carpooling, flex booking) in WALK legs.
 */
public final class LegMappingUtils {

  private LegMappingUtils() {}

  /**
   * Builds a WALK {@link StreetLeg} from a routed walk path with the given absolute times and
   * boundary places.
   */
  public static StreetLeg buildWalkLeg(
    GraphPath<State, Edge, Vertex> walkPath,
    ZonedDateTime startTime,
    ZonedDateTime endTime,
    Place fromPlace,
    Place toPlace
  ) {
    LineString geometry = GeometryUtils.concatenateLineStrings(walkPath.edges, Edge::getGeometry);
    double distance = walkPath.edges.stream().mapToDouble(Edge::getDistanceMeters).sum();

    return StreetLeg.of()
      .withMode(TraverseMode.WALK)
      .withStartTime(startTime)
      .withEndTime(endTime)
      .withFrom(fromPlace)
      .withTo(toPlace)
      .withGeometry(geometry)
      .withDistanceMeters(distance)
      .withGeneralizedCost((int) walkPath.getWeight())
      .build();
  }

  /**
   * Builds a {@link Place} from a vertex using the same naming logic the core street-leg
   * mapper applies for any mode. {@link StreetVertex} intersections get the localized
   * "corner of X and Y" name composed from the outgoing OSM street names (mode-agnostic);
   * {@link TemporaryStreetLocation}s (passenger origin/destination) keep the name they were
   * created with; everything else falls back to {@link Vertex#getName()}.
   */
  public static Place makePlace(Vertex vertex) {
    if (vertex instanceof StreetVertex sv && !(vertex instanceof TemporaryStreetLocation)) {
      return Place.normal(vertex, sv.getIntersectionName());
    }
    return Place.normal(vertex, vertex.getName());
  }
}
