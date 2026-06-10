package org.opentripplanner.street.linking;

import static com.google.common.truth.Truth.assertThat;
import static org.opentripplanner.core.model.id.FeedScopedIdFactory.id;
import static org.opentripplanner.street.model.StreetModelFactory.intersectionVertex;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.street.geometry.CoordinateArrayListSequence;
import org.opentripplanner.street.geometry.GeometryUtils;
import org.opentripplanner.street.geometry.SphericalDistanceLibrary;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.street.model.StreetModelFactory;
import org.opentripplanner.street.model.StreetTraversalPermission;
import org.opentripplanner.street.model.edge.StreetTransitStopLink;
import org.opentripplanner.street.model.vertex.StreetVertex;
import org.opentripplanner.street.model.vertex.TransitStopVertex;
import org.opentripplanner.street.search.TraverseMode;
import org.opentripplanner.street.search.TraverseModeSet;

/**
 * Test that linking very near the end of the edge doesn't split but uses the endpoint instead.
 */
public class LinkingNearEndTest {
  /* An edge with a very short first segment which caused problems in production. */
  private static final Coordinate[] points = {
    new Coordinate(25.007039006519058, 60.140565999664084),
    new Coordinate(25.007039, 60.140566),
    new Coordinate(25.006999800000003, 60.1403405)
  };

  private static final WgsCoordinate stop = new WgsCoordinate(60.1405804,25.007042);

  @Test
  void linkingNearStart() {
    var v1 = intersectionVertex(points[0]);
    var v2 = intersectionVertex(points[points.length - 1]);
    var sequence = new CoordinateArrayListSequence();
    sequence.extend(points);
    linking(v1, v2, sequence);
  }

  @Test
  void linkingNearEnd() {
    var v2 = intersectionVertex(points[points.length - 1]);
    var v1 = intersectionVertex(points[0]);
    var sequence = new CoordinateArrayListSequence();
    for (int i = points.length - 1; i >= 0; i--) {
      sequence.add(points[i]);
    }
    linking(v2, v1, sequence);
  }

  private void linking(StreetVertex v1, StreetVertex v2, CoordinateArrayListSequence sequence) {

    var stopVertex = TransitStopVertex.of()
      .withCoordinate(stop)
      .withId(id("stop"))
      .build();

    var geom = new LineString(sequence, GeometryUtils.getGeometryFactory());

    StreetModelFactory.streetEdgeBuilder(
      v1,
      v2,
      SphericalDistanceLibrary.distance(v1.getCoordinate(), v2.getCoordinate()),
      StreetTraversalPermission.ALL
    ).withGeometry(geom).buildAndConnect();
    var env = new LinkingEnvironment(v1, v2);

    env
      .linker()
      .linkVertexPermanently(
        stopVertex,
        new TraverseModeSet(TraverseMode.WALK),
        LinkingDirection.BIDIRECTIONAL,
        ((vertex, streetVertex) -> {
          var s = (TransitStopVertex) vertex;
          return List.of(
            StreetTransitStopLink.createStreetTransitStopLink(s, streetVertex),
            StreetTransitStopLink.createStreetTransitStopLink(streetVertex, s)
          );
        })
      );

    assertThat(env.graph().listStreetEdges()).hasSize(1);
  }

}
