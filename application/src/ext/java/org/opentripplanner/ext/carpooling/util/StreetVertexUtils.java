package org.opentripplanner.ext.carpooling.util;

import java.util.List;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.routing.linking.LinkingContext;
import org.opentripplanner.routing.linking.TemporaryVerticesContainer;
import org.opentripplanner.routing.linking.VertexLinker;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.street.model.edge.LinkingDirection;
import org.opentripplanner.street.model.edge.TemporaryFreeEdge;
import org.opentripplanner.street.model.vertex.TemporaryStreetLocation;
import org.opentripplanner.street.model.vertex.TemporaryVertex;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.TraverseMode;
import org.opentripplanner.street.search.TraverseModeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreetVertexUtils {

  private static final Logger LOG = LoggerFactory.getLogger(StreetVertexUtils.class);

  private final VertexLinker vertexLinker;
  private final TemporaryVerticesContainer temporaryVerticesContainer;

  /**
   * @param vertexLinker links coordinates to graph vertices
   * @param temporaryVerticesContainer container for temporary vertices and edges
   */
  public StreetVertexUtils(
    VertexLinker vertexLinker,
    TemporaryVerticesContainer temporaryVerticesContainer
  ) {
    this.vertexLinker = vertexLinker;
    this.temporaryVerticesContainer = temporaryVerticesContainer;
  }

  /**
   * Gets vertices for a location, either from the LinkingContext or by creating
   * temporary vertices on-demand.
   * <p>
   * This method first checks if vertices already exist in the LinkingContext (which
   * contains pre-linked vertices for the passenger's origin and destination). If not
   * found (e.g., for driver trip waypoints), it creates a temporary vertex on-demand
   * using VertexLinker and adds it to the TemporaryVerticesContainer for automatic cleanup.
   * <p>
   * This follows the pattern used in VertexCreationService but uses VertexLinker directly
   * to respect package boundaries (VertexCreationService is in the 'internal' package).
   *
   * @param location the location to get vertices for
   * @param linkingContext linking context to check for existing vertices
   * @return set of vertices for the location (either existing or newly created)
   */
  public Vertex getOrCreateVertex(GenericLocation location, LinkingContext linkingContext) {
    var vertices = linkingContext.findVertices(location);
    if (!vertices.isEmpty()) {
      return vertices.stream().findFirst().get();
    }

    var coordinate = location.getCoordinate();
    var tempVertex = new TemporaryStreetLocation(
      coordinate,
      new NonLocalizedString(location.label != null ? location.label : "Carpooling Waypoint")
    );

    var disposableEdges = vertexLinker.linkVertexForRequest(
      tempVertex,
      new TraverseModeSet(TraverseMode.CAR),
      LinkingDirection.BIDIRECTIONAL,
      (vertex, streetVertex) ->
        List.of(
          TemporaryFreeEdge.createTemporaryFreeEdge((TemporaryVertex) vertex, streetVertex),
          TemporaryFreeEdge.createTemporaryFreeEdge(streetVertex, (TemporaryVertex) vertex)
        )
    );

    // Add to container for automatic cleanup
    temporaryVerticesContainer.addEdgeCollection(disposableEdges);

    if (tempVertex.getIncoming().isEmpty() && tempVertex.getOutgoing().isEmpty()) {
      LOG.warn("Couldn't link coordinate {} to graph for location {}", coordinate, location);
    } else {
      LOG.debug("Created temporary vertex for coordinate {} (not in LinkingContext)", coordinate);
    }

    return tempVertex;
  }

  public Vertex getOrCreateVertex(WgsCoordinate coord, LinkingContext linkingContext) {
    return getOrCreateVertex(
      GenericLocation.fromCoordinate(coord.latitude(), coord.longitude()),
      linkingContext
    );
  }
}
